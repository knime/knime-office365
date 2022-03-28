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
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.lists.node.SharePointListUtils;
import org.knime.ext.sharepoint.lists.node.SharepointListSettings;

import com.microsoft.graph.core.DefaultConnectionConfig;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.ColumnDefinition;
import com.microsoft.graph.models.extensions.FieldValueSet;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.List;
import com.microsoft.graph.models.extensions.ListItem;
import com.microsoft.graph.requests.extensions.ColumnDefinitionCollectionPage;
import com.microsoft.graph.requests.extensions.ColumnDefinitionCollectionResponse;
import com.microsoft.graph.requests.extensions.IListItemCollectionPage;

/**
 * Handles the actual writing process of the SharePoint List Writer node.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
class SharepointListWriterClient implements AutoCloseable {

    private final IGraphServiceClient m_client;

    // TODO currently not in use since there are no other settings but will come
    // with future tickets
    private final SharepointListWriterConfig m_config;

    private final SharepointListSettings m_sharePointListSettings;

    private final BufferedDataTable m_table;

    private final DataTableSpec m_tableSpec;

    private final ExecutionContext m_exec;

    private final String m_siteId;

    private String m_listId;

    SharepointListWriterClient(final SharepointListWriterConfig config, final BufferedDataTable table,
            final MicrosoftCredentialPortObjectSpec authPortSpec, final ExecutionContext exec) throws IOException {
        m_config = config;
        m_sharePointListSettings = config.getSharepointListSettings();
        m_table = table;
        m_tableSpec = m_table.getDataTableSpec();
        m_exec = exec;
        m_client = createGraphServiceClient(authPortSpec);
        m_siteId = getSiteId();
        m_listId = getListId();
    }

    void writeList() throws IOException, CanceledExecutionException {
        final String[] colNames = m_tableSpec.getColumnNames();
        final var colMap = mapColNames();

        long rowNo = 0;
        final long noRows = m_table.size();
        try (final var iterator = m_table.iterator()) {
            while (iterator.hasNext()) {
                final var row = iterator.next();
                createListItem(row, colNames, colMap);

                // update progress
                final long rowNoFinal = rowNo;
                m_exec.setProgress(rowNo / (double) noRows, () -> ("Write row " + rowNoFinal));
                m_exec.checkCanceled();
                rowNo++;
            }
        }
    }

    private IGraphServiceClient createGraphServiceClient(final MicrosoftCredentialPortObjectSpec authPortSpec)
            throws IOException {
        final var client = GraphApiUtil.createClient(authPortSpec.getMicrosoftCredential());
        final var timeoutSettings = m_sharePointListSettings.getTimeoutSettings();
        final var connectionConfig = new DefaultConnectionConfig();
        connectionConfig.setConnectTimeout(toMilis(timeoutSettings.getConnectionTimeout()));
        connectionConfig.setReadTimeout(toMilis(timeoutSettings.getReadTimeout()));
        client.getHttpProvider().setConnectionConfig(connectionConfig);
        return client;
    }

    private static int toMilis(final int duration) {
        return Math.toIntExact(Duration.ofSeconds(duration).toMillisPart());
    }

    /**
     * Returns the site id.
     *
     * @return returns the site id
     * @throws IOException
     */
    private String getSiteId() throws IOException {
        final var siteResolver = new SharepointSiteResolver(m_client,
                m_sharePointListSettings.getSiteSettings().getMode(),
                m_sharePointListSettings.getSiteSettings().getSubsiteModel().getStringValue(),
                m_sharePointListSettings.getSiteSettings().getWebURLModel().getStringValue(),
                m_sharePointListSettings.getSiteSettings().getGroupModel().getStringValue());

        return siteResolver.getTargetSiteId();
    }

    /**
     * Returns the list id in case there is already an id or we create a new list.
     *
     * @return returns the list id
     * @throws IOException
     */
    private String getListId() throws IOException {
        final var listId = m_sharePointListSettings.getListSettings().getListModel().getStringValue();

        if (listId.isEmpty()) {
            final var optionalOfListId = getListByName();
            return optionalOfListId.isEmpty() ? createSharepointList() : optionalOfListId.get();
        } else {
            return listId;
        }
    }

    /**
     * Get a list by name by checking which list internal name matches the name from
     * the settings.
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     */
    private Optional<String> getListByName() throws IOException {
        final var listName = SharePointListUtils
                .getListDisplayName(m_sharePointListSettings.getListSettings().getListNameModel().getStringValue());
        try {
            final var resp = m_client.sites(m_siteId).lists().buildRequest().get();
            var lists = resp.getCurrentPage();

            for (final var list : lists) {
                if (listName.equals(list.displayName)) {
                    return Optional.of(list.id);
                }
            }

            var req = resp.getNextPage();
            while (req != null) {
                final var l = req.buildRequest().get();
                for (final var list : l.getCurrentPage()) {
                    if (listName.equals(list.displayName)) {
                        return Optional.of(list.id);
                    }
                }
                req = l.getNextPage();
            }
            return Optional.empty();
        } catch (GraphServiceException ex) {
            throw new IOException("Error during trying to retrieve list name: " + ex.getServiceError().message, ex);
        }
    }

    /**
     * Creates a new list.
     *
     * @return the Id of the created list
     * @throws IOException
     */
    private String createSharepointList() throws IOException {
        final var list = new List();
        list.displayName = m_sharePointListSettings.getListSettings().getListNameModel().getStringValue();

        final var columnDefinitionCollectionResponse = new ColumnDefinitionCollectionResponse();
        columnDefinitionCollectionResponse.value = createColumnDefinitions();

        final var columnDefinitionCollectionPage = new ColumnDefinitionCollectionPage(
                columnDefinitionCollectionResponse, null);
        list.columns = columnDefinitionCollectionPage;

        try {
            var response = m_client.sites(m_siteId).lists().buildRequest().post(list);
            return response.id;
        } catch (GraphServiceException ex) {
            throw new IOException("Error during the creation of the list with error: " + ex.getServiceError().message,
                    ex);
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
            final var columns = m_client.sites(m_siteId).lists(m_listId).columns().buildRequest().get();
            final Map<String, String> colMap = new HashMap<>();

            columns.getRawObject().get("value").getAsJsonArray().iterator().forEachRemaining(j -> {
                final var jsonObject = j.getAsJsonObject();
                colMap.put(jsonObject.get("displayName").getAsString(), jsonObject.get("name").getAsString());
            });

            return colMap;
        } catch (GraphServiceException ex) {
            throw new IOException(
                    "Error during the mapping of the column names with error: " + ex.getServiceError().message, ex);
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
     * @throws Exception
     */
    private void createListItem(final DataRow row, final String[] colNames, final Map<String, String> colMap)
            throws IOException {
        var i = 0;
        final var li = new ListItem();
        final var fvs = new FieldValueSet();

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
            m_client.sites(m_siteId).lists(m_listId).items().buildRequest().post(li);
        } catch (GraphServiceException ex) {
            throw new IOException("Error during creation of a row with error: " + ex.getServiceError().message, ex);
        }
    }

    // TODO not used yet
    private void deleteListItems() throws IOException {
        try {
            final IListItemCollectionPage listItems = m_client.sites(m_siteId).lists(m_listId).items().buildRequest()
                    .get();

            deleteListItems(listItems);

            var req = listItems.getNextPage();
            while (req != null) {
                final var items = req.buildRequest().get();
                deleteListItems(items);
                items.getNextPage();
            }
        } catch (GraphServiceException ex) {
            throw new IOException("Error during deletion of a list item with error: " + ex.getServiceError().message,
                    ex);
        }
    }

    // TODO not used yet
    private void deleteListItems(final IListItemCollectionPage listItems) {
        final var listItemsJson = listItems.getRawObject().getAsJsonObject().get("value").getAsJsonArray();
        listItemsJson.forEach(i -> {
            final var id = i.getAsJsonObject().get("id").getAsString();
            m_client.sites(m_siteId).lists(m_listId).items(id).buildRequest().delete();
        });
    }

    @Override
    public void close() throws Exception {
        m_client.shutdown();
    }
}
