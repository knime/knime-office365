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
 *   14 Feb 2022 (Lars Schweikardt, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.lists.node.writer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Function;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.util.Pair;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.BooleanColumn;
import com.microsoft.graph.models.extensions.ColumnDefinition;
import com.microsoft.graph.models.extensions.FieldValueSet;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.List;
import com.microsoft.graph.models.extensions.ListItem;
import com.microsoft.graph.models.extensions.NumberColumn;
import com.microsoft.graph.models.extensions.TextColumn;
import com.microsoft.graph.requests.extensions.ColumnDefinitionCollectionPage;
import com.microsoft.graph.requests.extensions.ColumnDefinitionCollectionResponse;
import com.microsoft.graph.requests.extensions.IListItemCollectionPage;

/**
 * “SharePoint List Writer” implementation of a {@link NodeModel}.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class SharepointListWriterNodeModel extends NodeModel {

    /**
     * Map which holds a Pair of Functions which can convert data values to
     * {@link JsonPrimitive} and creates named {@link ColumnDefinition} based on a
     * DataType
     */
    private static final Map<DataType, Pair<Function<DataCell, JsonElement>, Function<String, ColumnDefinition>>> TYPE_CONVERTER = new HashMap<>();

    /** If no suitable converter is available */
    private static final Pair<Function<DataCell, JsonElement>, Function<String, ColumnDefinition>> DEFAULT_CONVERTER = Pair
            .create(s -> new JsonPrimitive(s.toString()), SharepointListWriterNodeModel::createStringColDefiniton);

    static {
        TYPE_CONVERTER.put(StringCell.TYPE, Pair.create(s -> new JsonPrimitive(s.toString()),
                SharepointListWriterNodeModel::createStringColDefiniton));
        TYPE_CONVERTER.put(IntCell.TYPE, Pair.create(s -> new JsonPrimitive(((IntCell) s).getIntValue()),
                SharepointListWriterNodeModel::createNumberColDefiniton));
        TYPE_CONVERTER.put(DoubleCell.TYPE, Pair.create(SharepointListWriterNodeModel::doubleParser,
                SharepointListWriterNodeModel::createNumberColDefiniton));
        TYPE_CONVERTER.put(LongCell.TYPE, Pair.create(s -> new JsonPrimitive(((LongCell) s).getLongValue()),
                SharepointListWriterNodeModel::createNumberColDefiniton));
        TYPE_CONVERTER.put(BooleanCell.TYPE, Pair.create(s -> new JsonPrimitive(((BooleanCell) s).getBooleanValue()),
                SharepointListWriterNodeModel::createBooleanColDefiniton));
    }

    private String m_siteId;

    private String m_listId;

    protected SharepointListWriterNodeModel() {
        super(new PortType[] { MicrosoftCredentialPortObject.TYPE, BufferedDataTable.TYPE }, new PortType[] {});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final DataTableSpec inputTableSpec = (DataTableSpec) inSpecs[1];

        for (var i = 0; i < inputTableSpec.getNumColumns(); i++) {
            final String colName = inputTableSpec.getColumnSpec(i).getName();
            if (colName.length() > 255) {
                throw new InvalidSettingsException(
                        "One or more column names do have a length over 255 characters, which is not allowed. Please reduce the length.");
            }
        }
        return new PortObjectSpec[] {};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final var authPortSpec = (MicrosoftCredentialPortObjectSpec) inObjects[0].getSpec();
        final var table = (BufferedDataTable) inObjects[1];
        final var client = GraphApiUtil.createClient(authPortSpec.getMicrosoftCredential());

        // TODO will be assigned through the settings in the future
        m_siteId = "root";
        m_listId = "listID";

        writeList(client, table, exec);

        return new PortObject[] {};
    }

    private void writeList(final IGraphServiceClient client, final BufferedDataTable table, final ExecutionContext exec)
            throws Exception {
        // TODO for the time being we always delete the list with the name
        // "createdFromKNIMETable" to create a new one afterwards
        deleteList(client);

        // TODO we will not always create a new list in the future
        m_listId = createSharepointList(client, table.getDataTableSpec());

        final String[] colNames = table.getDataTableSpec().getColumnNames();
        final var colMap = mapColNames(client);

        long rowNo = 0;
        final long noRows = table.size();
        try (final var iterator = table.iterator()) {
            while (iterator.hasNext()) {
                final var row = iterator.next();
                createListItem(client, row, colNames, colMap);

                // update progress
                final long rowNoFinal = rowNo;
                exec.setProgress(rowNo / (double) noRows, () -> ("Write row " + rowNoFinal));
                exec.checkCanceled();
                rowNo++;
            }
        }
    }

    /**
     * Maps the display name to the actual internal name created by SharePoint.
     *
     * @param client
     *            the {@link IGraphServiceClient}
     * @return a {@link Map} which maps display name and internal name
     * @throws IOException
     */
    private Map<String, String> mapColNames(final IGraphServiceClient client) throws IOException {
        try {
            final var columns = client.sites(m_siteId).lists(m_listId).columns().buildRequest().get();
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
     * Creates a new list.
     *
     * @param client
     *            the {@link IGraphServiceClient}
     * @param spec
     *            the {@link DataTableSpec}
     * @return the Id of the created list
     * @throws IOException
     */
    private String createSharepointList(final IGraphServiceClient client, final DataTableSpec spec) throws IOException {
        final var list = new List();
        // TODO will be replaced through settings
        list.displayName = "createdFromKNIMETable";

        final var columnDefinitionCollectionResponse = new ColumnDefinitionCollectionResponse();
        columnDefinitionCollectionResponse.value = createColumnDefinitions(spec);

        final var columnDefinitionCollectionPage = new ColumnDefinitionCollectionPage(
                columnDefinitionCollectionResponse, null);
        list.columns = columnDefinitionCollectionPage;

        try {
            var response = client.sites(m_siteId).lists().buildRequest().post(list);
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
     * @param spec
     *            the {@link DataTableSpec}
     * @return {@link LinkedList} of {@link ColumnDefinition}
     */
    private static LinkedList<ColumnDefinition> createColumnDefinitions(final DataTableSpec spec) {
        final LinkedList<ColumnDefinition> columnDefinitions = new LinkedList<>();
        spec.forEach(c -> {
            final DataType type = c.getType();
            columnDefinitions.add(TYPE_CONVERTER.getOrDefault(type, DEFAULT_CONVERTER).getSecond().apply(c.getName()));
        });

        return columnDefinitions;
    }

    /**
     * Creates a {@link ListItem} and sends it to SharePoint.
     *
     * @param client
     *            the {@link IGraphServiceClient}
     * @param row
     *            the current {@link DataRow}
     * @param colNames
     *            the column names of the table
     * @param colMap
     *            the mapping of the column names
     * @throws Exception
     */
    private void createListItem(final IGraphServiceClient client, final DataRow row, final String[] colNames,
            final Map<String, String> colMap) throws IOException {
        var i = 0;
        final var li = new ListItem();
        final var fvs = new FieldValueSet();

        for (var cell : row) {
            final String colName = colMap.get(colNames[i]);
            if (!cell.isMissing() && colName != null) {
                fvs.additionalDataManager().put(colName, TYPE_CONVERTER.getOrDefault(cell.getType(), DEFAULT_CONVERTER)//
                        .getFirst()//
                        .apply(cell));
            }
            i++;
        }

        li.fields = fvs;

        try {
            client.sites(m_siteId).lists(m_listId).items().buildRequest().post(li);
        } catch (GraphServiceException ex) {
            throw new IOException("Error during creation of a row with error: " + ex.getServiceError().message, ex);
        }
    }

    // TODO temporary method to delete a list completely
    private void deleteList(final IGraphServiceClient client) throws Exception {
        try {
            client.sites(m_siteId).lists("createdFromKNIMETable").buildRequest().delete();
        } catch (GraphServiceException ex) {
            // Do nothing at the moment -> temp method anyway
        }
    }

    // TODO method might will be used in the future to clear a list
    private void deleteListItems(final IGraphServiceClient client) throws IOException {
        try {
            final IListItemCollectionPage listItems = client.sites(m_siteId).lists(m_listId).items().buildRequest()
                    .get();

            deleteListItems(listItems, client);

            var req = listItems.getNextPage();
            while (req != null) {
                final var items = req.buildRequest().get();
                deleteListItems(items, client);
                items.getNextPage();
            }
        } catch (GraphServiceException ex) {
            throw new IOException("Error during deletion of a list item with error: " + ex.getServiceError().message,
                    ex);
        }
    }

    // TODO method might will be used in the future to clear a list
    private void deleteListItems(final IListItemCollectionPage listItems, final IGraphServiceClient client) {
        final var listItemsJson = listItems.getRawObject().getAsJsonObject().get("value").getAsJsonArray();
        listItemsJson.forEach(i -> {
            final var id = i.getAsJsonObject().get("id").getAsString();
            client.sites(m_siteId).lists(m_listId).items(id).buildRequest().delete();
        });
    }

    private static JsonElement doubleParser(final DataCell dataCell) {
        final var val = ((DoubleValue) dataCell).getDoubleValue();
        if (Double.isInfinite(val) || Double.isNaN(val)) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(val);
    }

    private static ColumnDefinition createColDefintion(final String name) {
        final var colDef = new ColumnDefinition();
        colDef.name = name;
        return colDef;
    }

    private static ColumnDefinition createStringColDefiniton(final String name) {
        final ColumnDefinition colDef = createColDefintion(name);
        final var textCol = new TextColumn();
        textCol.allowMultipleLines = true;
        colDef.text = textCol;
        return colDef;
    }

    private static ColumnDefinition createBooleanColDefiniton(final String name) {
        final ColumnDefinition colDef = createColDefintion(name);
        colDef.msgraphBoolean = new BooleanColumn();
        return colDef;
    }

    private static ColumnDefinition createNumberColDefiniton(final String name) {
        final ColumnDefinition colDef = createColDefintion(name);
        colDef.number = new NumberColumn();
        return colDef;
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // TODO not implemented yet
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // TODO not implemented yet
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        // TODO not implemented yet
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        // TODO not implemented yet
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        // TODO not implemented yet
    }

    @Override
    protected void reset() {
        // TODO not implemented yet
    }
}
