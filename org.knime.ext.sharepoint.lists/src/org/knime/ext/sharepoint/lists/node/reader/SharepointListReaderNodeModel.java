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
 *   Nov 4, 2020 (Tobias): created
 */
package org.knime.ext.sharepoint.lists.node.reader;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.knime.core.data.DataType;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.streamable.InputPortRole;
import org.knime.core.node.streamable.OutputPortRole;
import org.knime.core.node.streamable.PartitionInfo;
import org.knime.core.node.streamable.PortInput;
import org.knime.core.node.streamable.PortOutput;
import org.knime.core.node.streamable.RowOutput;
import org.knime.core.node.streamable.StreamableOperator;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.lists.node.reader.framework.SharepointListClient;
import org.knime.ext.sharepoint.lists.node.reader.framework.SharepointListReader;
import org.knime.ext.sharepoint.lists.node.reader.mapping.SharepointListReadAdapterFactory;
import org.knime.filehandling.core.node.table.reader.DefaultMultiTableReadFactory;
import org.knime.filehandling.core.node.table.reader.DefaultSourceGroup;
import org.knime.filehandling.core.node.table.reader.MultiTableReader;
import org.knime.filehandling.core.node.table.reader.ProductionPathProvider;
import org.knime.filehandling.core.node.table.reader.SourceGroup;
import org.knime.filehandling.core.node.table.reader.rowkey.DefaultRowKeyGeneratorContextFactory;

/**
 * Node model implementation of the “SharePoint List Reader” node.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class SharepointListReaderNodeModel extends NodeModel {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListReaderNodeModel.class);

    private final SharepointListReaderMultiTableReadConfig m_config;

    /**
     * A supplier is used to avoid any issues should this node model ever be used in
     * parallel. However, this also means that the specs are recalculated for each
     * generated reader.
     */
    private final MultiTableReader<SharepointListClient, SharepointListReaderConfig, DataType> m_tableReader;

    private final InputPortRole[] m_inputPortRoles;

    SharepointListReaderNodeModel() {
        super(new PortType[] { MicrosoftCredentialPortObject.TYPE }, new PortType[] { BufferedDataTable.TYPE });
        m_inputPortRoles = new InputPortRole[1];
        // Row key generation is not distributable.
        Arrays.fill(m_inputPortRoles, InputPortRole.NONDISTRIBUTED_STREAMABLE);
        m_config = createConfig();
        final var multiTableReadFactory = createReadFactory();
        m_tableReader = new MultiTableReader<>(multiTableReadFactory);
    }

    static SharepointListReaderMultiTableReadConfig createConfig() {
        return new SharepointListReaderMultiTableReadConfig();
    }

    static DefaultMultiTableReadFactory<SharepointListClient, SharepointListReaderConfig, DataType, Object> createReadFactory() {
        final var readAdapterFactory = SharepointListReadAdapterFactory.INSTANCE;
        final var productionPathProvider = createProductionPathProvider();
        return new DefaultMultiTableReadFactory<>(//
                SharepointListReadAdapterFactory.TYPE_HIERARCHY, // <DataType, DataType>
                new DefaultRowKeyGeneratorContextFactory<>(Object::toString, "Table"), // <SharepointListClient, Object>
                new SharepointListReader(), // <SharepointListClient, Config, DataType, Object>
                productionPathProvider, // <DataType>
                readAdapterFactory::createReadAdapter); // <DataType, Object>
    }

    static ProductionPathProvider<DataType> createProductionPathProvider() {
        final var readAdapterFactory = SharepointListReadAdapterFactory.INSTANCE;
        return readAdapterFactory.createProductionPathProvider();
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        if (m_config.hasTableSpecConfig()) {
            return new PortObjectSpec[] { m_config.getTableSpecConfig().getDataTableSpec() };
        }
        return new PortObjectSpec[1];
    }

    @Override
    protected PortObject[] execute(final PortObject[] inStrings, final ExecutionContext exec) throws Exception {
        final var sourceGroup = createSourceGroup(inStrings,
                m_config.getReaderSpecificConfig().getSharepointListSettings());
        return new PortObject[] { m_tableReader.readTable(sourceGroup, m_config, exec) };
    }

    private static SourceGroup<SharepointListClient> createSourceGroup(final PortObject[] inObjects,
            final SharepointListSettings settings) throws IOException, InvalidSettingsException {

        final var credential = ((MicrosoftCredentialPortObject) inObjects[0]).getMicrosoftCredentials();
        if (credential == null) {
            throw new InvalidSettingsException("Not authenticated!");
        }

        return new DefaultSourceGroup<>("igraph_service_client_source_group",
                Collections.singleton(new SharepointListClient(GraphApiUtil.createClient(credential), settings)));
    }

    private static SourceGroup<SharepointListClient> createSourceGroup(final PortInput[] inputs,
            final SharepointListSettings settings) {
        return new DefaultSourceGroup<>("igraph_service_client_source_group", Collections.singleton(null));
    }

    @Override
    public StreamableOperator createStreamableOperator(final PartitionInfo partitionInfo,
            final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new StreamableOperator() {
            @Override
            public void runFinal(final PortInput[] inputs, final PortOutput[] outputs, final ExecutionContext exec)
                    throws Exception {
                final var sourceGroup = createSourceGroup(inputs,
                        m_config.getReaderSpecificConfig().getSharepointListSettings());
                m_tableReader.fillRowOutput(sourceGroup, m_config, (RowOutput) outputs[0], exec);
            }
        };
    }

    @Override
    public InputPortRole[] getInputPortRoles() {
        return m_inputPortRoles;
    }

    @Override
    public OutputPortRole[] getOutputPortRoles() {
        return new OutputPortRole[] { OutputPortRole.DISTRIBUTED };
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec) {
        setWarningMessage("Sharepoint connection no longer available. Please re-execute the node.");
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec) {
        // no internals to load
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_config.saveInModel(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.validate(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.loadInModel(settings);
    }

    @Override
    protected void reset() {
        m_tableReader.reset();
    }

}
