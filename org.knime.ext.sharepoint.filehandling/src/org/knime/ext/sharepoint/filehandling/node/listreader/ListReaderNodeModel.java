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
 *   2020-05-02 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataCellFactory;
import org.knime.core.data.DataCellFactory.FromString;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.MissingCell;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
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
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.sharepoint.filehandling.GraphApiAuthenticationProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import com.microsoft.graph.requests.extensions.IColumnDefinitionCollectionPage;
import com.microsoft.graph.requests.extensions.IListItemCollectionPage;

/**
 * Node model for the Sharepoint Connector node.
 *
 * @author Alexander Bondaletov
 */
@SuppressWarnings("deprecation")
final class ListReaderNodeModel extends NodeModel {

    private final ListReaderNodeSettings m_settings = new ListReaderNodeSettings();

    /**
     * Creates new instance.
     */
    ListReaderNodeModel() {
        super(new PortType[] { MicrosoftCredentialPortObject.TYPE }, new PortType[] { BufferedDataTable.TYPE });
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final MicrosoftCredential connection = ((MicrosoftCredentialPortObjectSpec) inObjects[0].getSpec())
                .getMicrosoftCredential();
        final IGraphServiceClient graphClient = GraphServiceClient.builder()
                .authenticationProvider(createGraphAuthProvider(connection)).buildClient();

        final DataTableSpec dataTableSpec = createSpec(graphClient);

        // TODO create outputspec
        final BufferedDataContainer container = exec.createDataContainer(dataTableSpec);

        fillTable(container, exec, graphClient, dataTableSpec);

        return new PortObject[] { container.getTable() };
    }

    private void fillTable(final BufferedDataContainer container, final ExecutionContext exec,
            final IGraphServiceClient client, final DataTableSpec spec) throws ClientException, IOException {

        final LinkedList<Option> requestOptions = new LinkedList<Option>();
        requestOptions.add(new QueryOption("expand", "fields"));
        final IListItemCollectionPage items = client.sites("root").lists("07540548-5b44-4430-a1ab-9de37a102e79").items()
                .buildRequest(requestOptions).get();

        final Iterator<JsonElement> valueIterator = items.getRawObject().getAsJsonArray("value").iterator();

        long i = 0;
        while (valueIterator.hasNext()) {
            final JsonObject fields = ((JsonObject) valueIterator.next()).getAsJsonObject("fields");
            container.addRowToTable(fillCells(spec, fields, exec, i));
            i++;
        }

        container.close();

    }

    private static DefaultRow fillCells(final DataTableSpec spec, final JsonObject fields, final ExecutionContext exec,
            final long rowKey) {
        final String[] colNames = spec.getColumnNames();
        final DataCell[] cells = new DataCell[colNames.length];

        // TODO how to handle images? how to handle persons?
        for (int i = 0; i < colNames.length; i++) {
            final String colName = colNames[i];
            final JsonElement field = fields.get(colName);
            if (field == null) {
                cells[i] = new MissingCell("Empty Cell");
            } else {
                String value = "";
                // TODO hack for now
                if (field.isJsonObject() && field.getAsJsonObject().get("Url") != null) {
                    value = field.getAsJsonObject().get("Url").getAsString();
                } else {
                    value = field.getAsString();
                }
                final DataType type = spec.getColumnSpec(colName).getType();
                final DataCellFactory fac = type.getCellFactory(exec).orElseThrow(
                        () -> new IllegalArgumentException("No data cell factory for data type '" + type + "' found"));

                cells[i] = ((FromString) fac).createCell(value);
            }

        }

        return new DefaultRow(RowKey.createRowKey(rowKey), cells);

    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        // TODO what to do here?
        return new DataTableSpec[] { null };
    }

    private DataTableSpec createSpec(final IGraphServiceClient client) {
        final IColumnDefinitionCollectionPage columns = client.sites("root")
                .lists("07540548-5b44-4430-a1ab-9de37a102e79").columns().buildRequest().get();

        final JsonObject jsonColumns = columns.getRawObject();

        final JsonArray values = jsonColumns.getAsJsonArray("value");

        final int noCols = values.size();
        final ArrayList<DataColumnSpec> colSpec = new ArrayList<DataColumnSpec>();

        final Iterator<JsonElement> it = values.iterator();

        while (it.hasNext()) {
            final JsonObject value = (JsonObject) it.next();
            // TODO Temporary hack until I know how to distiguish those "hidden" cols
            // attachements and another columns is still in there
            if (value.get("readOnly").getAsBoolean() == true) {
                continue;
            } else {
                colSpec.add(specMapper(value));
            }

        }

        return new DataTableSpec(colSpec.toArray(new DataColumnSpec[colSpec.size()]));
    }

    private static DataColumnSpec specMapper(final JsonObject value) {
        // TODO how to recognize location col? hyperlink col? image col?
        final String displayName = value.get("displayName").getAsString();
        final DataColumnSpec spec;

        if (value.get("text") != null) {
            spec = new DataColumnSpecCreator(displayName, StringCell.TYPE).createSpec();
        } else if (value.get("number") != null || value.get("currency") != null) {
            // TODO int and double?
            spec = new DataColumnSpecCreator(displayName, DoubleCell.TYPE).createSpec();
        } else if (value.get("boolean") != null) {
            // TODO int and double?
            spec = new DataColumnSpecCreator(displayName, BooleanCell.TYPE).createSpec();
        } else if (value.get("dateTime") != null) {
            // TODO new datetimetypes import knime.base
            spec = new DataColumnSpecCreator(displayName, StringCell.TYPE).createSpec();
        } else {
            spec = new DataColumnSpecCreator(displayName, StringCell.TYPE).createSpec();
        }

        return spec;
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        setWarningMessage("Sharepoint connection no longer available. Please re-execute the node.");
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // nothing to save

    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettingsTo(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.validateSettings(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.loadSettingsFrom(settings);
    }

    @Override
    protected void onDispose() {
        // close the file system also when the workflow is closed
        reset();
    }

    /**
     * Creates {@link IAuthenticationProvider} from the given
     * {@link MicrosoftCredential} object.
     *
     * @param connection
     *            The Microsoft connection object.
     * @return The {@link IAuthenticationProvider} instance.
     * @throws MalformedURLException
     */
    static IAuthenticationProvider createGraphAuthProvider(final MicrosoftCredential connection) throws IOException {

        if (!(connection instanceof OAuth2Credential)) {
            throw new UnsupportedOperationException("Unsupported credential type: " + connection.getType());
        }

        final String accessToken = ((OAuth2Credential) connection).getAccessToken().getToken();

        return new GraphApiAuthenticationProvider(accessToken);
    }

    @Override
    protected void reset() {
        // TODO nothing TODO?
    }
}
