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
package org.knime.ext.sharepoint.lists.node;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.util.Pair;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters.ListMode;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.ext.sharepoint.parameters.TimeoutParameters;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.ColumnDefinition;
import com.microsoft.graph.models.FieldValueSet;
import com.microsoft.graph.models.ListItem;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.ColumnDefinitionCollectionPage;
import com.microsoft.graph.requests.ColumnDefinitionCollectionResponse;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.ListItemCollectionPage;
import com.microsoft.graph.requests.ListRequestBuilder;

import okhttp3.Request;

/**
 * Handles operations which change a Sharepoint List (create, write, update,
 * delete).
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointListChangingClient implements AutoCloseable {

    /**
     * Internal name of "Title" system column which is used for the RowID
     */
    private static final String COL_TITLE = "Title";

    /**
     * Internal name of "Content Type" system column
     */
    private static final String COL_CONTENT_TYPE = "ContentType";

    /**
     * Internal name of "Attachments" system column
     */
    private static final String COL_ATTACHMENTS = "Attachments";

    /**
     * System columns flagged as writable which are nevertheless treated specially
     * by Sharepoint
     */
    private static final Set<String> SPECIAL_COLS = Set.of(COL_TITLE, COL_ATTACHMENTS, COL_CONTENT_TYPE);

    /**
     * Columns which are writable according to their flags but ignore data written
     * to them.
     */
    private static final Set<String> EFFECTIVELY_RO_COLS = Set.of(COL_ATTACHMENTS, COL_CONTENT_TYPE);

    private static final Object LOCK = new Object();

    private final GraphServiceClient<Request> m_client;

    private final Consumer<String> m_pushListId;


    private final SharepointListParameters m_listSettings;

    private final TimeoutParameters m_timeoutSettings;

    private final BufferedDataTable m_table;

    private final DataTableSpec m_tableSpec;

    private final ExecutionContext m_exec;

    private final String m_siteId;

    private String m_listId;

    private boolean m_listCreated;

    private ColumnDefinitionCollectionPage m_createdColumns;

    private Optional<String> m_titleColumnDisplayName = Optional.empty();

    private long m_columnsCleared;

    private long m_itemsCleared;

    private boolean m_processItemsSequential = true; // change this with toggle later

    private final boolean m_createMissingList;

    /**
     * Create the client
     *
     * @param siteSettings
     *            the site settings to use
     * @param listSettings
     *            the list settings to use
     * @param timeoutSettings
     *            the timeout settings to use
     * @param pushListId
     *            called with the id of a newly created list or the existing list.
     *            Can be used to export the id. May be {@code id}.
     * @param table
     *            the input table
     * @param credSpec
     *            the credential used to authenticate against the Graph API
     * @param exec
     *            the {@link ExecutionContext} of the executing node; used to set
     *            progress information
     * @throws IOException
     *             if there was an error accessing the Graph API
     * @throws InvalidSettingsException
     *             if the list could not be created due to configuration
     * @throws NoSuchCredentialException
     *             the credential was invalid
     */
    public SharepointListChangingClient(final SharepointSiteParameters siteSettings,
            final SharepointListParameters listSettings, //
            final TimeoutParameters timeoutSettings, //
            final Consumer<String> pushListId, //
            final BufferedDataTable table, //
            final CredentialPortObjectSpec credSpec, //
            final ExecutionContext exec) throws IOException, InvalidSettingsException, NoSuchCredentialException {

        m_listSettings = listSettings;
        m_timeoutSettings = timeoutSettings;
        m_table = table;
        m_tableSpec = m_table.getDataTableSpec();
        m_exec = exec;
        m_client = createGraphServiceClient(credSpec);
        m_pushListId = pushListId;
        m_siteId = siteSettings.getSiteId(m_client);
        m_createMissingList = listSettings instanceof SharepointListParameters.WithCreateLists
                || listSettings instanceof SharepointListParameters.WithCreateListsAndSystemLists;
        m_listId = getListId();
    }

    /**
     * Creates / overwrites / appends a SharePoint list from a KNIME Table.
     *
     * @throws IOException
     *             there was an error accessing the API or settings are determined
     *             invalid by it
     * @throws CanceledExecutionException
     *             execution was cancelled
     */
    public void writeList() throws IOException, CanceledExecutionException {
        m_exec.setMessage("Writing rows");
        final String[] colNames = m_tableSpec.getColumnNames();
        final var overwritePolicy = m_listSettings.getExistingListPolicy().orElseThrow(() -> new IllegalStateException(
                "This method can only be called when the list settings allow creating lists."));

        final var colMap = mapColNames(false);

        if (overwritePolicy == ListExistsPolicy.APPEND && !m_listCreated) {
            checkColumnsForAppend(colMap);
        }

        try (final var batch = new ListBatchRequest(m_client, m_exec); //
                final var iterator = m_table.iterator()) {

            if (overwritePolicy == ListExistsPolicy.OVERWRITE && !m_listCreated) {
                prepareOverwrite(batch);
            }

            long rowNumber = 0;
            final long noRows = m_table.size();
            while (iterator.hasNext()) {
                final var row = iterator.next();
                // update progress
                final long rowNumberFinal = rowNumber;
                m_exec.setProgress(rowNumber / (double) noRows, () -> ("Write row " + rowNumberFinal));
                m_exec.checkCanceled();

                createListItem(row, colNames, colMap, batch);
                rowNumber++;
            }
        }
    }

    /**
     * Updates a SharePoint list from a KNIME Table.
     *
     * @param idColumnName
     *            the name of the column in the input table which holds the
     *            identifier used to select list items to edit. If this value is
     *            {@code null} the row ID will be used instead.
     *
     * @throws IOException
     *             there was an error accessing the API or settings are determined
     *             invalid by it
     * @throws CanceledExecutionException
     *             execution was cancelled
     */
    public void updateList(final String idColumnName) throws IOException, CanceledExecutionException {
        m_exec.setMessage("Updating rows");
        final String[] colNames = m_tableSpec.getColumnNames();
        final var idColIdx = m_tableSpec.findColumnIndex(idColumnName);

        final var colMap = mapColNames(true);
        checkColumnsForUpdate(colMap, idColumnName);

        try (final var batch = new ListBatchRequest(m_client, m_exec); //
                final var iterator = m_table.iterator()) {

            long rowNumber = 0;
            final long noRows = m_table.size();
            while (iterator.hasNext()) {
                final var row = iterator.next();
                final var id = getId(row, idColIdx, rowNumber);
                // update progress
                final long rowNumberFinal = rowNumber;
                m_exec.setProgress(rowNumber / (double) noRows,
                        () -> ("Updating item \"" + id + "\" with row " + rowNumberFinal));
                m_exec.checkCanceled();

                updateListItem(row, colNames, id, idColIdx, colMap, batch);
                rowNumber++;
            }
        }
    }

    /**
     * Extracts an ID cell from a row and checks its content
     *
     * @param row
     *            the row which contains the ID
     * @param index
     *            the cell index within the row, may be {@code -1} to use the row ID
     * @param rowNumber
     *            the current row number as context
     * @throws IOException
     *             if it is not
     */
    private static String getId(final DataRow row, final int index, final long rowNumber) throws IOException {
        if (index < 0) {
            return row.getKey().toString();
        } else {
            final var idCell = row.getCell(index);
            if (idCell.isMissing()) {
                throw new IOException("ID value in row " + rowNumber + " is missing");
            }
            return idCell.toString();
        }
    }

    /**
     * Ensures that all columns in the input table are present in the Sharepoint
     * list and that any required columns in the list are covered by input columns.
     *
     * @param colMap
     *            a mapping between the display name and the internal name +
     *            required status of a Sharepoint list column.
     * @throws IOException
     *             if the check fails.
     */
    private void checkColumnsForAppend(final Map<String, Pair<String, Boolean>> colMap) throws IOException {
        // trying to write to the Title column is not allowed as we use that to write
        // the RowID
        // if there is no other Title column to map to we inform them that they have to
        // use another method to change the Title column to nudge them in the right
        // direction
        if (m_titleColumnDisplayName.isPresent()) {
            final var name = m_titleColumnDisplayName.get();
            if (m_tableSpec.containsName(name) && !colMap.containsKey(name)) {
                throw new IOException("Cannot write to the %s column “%s” as it used for the RowID. "
                        .formatted(COL_TITLE.toLowerCase(Locale.ROOT), name)
                        + "Please edit the content with a RowID node instead of supplying it as "
                        + "an input table column.");
            }
        }

        checkColumnsForUpdate(colMap, null);

        for (final var mapping : colMap.entrySet()) {
            final var readOnly = mapping.getValue().getSecond().booleanValue();
            final var colNameInternal = mapping.getValue().getFirst();
            if (readOnly && !colNameInternal.equals(COL_TITLE) && m_tableSpec.getColumnSpec(mapping.getKey()) == null) {
                throw new IOException(
                        "Sharepoint list specifies mandatory column “%s” which is not present in the input table."
                                .formatted(mapping.getKey()));
            }
        }
    }

    /**
     * Ensures that all columns in the input table are present in the Sharepoint
     * list.
     *
     * @param colMap
     *            a mapping between the display name and the internal name +
     *            required status of a Sharepoint list column.
     * @param idCol
     *            the name of the column holding the ID, will be skipped when
     *            checking; may be {@code null}.
     * @throws IOException
     *             if the check fails.
     */
    private void checkColumnsForUpdate(final Map<String, Pair<String, Boolean>> colMap, final String idCol)
            throws IOException {

        for (final var tableCol : m_tableSpec.getColumnNames()) {
            if (!tableCol.equals(idCol) && !colMap.containsKey(tableCol)) {
                throw new IOException(("Input table specifies column “%s” which is not present in the Sharepoint "
                        + "list or is read-only.").formatted(tableCol));
            }
        }

    }

    private GraphServiceClient<Request> createGraphServiceClient(final CredentialPortObjectSpec credSpecl)
            throws NoSuchCredentialException, IOException {

        return GraphApiUtil.createClient(//
                GraphCredentialUtil.createAuthenticationProvider(credSpecl), //
                m_timeoutSettings.getConnectionTimeoutMillis(), //
                m_timeoutSettings.getReadTimeoutMillis());
    }

    /**
     * Returns the list id in case there is already an id or we create a new list.
     *
     * @return returns the list id
     * @throws IOException
     *             if there was an error accessing the Graph API
     * @throws InvalidSettingsException
     *             if the list could not be created due to configuration
     */
    private String getListId() throws IOException, InvalidSettingsException {
        final var failOnExists = m_listSettings.getExistingListPolicy() //
                .filter(ListExistsPolicy.FAIL::equals).isPresent();

        var result = "";
        if (m_listSettings.getListMode().filter(ListMode.CREATE::equals).isPresent()) {
            result = getOrCreateNewListId(failOnExists);
        } else {
            result = getExistingListId(failOnExists);
        }

        if (m_pushListId != null) {
            m_pushListId.accept(result);
        }

        return result;
    }

    /**
     * Create a list with the given name or reuse an existing one if permitted by
     * node settings
     *
     * @param failOnExists
     *            whether the node should fail if the list already exists
     * @return returns the list id
     * @throws IOException
     *             if there was an error accessing the Graph API
     * @throws InvalidSettingsException
     *             if the list could not be created due to configuration
     */
    private String getOrCreateNewListId(final boolean failOnExists) throws IOException, InvalidSettingsException {
        var toCreate = m_listSettings.getListNameToCreate().orElseThrow();
        final var optionalListId = SharePointListUtils.getListIdByDisplayName(m_client, m_siteId, toCreate);
        if (optionalListId.isPresent() && failOnExists) {
            throw new InvalidSettingsException(
                    "The specified list already exists and the node fails due to overwrite settings");
        }

        return optionalListId.isPresent() ? optionalListId.get()
                : tryCreateSharepointList(toCreate);
    }

    /**
     * Find an existing list depending on legacy settings
     *
     * @param failOnExists
     *            whether the node should fail if the list already exists
     * @return returns the list id
     * @throws IOException
     *             if there was an error accessing the Graph API
     * @throws InvalidSettingsException
     *             if the list could not be found or not created due to
     *             configuration
     */
    private String getExistingListId(final boolean failOnExists) throws IOException, InvalidSettingsException {
        var result = m_listSettings.getExistingListId();
        if (result == null && m_listSettings.isLegacyAndWebUIDialogNeverOpened()) {
            result = SharePointListUtils.getListIdByInternalOrDisplayNameLegacy(m_client, m_siteId,
                    m_listSettings.getExistingListInternalName(), m_listSettings.getExistingListDisplayName())
                    .orElse(null);
        } else if (result == null) {
            result = SharePointListUtils.getListIdByInternalOrDisplayName(m_client, m_siteId,
                    m_listSettings.getExistingListInternalName(), m_listSettings.getExistingListDisplayName())
                    .orElse(null);
        }

        if (result != null && failOnExists) {
            throw new InvalidSettingsException(
                    "The specified list already exists and the node fails due to overwrite settings");
        }
        if (result == null && m_createMissingList) {
            // as a last resort try to create the list
            return tryCreateSharepointList(m_listSettings.getExistingListDisplayName());
        } else if (result == null) {
            throw new InvalidSettingsException(
                    "Could not find a list with display name: " + m_listSettings.getExistingListDisplayName());
        }
        return result;
    }

    /**
     * Creates a new list.
     *
     * @param displayName
     *            the name of the new list to create
     *
     * @return the Id of the created list
     * @throws IOException
     */
    private String tryCreateSharepointList(final String displayName) throws IOException {
        final var list = new com.microsoft.graph.models.List();
        list.displayName = displayName;

        final var columnDefinitionCollectionResponse = new ColumnDefinitionCollectionResponse();
        columnDefinitionCollectionResponse.value = createColumnDefinitions(new HashSet<>());

        final var columnDefinitionCollectionPage = new ColumnDefinitionCollectionPage(
                columnDefinitionCollectionResponse, null);
        list.columns = columnDefinitionCollectionPage;

        try {
            var response = m_client.sites(m_siteId).lists().buildRequest(List.of(new QueryOption("expand", "columns")))
                    .post(list);
            m_listCreated = true;
            m_createdColumns = response.columns;
            return response.id;
        } catch (GraphServiceException ex) {
            if (ex.getServiceError().code.equals("nameAlreadyExists")) {
                throw new IOException("Cannot create list because there already exists a (system) list " //
                        + "of the same name. Please change the name.");
            } else {
                throw new IOException("Error during list creation: " + ex.getServiceError().message, ex);
            }
        }
    }

    /**
     * Creates a {@link LinkedList} of {@link ColumnDefinition} based on the
     * {@link DataTableSpec}.
     *
     * @param existingNames
     *            a list of already existing, lower-case internal names. This set
     *            will be modified with newly created internal names.
     *
     * @return {@link LinkedList} of {@link ColumnDefinition}
     */
    private LinkedList<ColumnDefinition> createColumnDefinitions(final Set<String> existingNames) {
        final LinkedList<ColumnDefinition> columnDefinitions = new LinkedList<>();
        m_tableSpec.forEach(c -> {
            final DataType type = c.getType();
            columnDefinitions.add(KNIMEToSharepointTypeConverter.TYPE_CONVERTER
                    .getOrDefault(type, KNIMEToSharepointTypeConverter.DEFAULT_CONVERTER).getSecond()
                    .apply(existingNames, c.getName()));
        });

        return columnDefinitions;
    }

    /**
     * Maps the display name to the actual internal name created by SharePoint.
     *
     * @param allowTitleColumn
     *            whether the special Title column will be filtered out
     *
     * @return a {@link Map} which maps display name and (internal name, required
     *         status)
     * @throws IOException
     */
    private Map<String, Pair<String, Boolean>> mapColNames(final boolean allowTitleColumn) throws IOException {
        try {
            var columns = m_createdColumns != null //
                    ? m_createdColumns //
                    : createListRequestBuilder().columns().buildRequest().get();

            final var colDefs = new LinkedList<>(columns.getCurrentPage());

            var nextRequest = columns.getNextPage();
            while (nextRequest != null) {
                columns = nextRequest.buildRequest().get();
                colDefs.addAll(columns.getCurrentPage());
                nextRequest = columns.getNextPage();
            }

            m_titleColumnDisplayName = colDefs.stream() //
                    .filter(c -> COL_TITLE.equals(c.name)).findAny().map(c -> c.displayName);
            final var ignoredInternalCols = allowTitleColumn ? EFFECTIVELY_RO_COLS : SPECIAL_COLS;
            return colDefs.stream() //
                    .filter(c -> !c.readOnly) // we cannot write these anyways
                    .filter(c -> !ignoredInternalCols.contains(c.name)) // not writable or used by RowID
                    .sorted(Comparator.comparing(c -> c.name)) //
                    .collect(Collectors.toMap(c -> c.displayName, c -> Pair.create(c.name, c.required), //
                            (c1, c2) -> c1)); // select first
        } catch (GraphServiceException ex) {
            throw new IOException("Error while mapping column names: " + ex.getServiceError().message, ex);
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
    private void createListItem(final DataRow row, final String[] colNames,
            final Map<String, Pair<String, Boolean>> colMap, final ListBatchRequest batch)
            throws IOException, CanceledExecutionException {
        var i = 0;
        final var fvs = new FieldValueSet();

        fvs.additionalDataManager().put(COL_TITLE, new JsonPrimitive(row.getKey().getString()));

        for (final var cell : row) {
            final var listColNameInternal = colMap.get(colNames[i]).getFirst();

            if (!cell.isMissing() && listColNameInternal != null) {
                fvs.additionalDataManager().put(listColNameInternal,
                        KNIMEToSharepointTypeConverter.TYPE_CONVERTER
                                .getOrDefault(cell.getType(), KNIMEToSharepointTypeConverter.DEFAULT_CONVERTER)//
                                .getFirst()//
                                .apply(cell));
            }
            i++;
        }

        final var li = new ListItem();
        li.fields = fvs;

        batch.post(createListRequestBuilder().items().buildRequest(), li, m_processItemsSequential);
    }

    /**
     * Updates a {@link ListItem} and sends it to SharePoint.
     *
     * @param row
     *            the current {@link DataRow}
     * @param colNames
     *            the column names of the table
     * @param id
     *            the ID of the item to update
     * @param idColIndex
     *            the index of the column containing the ID value
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
    private void updateListItem(final DataRow row, final String[] colNames, final String id, final int idColIndex,
            final Map<String, Pair<String, Boolean>> colMap, final ListBatchRequest batch)
            throws IOException, CanceledExecutionException {
        var i = 0;
        final var fvs = new FieldValueSet();

        for (final var cell : row) {
            if (i == idColIndex) {
                i++;
                continue;
            }
            final var listColNameInternal = colMap.get(colNames[i]).getFirst();

            if (listColNameInternal != null) {
                if (cell.isMissing()) {
                    fvs.additionalDataManager().put(listColNameInternal, null);
                } else {
                    fvs.additionalDataManager().put(listColNameInternal,
                            KNIMEToSharepointTypeConverter.TYPE_CONVERTER
                                    .getOrDefault(cell.getType(), KNIMEToSharepointTypeConverter.DEFAULT_CONVERTER)//
                                    .getFirst()//
                                    .apply(cell));
                }
            }
            i++;
        }

        batch.patch(createListRequestBuilder().items(id).fields().buildRequest(), fvs, m_processItemsSequential);
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
        synchronized (LOCK) {
            // Multiple parallel executions of different nodes may influence each other
            // otherwise, resulting in weird errors.
            // It still works if we wait for "Retry-After", but it's _really_ slow.
            // Just locking is a lot faster.
            //
            // We also keep track of the internal names of columns which were not deleted
            // as Sharepoint only disambiguates the names if we call the create list
            // endpoint. If we try to add columns to an existing list we get
            // name-already-exists errors which causes weird behaviour if the table contains
            // columns which have the same name as system columns: creating a new list
            // succeeds while replacing it fails (even if node settings aren't changed).
            // Thus we generate the internal names for ourselves to remain
            // consistent.
            final var existingLowerCase = deleteColumns(batch);
            createColumns(existingLowerCase, batch);
            final var results = batch.tryCompleteAllCurrentRequests();
            // avoid making a second request for the column definitions
            // note:
            // this will only contain columns we explicitly created, so we won't
            // be aware of any system columns to begin with
            parseCreatedColumnsFromOverwrite(results);
        }
        // The following is node independent so it has to be separated
        deleteListItems(batch);
        if (m_processItemsSequential) {
            // Switch to sequential: has to separated
            batch.tryCompleteAllCurrentRequests();
        }
    }

    /**
     * Parses the responses from the column creation batch request made when
     * preparing a list overwrite. This is done done avoid having to do a second
     * request in {@link #mapColNames(boolean)} and to ensure that the correct
     * column names are mapped.
     *
     * @param batchRequests
     *            the array containing the responses from each batch request; return
     *            value of {@link ListBatchRequest#tryCompleteAllCurrentRequests()}.
     */
    private void parseCreatedColumnsFromOverwrite(final JsonArray batchRequests) {
        final var serializer = m_client.getSerializer();
        final var columnDefs = new LinkedList<ColumnDefinition>();
        for (final var batchRequest : batchRequests) {
            for (final var response : batchRequest.getAsJsonArray()) {
                final var obj = response.getAsJsonObject();
                switch (obj.get("status").getAsInt()) {
                case 201 /* CREATED */ -> columnDefs.add(serializer.deserializeObject( //
                        obj.getAsJsonObject("body"), ColumnDefinition.class));
                case 204 /* DELETED */ -> {
                    // ignore
                }
                default -> throw new IllegalStateException( //
                        "Unexpected status when parsing column creation response: " //
                        + obj.get("status").getAsInt());
                }
            }
        }
        m_createdColumns = new ColumnDefinitionCollectionPage(columnDefs, null);
    }

    /**
     * Creates columns for an existing list
     *
     * @param existingNamesLowerCase
     *            a list of already existing, lower-case internal names. This set
     *            will be modified with newly created internal names.
     * @param batch
     *            {@link ListBatchRequest} used to accumulate and execute batch
     *            requests
     *
     * @throws IOException
     *             if some part of the column creation failed. This may get
     *             triggered at a later point due to batching.
     * @throws CanceledExecutionException
     */
    private void createColumns(final Set<String> existingNamesLowerCase, final ListBatchRequest batch)
            throws IOException, CanceledExecutionException {
        m_exec.setMessage("Creating columns");
        final var columnDefinitions = createColumnDefinitions(existingNamesLowerCase);
        var columnNumber = 0L;
        final var totalColumns = columnDefinitions.size();
        for (final var columnDefinition : columnDefinitions) {
            columnNumber++;
            m_exec.setMessage(String.format("Create column %d/%d for overwrite", columnNumber, totalColumns));
            m_exec.checkCanceled();
            // force sequential here so that SharePoint doesn't stumble over itself and
            // loses data.
            batch.post(createListRequestBuilder().columns().buildRequest(), columnDefinition, true);
        }
        m_exec.setProgress(Double.NaN);
    }

    /**
     * Deletes all deleteable columns from a list.
     *
     * @param batch
     *            {@link ListBatchRequest} used to accumulate and execute batch
     *            requests
     *
     * @return a list of already existing, lower-case internal names.
     *
     * @throws IOException
     *             if some part of the column deletion failed. This may get
     *             triggered at a later point due to batching.
     * @throws CanceledExecutionException
     */
    private Set<String> deleteColumns(final ListBatchRequest batch) throws IOException, CanceledExecutionException {
        m_exec.setMessage("Deleting columns");
        final var existing = new HashSet<String>();
        try {
            m_columnsCleared = 0;
            var nextRequest = createListRequestBuilder().columns();
            while (nextRequest != null) {
                final var columns = nextRequest.buildRequest().get();
                existing.addAll(deleteColumns(columns.getCurrentPage(), batch));
                nextRequest = columns.getNextPage();
            }
            return existing;
        } catch (GraphServiceException ex) {
            throw new IOException("Error while fetching columns for deletion: " + ex.getServiceError().message, ex);
        }
    }

    private Set<String> deleteColumns(final List<ColumnDefinition> colDefList, final ListBatchRequest batch)
            throws IOException, CanceledExecutionException {
        final var existing = new HashSet<String>();
        for (final var c : colDefList) {
            // Read only columns and Title, ContentType and Attachments column can't be
            // deleted
            if (Boolean.FALSE.equals(c.readOnly) && !SPECIAL_COLS.contains(c.name)) {
                m_columnsCleared++;
                m_exec.setMessage(m_columnsCleared + " columns cleared");
                m_exec.checkCanceled();
                // force sequential here so that SharePoint doesn't stumble over itself and
                // loses data.
                batch.delete(createListRequestBuilder().columns(c.id).buildRequest(), true);
            } else {
                existing.add(c.name.toLowerCase(Locale.ROOT));
            }
        }
        return existing;
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
        m_exec.setMessage("Deleting items");
        try {
            m_itemsCleared = 0;
            var nextRequest = createListRequestBuilder().items();

            while (nextRequest != null) {
                final var items = nextRequest.buildRequest().get();
                deleteListItems(items, batch);
                nextRequest = items.getNextPage();
            }
        } catch (GraphServiceException ex) {
            throw new IOException("Error while fetching list items for deletion: " + ex.getServiceError().message, ex);
        }
    }

    private void deleteListItems(final ListItemCollectionPage listItems, final ListBatchRequest batch)
            throws IOException, CanceledExecutionException {

        for (var item : listItems.getCurrentPage()) {
            m_itemsCleared++;
            m_exec.setMessage(m_itemsCleared + " items cleared");
            m_exec.checkCanceled();
            batch.delete(createListRequestBuilder().items(item.id).buildRequest(), false);
        }
    }

    private ListRequestBuilder createListRequestBuilder() {
        return m_client.sites(m_siteId).lists(m_listId);
    }

    @Override
    public void close() throws Exception {
        // Nothing to do
    }
}
