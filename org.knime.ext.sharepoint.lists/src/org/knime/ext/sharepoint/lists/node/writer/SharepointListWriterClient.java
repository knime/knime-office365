/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   2022-03-02 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint.lists.node.writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.Pair;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphApiUtil.RefreshableAuthenticationProvider;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.lists.node.SharePointListUtils;
import org.knime.ext.sharepoint.lists.node.SharepointListSettings;

import com.google.gson.JsonPrimitive;
import com.microsoft.graph.core.DefaultConnectionConfig;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.ColumnDefinition;
import com.microsoft.graph.models.extensions.FieldValueSet;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.ListItem;
import com.microsoft.graph.requests.extensions.ColumnDefinitionCollectionPage;
import com.microsoft.graph.requests.extensions.ColumnDefinitionCollectionResponse;
import com.microsoft.graph.requests.extensions.IListItemCollectionPage;
import com.microsoft.graph.requests.extensions.IListRequestBuilder;

/**
 * Handles the actual writing process of the SharePoint List Writer node.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
class SharepointListWriterClient implements AutoCloseable {

    // Attribute it to the node in the logs
    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListWriterNodeModel.class); // NOSONAR
    private static final long TOKEN_MAX_AGE = TimeUnit.MINUTES.toMillis(10);

    private final IGraphServiceClient m_client;
    private final RefreshableAuthenticationProvider m_authProvider;

    private static final Object LOCK = new Object();

    private final SharepointListWriterConfig m_config;

    private final SharepointListSettings m_sharePointListSettings;

    private final BufferedDataTable m_table;

    private final DataTableSpec m_tableSpec;

    private final ExecutionContext m_exec;

    private final String m_siteId;

    private String m_listId;

    private boolean m_listCreated;

    private long m_columnsCleared;

    private long m_itemsCleared;

    private boolean m_processItemsSequential = true; // change this with toggle later

    SharepointListWriterClient(final SharepointListWriterConfig config, final BufferedDataTable table,
            final MicrosoftCredentialPortObjectSpec authPortSpec, final ExecutionContext exec)
            throws IOException, InvalidSettingsException {
        m_config = config;
        m_sharePointListSettings = config.getSharepointListSettings();
        m_table = table;
        m_tableSpec = m_table.getDataTableSpec();
        m_exec = exec;
        final var clientAndAuth = createGraphServiceClient(authPortSpec);
        m_client = clientAndAuth.getFirst();
        m_authProvider = clientAndAuth.getSecond();
        m_siteId = getSiteId();
        m_listId = getListId();
    }

    private void checkToken() throws IOException {
        if (m_authProvider.refreshTokenIfOlder(TOKEN_MAX_AGE)) {
            LOGGER.debug("Requested new token…");
        }
    }

    /**
     * Creates / overwrites a SharePoint list from a KNIME Table.
     *
     * @throws IOException
     * @throws CanceledExecutionException
     */
    void writeList() throws IOException, CanceledExecutionException {
        m_exec.setMessage("Writing rows…");
        final String[] colNames = m_tableSpec.getColumnNames();
        try (final var batch = new ListBatchRequest(m_client, m_authProvider, m_exec)) {
            if (m_config.getSharepointListSettings().getOverwritePolicy() == ListOverwritePolicy.OVERWRITE
                    && !m_listCreated) {
                prepareOverwrite(batch);
            }

            final var colMap = mapColNames();

            long rowNumber = 0;
            final long noRows = m_table.size();
            try (final var iterator = m_table.iterator()) {
                while (iterator.hasNext()) {
                    final var row = iterator.next();
                    createListItem(row, colNames, colMap, batch);

                    // update progress
                    final long rowNumberFinal = rowNumber;
                    m_exec.setProgress(rowNumber / (double) noRows, () -> ("Write row " + rowNumberFinal));
                    m_exec.checkCanceled();
                    rowNumber++;
                }
            }
        }

    }

    private Pair<IGraphServiceClient, RefreshableAuthenticationProvider> createGraphServiceClient(
            final MicrosoftCredentialPortObjectSpec authPortSpec) throws IOException {
        final var result = GraphApiUtil
                .createClientAndRefreshableAuthenticationProvider(authPortSpec.getMicrosoftCredential());
        final var client = result.getFirst();
        final var timeoutSettings = m_sharePointListSettings.getTimeoutSettings();
        final var connectionConfig = new DefaultConnectionConfig();
        connectionConfig.setConnectTimeout(timeoutSettings.getConnectionTimeout().toMillisPart());
        connectionConfig.setReadTimeout(timeoutSettings.getReadTimeout().toMillisPart());
        client.getHttpProvider().setConnectionConfig(connectionConfig);
        return result;
    }

    /**
     * Returns the site id.
     *
     * @return returns the site id
     * @throws IOException
     */
    private String getSiteId() throws IOException {
        final var settings = m_sharePointListSettings.getSiteSettings();
        final var siteResolver = new SharepointSiteResolver(m_client, settings.getMode(),
                settings.getSubsiteModel().getStringValue(), settings.getWebURLModel().getStringValue(),
                settings.getGroupModel().getStringValue());

        return siteResolver.getTargetSiteId();
    }

    /**
     * Returns the list id in case there is already an id or we create a new list.
     *
     * @return returns the list id
     * @throws IOException
     * @throws InvalidSettingsException
     */
    private String getListId() throws IOException, InvalidSettingsException {
        var listId = m_sharePointListSettings.getListSettings().getListModel().getStringValue();
        var listExists = !listId.isEmpty();

        if (listId.isEmpty()) {
            final var optionalListId = getListIdByName();
            listExists = optionalListId.isPresent();

            listId = listExists ? optionalListId.get() : createSharepointList();
        }

        if (listExists && m_config.getSharepointListSettings().getOverwritePolicy() == ListOverwritePolicy.FAIL) {
            throw new InvalidSettingsException(
                    "The specified list already exists and the node fails due to overwrite settings");
        }

        return listId;
    }

    /**
     * Get a list by name by checking which list internal name matches the name from
     * the settings.
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     */
    private Optional<String> getListIdByName() throws IOException {
        try {
            final var resp = m_client.sites(m_siteId).lists().buildRequest().get();
            var lists = resp.getCurrentPage();
            var nextRequest = resp.getNextPage();
            while (nextRequest != null) {
                final var listsTmp = nextRequest.buildRequest().get().getCurrentPage();
                lists.addAll(listsTmp);
                nextRequest = resp.getNextPage();
            }

            return findListId(lists);
        } catch (GraphServiceException ex) {
            throw new IOException("Error during list name retrival: " + ex.getServiceError().message, ex);
        }
    }

    private Optional<String> findListId(final List<com.microsoft.graph.models.extensions.List> lists) {
        final var listName = SharePointListUtils
                .getInternalListName(m_sharePointListSettings.getListSettings().getListNameModel().getStringValue());
        return lists.stream().filter(l -> l.name.equals(listName)).findAny().map(l -> l.id);
    }

    /**
     * Creates a new list.
     *
     * @return the Id of the created list
     * @throws IOException
     */
    private String createSharepointList() throws IOException {
        final var list = new com.microsoft.graph.models.extensions.List();
        list.displayName = m_sharePointListSettings.getListSettings().getListNameModel().getStringValue();

        final var columnDefinitionCollectionResponse = new ColumnDefinitionCollectionResponse();
        columnDefinitionCollectionResponse.value = createColumnDefinitions();

        final var columnDefinitionCollectionPage = new ColumnDefinitionCollectionPage(
                columnDefinitionCollectionResponse, null);
        list.columns = columnDefinitionCollectionPage;

        try {
            var response = m_client.sites(m_siteId).lists().buildRequest().post(list);
            m_listCreated = true;
            return response.id;
        } catch (GraphServiceException ex) {
            throw new IOException("Error during list creation: " + ex.getServiceError().message, ex);
        }
    }

    /**
     * Creates a {@link LinkedList} of {@link ColumnDefinition} based on the
     * {@link DataTableSpec}.
     *
     * @return {@link LinkedList} of {@link ColumnDefinition}
     */
    private LinkedList<ColumnDefinition> createColumnDefinitions() {
        final LinkedList<ColumnDefinition> columnDefinitions = new LinkedList<>();
        m_tableSpec.forEach(c -> {
            final DataType type = c.getType();
            columnDefinitions.add(KNIMEToSharepointTypeConverter.TYPE_CONVERTER
                    .getOrDefault(type, KNIMEToSharepointTypeConverter.DEFAULT_CONVERTER).getSecond()
                    .apply(c.getName()));
        });

        return columnDefinitions;
    }

    /**
     * Maps the display name to the actual internal name created by SharePoint.
     *
     * @return a {@link Map} which maps display name and internal name
     * @throws IOException
     */
    private Map<String, String> mapColNames() throws IOException {
        try {
            checkToken();
            final var columns = createListRequestBuilder().columns().buildRequest().get();
            final Map<String, String> colMap = new HashMap<>();

            columns.getRawObject().get("value").getAsJsonArray().iterator().forEachRemaining(j -> {
                final var jsonObject = j.getAsJsonObject();
                colMap.put(jsonObject.get("displayName").getAsString(), jsonObject.get("name").getAsString());
            });

            return colMap;
        } catch (GraphServiceException ex) {
            throw new IOException("Error while mapping of column names: " + ex.getServiceError().message, ex);
        }
    }

    /**
     * Creates a {@link ListItem} and sends it to SharePoint.
     *
     * @param row
     *            the current {@link DataRow}
     * @param colNames
     *            the column names of the table
     * @param colMap
     *            the mapping of the column names
     * @param batch
     *            {@link ListBatchRequest} used to accumulate and execute batch
     *            requests
     * @throws IOException
     *             if creating the items failed. This may get triggered at a later
     *             point due to batching.
     * @throws CanceledExecutionException
     */
    private void createListItem(final DataRow row, final String[] colNames, final Map<String, String> colMap,
            final ListBatchRequest batch) throws IOException, CanceledExecutionException {
        var i = 0;
        final var li = new ListItem();
        final var fvs = new FieldValueSet();

        fvs.additionalDataManager().put("Title", new JsonPrimitive(row.getKey().getString()));

        for (final var cell : row) {
            final String colName = colMap.get(colNames[i]);

            if (!cell.isMissing() && colName != null) {
                fvs.additionalDataManager().put(colName,
                        KNIMEToSharepointTypeConverter.TYPE_CONVERTER
                                .getOrDefault(cell.getType(), KNIMEToSharepointTypeConverter.DEFAULT_CONVERTER)//
                                .getFirst()//
                                .apply(cell));
            }
            i++;
        }

        li.fields = fvs;

        try {
            batch.post(createListRequestBuilder().items().buildRequest(), li, m_processItemsSequential);
        } catch (GraphServiceException ex) {
            throw new IOException("Error during row creation: " + ex.getServiceError().message, ex);
        }
    }

    /**
     * Prepares the overwrite process by deleting all columns + the list items which
     * will remain.
     *
     * @param batch
     *            {@link ListBatchRequest} used to accumulate and execute batch
     *            requests
     *
     * @throws IOException
     *             if some part of the overwrite failed. This may get triggered at a
     *             later point due to batching.
     * @throws CanceledExecutionException
     */
    private void prepareOverwrite(final ListBatchRequest batch) throws IOException, CanceledExecutionException {
        // These are always sequential because SharePoint likes to stumble over itself
        synchronized (LOCK) { // Microsoft doesn't know what they're doing…
            // Multiple parallel executions of different nodes may influence each other
            // otherwise, resulting in weird errors… I know, right?
            deleteColumns(batch);
            createColumns(batch);
            batch.tryCompleteAllCurrentRequests();
        }
        // The following may not so it has to be separated
        deleteListItems(batch);
        if (m_processItemsSequential) {
            // Switch to sequential: has to separated
            batch.tryCompleteAllCurrentRequests();
        }
    }

    /**
     * Creates columns for an existing list
     *
     * @param batch
     *            {@link ListBatchRequest} used to accumulate and execute batch
     *            requests
     *
     * @throws IOException
     *             if some part of the column creation failed. This may get
     *             triggered at a later point due to batching.
     * @throws CanceledExecutionException
     */
    private void createColumns(final ListBatchRequest batch) throws IOException, CanceledExecutionException {
        m_exec.setMessage("Creating columns…");
        try {
            final var columnDefinitions = createColumnDefinitions();
            var columnNumber = 0L;
            final var totalColumns = columnDefinitions.size();
            for (final var columnDefinition : columnDefinitions) {
                columnNumber++;
                m_exec.setMessage(String.format("Create column %d/%d", columnNumber, totalColumns));
                m_exec.checkCanceled();
                // force sequential here so that SharePoint doesn't stumble over itself and
                // loses data…
                batch.post(createListRequestBuilder().columns().buildRequest(), columnDefinition, true);
            }
            m_exec.setProgress(Double.NaN);
        } catch (GraphServiceException ex) {
            throw new IOException("Error during overwriting process: " + ex.getServiceError().message, ex);
        }
    }

    /**
     * Deletes all deleteable columns from a list.
     *
     * @param batch
     *            {@link ListBatchRequest} used to accumulate and execute batch
     *            requests
     *
     * @throws IOException
     *             if some part of the column deletion failed. This may get
     *             triggered at a later point due to batching.
     * @throws CanceledExecutionException
     */
    private void deleteColumns(final ListBatchRequest batch) throws IOException, CanceledExecutionException {
        m_exec.setMessage("Deleting columns…");
        try {
            m_columnsCleared = 0;
            checkToken();
            final var columns = createListRequestBuilder().columns().buildRequest().get();
            var colDefList = columns.getCurrentPage();
            deleteColumns(colDefList, batch);
            var nextRequest = columns.getNextPage();
            while (nextRequest != null) {
                checkToken();
                colDefList = nextRequest.buildRequest().get().getCurrentPage();
                deleteColumns(colDefList, batch);
                nextRequest = columns.getNextPage();
            }
        } catch (GraphServiceException ex) {
            throw new IOException("Error while trying to delete columns: " + ex.getServiceError().message, ex);
        }
    }

    private void deleteColumns(final List<ColumnDefinition> colDefList, final ListBatchRequest batch)
            throws IOException, CanceledExecutionException {
        for (final var c : colDefList) {
            // Read only columns and Title, ContentType and Attachments column can't be
            // deleted
            if (Boolean.FALSE.equals(c.readOnly) && !c.name.equals("Title") && !c.name.equals("ContentType")
                    && !c.name.equals("Attachments")) {
                m_columnsCleared++;
                m_exec.setMessage(m_columnsCleared + " columns cleared");
                m_exec.checkCanceled();
                // force sequential here so that SharePoint doesn't stumble over itself and
                // loses data…
                batch.delete(createListRequestBuilder().columns(c.id).buildRequest(), true);
            }
        }
    }

    /**
     * Deletes all list items.
     *
     * @param batch
     *            {@link ListBatchRequest} used to accumulate and execute batch
     *            requests
     *
     * @throws IOException
     *             if some part of the item deletion failed. This may get triggered
     *             at a later point due to batching.
     * @throws CanceledExecutionException
     */
    private void deleteListItems(final ListBatchRequest batch) throws IOException, CanceledExecutionException {
        m_exec.setMessage("Deleting items…");
        try {
            m_itemsCleared = 0;
            checkToken();
            final var listItems = createListRequestBuilder().items().buildRequest().get();

            deleteListItems(listItems, batch);

            var req = listItems.getNextPage();
            while (req != null) {
                checkToken();
                final var items = req.buildRequest().get();
                deleteListItems(items, batch);
                req = items.getNextPage();
            }
        } catch (GraphServiceException ex) {
            throw new IOException("Error while deleting list item: " + ex.getServiceError().message, ex);
        }
    }

    private void deleteListItems(final IListItemCollectionPage listItems, final ListBatchRequest batch)
            throws IOException, CanceledExecutionException {
        final var listItemsJson = listItems.getRawObject().getAsJsonObject().get("value").getAsJsonArray();
        for (final var item : listItemsJson) {
            m_itemsCleared++;
            m_exec.setMessage(m_itemsCleared + " items cleared");
            m_exec.checkCanceled();
            final var id = item.getAsJsonObject().get("id").getAsString();
            batch.delete(createListRequestBuilder().items(id).buildRequest(), false);
        }
    }

    private IListRequestBuilder createListRequestBuilder() {
        return m_client.sites(m_siteId).lists(m_listId);
    }

    @Override
    public void close() throws Exception {
        m_client.shutdown();
    }

}
