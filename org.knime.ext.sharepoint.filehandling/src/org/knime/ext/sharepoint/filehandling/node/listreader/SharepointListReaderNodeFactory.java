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
 *   28 Jun 2021 (Moditha Hewasinghaget): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader;

import java.util.Optional;
import java.util.function.Consumer;

import javax.json.JsonObject;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.filehandling.core.node.table.reader.GenericAbstractTableReaderNodeFactory;
import org.knime.filehandling.core.node.table.reader.GenericTableReader;
import org.knime.filehandling.core.node.table.reader.MultiTableReadFactory;
import org.knime.filehandling.core.node.table.reader.ProductionPathProvider;
import org.knime.filehandling.core.node.table.reader.ReadAdapterFactory;
import org.knime.filehandling.core.node.table.reader.paths.SourceSettings;
import org.knime.filehandling.core.node.table.reader.preview.dialog.AbstractTableReaderNodeDialog;
import org.knime.filehandling.core.node.table.reader.preview.dialog.GenericItemAccessor;
import org.knime.filehandling.core.node.table.reader.type.hierarchy.TreeTypeHierarchy;
import org.knime.filehandling.core.node.table.reader.type.hierarchy.TypeHierarchy;
import org.knime.filehandling.core.node.table.reader.type.hierarchy.TypeTester;

/**
 * This is the factory class for the “SharePoint List Reader” node
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public class SharepointListReaderNodeFactory
        extends GenericAbstractTableReaderNodeFactory<JsonObject, SharepointListReaderConfig, Class<?>, String> {
    private final static String MS_AUTH_GRP_ID = "Microsoft Authentication";
    // Only have String values in the simple csv reading
    private static final TypeHierarchy<Class<?>, Class<?>> TYPE_HIERARCHY = TreeTypeHierarchy
            .builder(createTypeTester(String.class)).build();

    // We can convert everything as a String
    private static TypeTester<Class<?>, Class<?>> createTypeTester(final Class<?> type) {
        return TypeTester.createTypeTester(type, s -> true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        return super.createPortsConfigBuilder().map(b -> {
            b.addFixedInputPortGroup(MS_AUTH_GRP_ID, MicrosoftCredentialPortObject.TYPE);
            return b;
        });
    }

    @Override
    protected ReadAdapterFactory<Class<?>, String> getReadAdapterFactory() {
        return SharepointListReaderReadAdapterFactory.INSTANCE;
    }

    @Override
    protected GenericTableReader<JsonObject, SharepointListReaderConfig, Class<?>, String> createReader() {
        return new SharepointListReader();
    }

    @Override
    protected String extractRowKey(final String value) {
        return value;
    }

    @Override
    protected TypeHierarchy<Class<?>, Class<?>> getTypeHierarchy() {
        return TYPE_HIERARCHY;
    }

    @Override
    protected SharepointListReaderMultiTableReadConfig createConfig(
            final NodeCreationConfiguration nodeCreationConfig) {
        return new SharepointListReaderMultiTableReadConfig();
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractTableReaderNodeDialog<JsonObject, SharepointListReaderConfig, Class<?>> createNodeDialogPane(
            final NodeCreationConfiguration creationConfig,
            final MultiTableReadFactory<JsonObject, SharepointListReaderConfig, Class<?>> readFactory,
            final ProductionPathProvider<Class<?>> defaultProductionPathFn) {
        return new SharepointListReaderNodeDialog(new SharepointListReaderMultiTableReadConfig(), creationConfig,
                readFactory,
                defaultProductionPathFn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SourceSettings<JsonObject> createPathSettings(final NodeCreationConfiguration nodeCreationConfig) {
        return new SourceSettings<JsonObject>() {

            @Override
            public String getSourceIdentifier() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void configureInModel(final PortObjectSpec[] specs,
                    final Consumer<StatusMessage> statusMessageConsumer) throws InvalidSettingsException {
                // TODO Auto-generated method stub

            }

            @Override
            public GenericItemAccessor<JsonObject> createItemAccessor() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void saveSettingsTo(final NodeSettingsWO settings) {
                // TODO Auto-generated method stub

            }

            @Override
            public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
                // TODO Auto-generated method stub

            }

            @Override
            public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
                // TODO Auto-generated method stub

            }
        };
    }
}
