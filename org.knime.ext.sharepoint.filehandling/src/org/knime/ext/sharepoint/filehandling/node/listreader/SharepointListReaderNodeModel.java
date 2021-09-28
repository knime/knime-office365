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
package org.knime.ext.sharepoint.filehandling.node.listreader;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.streamable.InputPortRole;
import org.knime.core.node.streamable.OutputPortRole;
import org.knime.core.node.streamable.PartitionInfo;
import org.knime.core.node.streamable.PortInput;
import org.knime.core.node.streamable.PortOutput;
import org.knime.core.node.streamable.RowOutput;
import org.knime.core.node.streamable.StreamableOperator;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.sharepoint.filehandling.node.listreader.framework.SharepointListAccessor;
import org.knime.ext.sharepoint.filehandling.node.listreader.framework.SharepointListReader;
import org.knime.ext.sharepoint.filehandling.node.listreader.mapping.SharepointListReadAdapterFactory;
import org.knime.filehandling.core.node.table.reader.DefaultMultiTableReadFactory;
import org.knime.filehandling.core.node.table.reader.DefaultProductionPathProvider;
import org.knime.filehandling.core.node.table.reader.DefaultSourceGroup;
import org.knime.filehandling.core.node.table.reader.MultiTableReader;
import org.knime.filehandling.core.node.table.reader.ProductionPathProvider;
import org.knime.filehandling.core.node.table.reader.SourceGroup;
import org.knime.filehandling.core.node.table.reader.config.StorableMultiTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.tablespec.TableSpecConfig;
import org.knime.filehandling.core.node.table.reader.rowkey.DefaultRowKeyGeneratorContextFactory;

import com.microsoft.graph.requests.extensions.GraphServiceClient;

/**
 * Node model implementation of the table manipulation node.
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
final class SharepointListReaderNodeModel extends NodeModel {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListReaderNodeModel.class);

    private final StorableMultiTableReadConfig<SharepointListReaderConfig, Class<?>> m_config;

    /**
     * A supplier is used to avoid any issues should this node model ever be used in
     * parallel. However, this also means that the specs are recalculated for each
     * generated reader.
     */
    private final MultiTableReader<SharepointListAccessor, SharepointListReaderConfig, Class<?>> m_tableReader;

    private final InputPortRole[] m_inputPortRoles;

    SharepointListReaderNodeModel(final PortsConfiguration portConfig) {
        super(portConfig.getInputPorts(), portConfig.getOutputPorts());
        final int noOfInputPorts = portConfig.getInputPorts().length;
        m_inputPortRoles = new InputPortRole[noOfInputPorts];
        // Row key generation is not distributable.
        Arrays.fill(m_inputPortRoles, InputPortRole.NONDISTRIBUTED_STREAMABLE);
        m_config = createConfig();
        final var multiTableReadFactory = createReadFactory();
        m_tableReader = new MultiTableReader<>(multiTableReadFactory);
    }

    static SharepointListReaderMultiTableReadConfig createConfig() {
        return new SharepointListReaderMultiTableReadConfig();
    }

    static DefaultMultiTableReadFactory<SharepointListAccessor, SharepointListReaderConfig, Class<?>, String> createReadFactory() {
        final var readAdapterFactory = SharepointListReadAdapterFactory.INSTANCE;
        final var productionPathProvider = createProductionPathProvider();
        return new DefaultMultiTableReadFactory<>(//
                SharepointListReadAdapterFactory.TYPE_HIERARCHY, // <Class<?>, Class<?>>
                new DefaultRowKeyGeneratorContextFactory<>(String::toString, "Table"), // <SharepointListAccessor, String>
                new SharepointListReader(), // <SharepointListAccessor, Config, Class<?>, String>
                productionPathProvider, // <Class<?>>
                readAdapterFactory::createReadAdapter); // <Class<?>, String>
    }

    static ProductionPathProvider<Class<?>> createProductionPathProvider() {
        final var readAdapterFactory = SharepointListReadAdapterFactory.INSTANCE;
        return new DefaultProductionPathProvider<>(readAdapterFactory.getProducerRegistry(),
                readAdapterFactory::getDefaultType);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        try {
            final var tableSourceGroup = createSourceGroup(inSpecs);
            final TableSpecConfig<Class<?>> tableSpecConfig = m_tableReader.createTableSpecConfig(tableSourceGroup,
                    m_config);
            if (!m_config.hasTableSpecConfig()) {
                m_config.setTableSpecConfig(tableSpecConfig);
            }
            return new PortObjectSpec[] { tableSpecConfig.getDataTableSpec() };
        } catch (IOException | IllegalStateException e) {
            LOGGER.debug("Computing the output spec failed.", e);
            throw new InvalidSettingsException(e.getMessage(), e);
        }
    }

    @Override
    protected PortObject[] execute(final PortObject[] inStrings, final ExecutionContext exec) throws Exception {
        final var sourceGroup = createSourceGroup(inStrings);
        return new PortObject[] { m_tableReader.readTable(sourceGroup, m_config, exec) };
    }

    static SourceGroup<SharepointListAccessor> createSourceGroup(final PortObjectSpec[] inObjects)
            throws IOException, InvalidSettingsException {
        final var credential = ((MicrosoftCredentialPortObjectSpec) inObjects[0]).getMicrosoftCredential();
        return new DefaultSourceGroup<>("igraph_servic_client_source_group",
                Collections.singleton(createGraphClient(credential)));
    }

    static SourceGroup<SharepointListAccessor> createSourceGroup(final PortObject[] inObjects)
            throws IOException, InvalidSettingsException {
        final var credential = ((MicrosoftCredentialPortObject) inObjects[0]).getMicrosoftCredentials();
        return new DefaultSourceGroup<>("igraph_servic_client_source_group",
                Collections.singleton(createGraphClient(credential)));
    }

    private static SourceGroup<SharepointListAccessor> createSourceGroup(final PortInput[] inputs) {
        return new DefaultSourceGroup<>("igraph_servic_client_source_group", Collections.singleton(null));
    }

    private static final SharepointListAccessor createGraphClient(final MicrosoftCredential credential)
            throws IOException, InvalidSettingsException {
        if (credential == null) {
            throw new InvalidSettingsException("Not authenticated!");
        }
        if (!(credential instanceof OAuth2Credential)) {
            throw new UnsupportedOperationException("Unsupported credential type: " + credential.getType());
        }

        final String accessToken = ((OAuth2Credential) credential).getAccessToken().getToken();
        return new SharepointListAccessor(GraphServiceClient.builder()//
                // Psst, we have to use this deprecated interface here
                .authenticationProvider(r -> r.addHeader("Authorization", "Bearer " + accessToken))//
                .buildClient());
    }

    @Override
    public StreamableOperator createStreamableOperator(final PartitionInfo partitionInfo,
            final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new StreamableOperator() {
            @Override
            public void runFinal(final PortInput[] inputs, final PortOutput[] outputs, final ExecutionContext exec)
                    throws Exception {
                final var sourceGroup = createSourceGroup(inputs);
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
