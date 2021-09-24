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
 *   Jun 12, 2020 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader;

import static org.knime.ext.sharepoint.filehandling.node.listreader.mapping.DataTypeProducerRegistry.PATH_SERIALIZER;
import static org.knime.filehandling.core.util.SettingsUtils.getOrEmpty;

import org.knime.core.data.DataType;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.filehandling.core.node.table.ConfigSerializer;
import org.knime.filehandling.core.node.table.reader.config.DefaultTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.TableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.tablespec.ConfigID;
import org.knime.filehandling.core.node.table.reader.config.tablespec.ConfigIDFactory;
import org.knime.filehandling.core.node.table.reader.config.tablespec.NodeSettingsConfigID;
import org.knime.filehandling.core.node.table.reader.config.tablespec.NodeSettingsSerializer;
import org.knime.filehandling.core.node.table.reader.config.tablespec.TableSpecConfigSerializer;
import org.knime.filehandling.core.util.SettingsUtils;

/**
 * {@link ConfigSerializer} for CSV reader nodes.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
enum SharepointListReaderConfigSerializer implements ConfigSerializer<SharepointListReaderMultiTableReadConfig>,
    ConfigIDFactory<SharepointListReaderMultiTableReadConfig> {

        /**
         * Singleton instance.
         */
        INSTANCE;

    private static final String CFG_SETTINGS_TAB = "settings";

    private static final String CFG_TABLE_SPEC_CONFIG = "table_spec_config" + SettingsModel.CFGKEY_INTERNAL;

    private final TableSpecConfigSerializer<DataType> m_tableSpecSerializer;

    enum DataTypeSerializer implements NodeSettingsSerializer<DataType> {

            SERIALIZER_INSTANCE;

        private static final String CFG_TYPE = "type";

        @Override
        public void save(final DataType object, final NodeSettingsWO settings) {
            settings.addDataType(CFG_TYPE, object);
        }

        @Override
        public DataType load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return settings.getDataType(CFG_TYPE);
        }

    }

    private SharepointListReaderConfigSerializer() {
        m_tableSpecSerializer =
            TableSpecConfigSerializer.createStartingV43(PATH_SERIALIZER, this, DataTypeSerializer.SERIALIZER_INSTANCE);
    }

    @Override
    public void saveInDialog(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsWO settings)
        throws InvalidSettingsException {
        saveInModel(config, settings);
    }

    @Override
    public void loadInDialog(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings,
        final PortObjectSpec[] specs) throws NotConfigurableException {
        loadSettingsTabInDialog(config, getOrEmpty(settings, CFG_SETTINGS_TAB));
        if (settings.containsKey(CFG_TABLE_SPEC_CONFIG)) {
            try {
                config.setTableSpecConfig(m_tableSpecSerializer.load(settings.getNodeSettings(CFG_TABLE_SPEC_CONFIG)));
            } catch (InvalidSettingsException ex) { // NOSONAR, see below
                /* Can only happen in TableSpecConfig#load, since we checked #NodeSettingsRO#getNodeSettings(String)
                 * before. The framework takes care that #validate is called before load so we can assume that this
                 * exception does not occur.
                 */
            }
        } else {
            config.setTableSpecConfig(null);
        }
    }

    @Override
    public void saveInModel(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsWO settings) {
        if (config.hasTableSpecConfig()) {
            m_tableSpecSerializer.save(config.getTableSpecConfig(), settings.addNodeSettings(CFG_TABLE_SPEC_CONFIG));
        }
        saveSettingsTab(config, SettingsUtils.getOrAdd(settings, CFG_SETTINGS_TAB));
    }

    @Override
    public void loadInModel(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings)
        throws InvalidSettingsException {
        loadSettingsTabInModel(config, settings.getNodeSettings(CFG_SETTINGS_TAB));
        if (settings.containsKey(CFG_TABLE_SPEC_CONFIG)) {
            config.setTableSpecConfig(m_tableSpecSerializer.load(settings.getNodeSettings(CFG_TABLE_SPEC_CONFIG)));
        } else {
            config.setTableSpecConfig(null);
        }
    }

    @Override
    public void validate(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings)
        throws InvalidSettingsException {
        if (settings.containsKey(CFG_TABLE_SPEC_CONFIG)) {
            m_tableSpecSerializer.load(settings.getNodeSettings(CFG_TABLE_SPEC_CONFIG));
        }
        validateSettingsTab(settings.getNodeSettings(CFG_SETTINGS_TAB));
    }

    private static void loadSettingsTabInDialog(final SharepointListReaderMultiTableReadConfig config,
        final NodeSettingsRO settings) {
        final DefaultTableReadConfig<SharepointListReaderConfig> tc = config.getTableReadConfig();
    }

    private static void loadSettingsTabInModel(final SharepointListReaderMultiTableReadConfig config,
        final NodeSettingsRO settings) throws InvalidSettingsException {
        final DefaultTableReadConfig<SharepointListReaderConfig> tc = config.getTableReadConfig();
    }

    private static void saveSettingsTab(final SharepointListReaderMultiTableReadConfig config,
        final NodeSettingsWO settings) {
        final TableReadConfig<?> tc = config.getTableReadConfig();
    }

    static void validateSettingsTab(final NodeSettingsRO settings) throws InvalidSettingsException {
    }

    @Override
    public ConfigID createFromConfig(final SharepointListReaderMultiTableReadConfig config) {
        return new NodeSettingsConfigID(new NodeSettings("table_manipulator"));
    }

    @Override
    public ConfigID createFromSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        return new NodeSettingsConfigID(settings.getNodeSettings("table_manipulator"));
    }
}
