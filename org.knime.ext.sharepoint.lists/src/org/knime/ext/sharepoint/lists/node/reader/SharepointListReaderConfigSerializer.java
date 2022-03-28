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
package org.knime.ext.sharepoint.lists.node.reader;

import org.knime.core.data.DataType;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.ext.sharepoint.lists.node.SharepointListSettings;
import org.knime.ext.sharepoint.lists.node.reader.mapping.SharepointListReadAdapterFactory;
import org.knime.filehandling.core.node.table.ConfigSerializer;
import org.knime.filehandling.core.node.table.reader.config.tablespec.ConfigID;
import org.knime.filehandling.core.node.table.reader.config.tablespec.ConfigIDFactory;
import org.knime.filehandling.core.node.table.reader.config.tablespec.DefaultProductionPathSerializer;
import org.knime.filehandling.core.node.table.reader.config.tablespec.NodeSettingsConfigID;
import org.knime.filehandling.core.node.table.reader.config.tablespec.NodeSettingsSerializer;
import org.knime.filehandling.core.node.table.reader.config.tablespec.TableSpecConfigSerializer;
import org.knime.filehandling.core.util.SettingsUtils;

/**
 * {@link ConfigSerializer} for “SharePoint List Reader” nodes.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
enum SharepointListReaderConfigSerializer implements ConfigSerializer<SharepointListReaderMultiTableReadConfig>,
        ConfigIDFactory<SharepointListReaderMultiTableReadConfig> {

    /**
     * Singleton instance.
     */
    INSTANCE;

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListReaderConfigSerializer.class);

    private static final String CFG_SETTINGS_ID = "sharepoint_list_reader";

    private static final String CFG_SETTINGS_TAB = "settings";

    private static final String CFG_TABLE_SPEC_CONFIG = "table_spec_config" + SettingsModel.CFGKEY_INTERNAL;

    private static final String SUB_CFG_ADVANCED = "advanced";

    private static final String CFG_TIMEOUT = "timeout";

    private static final String SUB_CFG_LIMIT_ROWS = "limit_rows";

    private static final String CFG_SKIP_ROWS = "skip_data_rows";

    private static final String CFG_SKIP_ROW_NUM = "number_of_rows_to_skip";

    private static final String CFG_LIMIT_ROWS = "limit_data_rows";

    private static final String CFG_LIMIT_ROW_NUM = "max_data_rows";

    private final TableSpecConfigSerializer<DataType> m_tableSpecSerializer;

    public enum DataTypeSerializer implements NodeSettingsSerializer<DataType> {

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
        m_tableSpecSerializer = TableSpecConfigSerializer.createStartingV43(
                new DefaultProductionPathSerializer(SharepointListReadAdapterFactory.INSTANCE.getProducerRegistry()),
                this, DataTypeSerializer.SERIALIZER_INSTANCE);
    }

    @Override
    public ConfigID createFromConfig(final SharepointListReaderMultiTableReadConfig config) {
        final var settings = new NodeSettings(CFG_SETTINGS_ID);
        saveConfigIDSettingsTab(config, settings.addNodeSettings(CFG_SETTINGS_TAB));
        saveConfigIDAdvancedTab(config, settings.addNodeSettings(SUB_CFG_ADVANCED));
        saveConfigIDLimitRowsTab(config, settings.addNodeSettings(SUB_CFG_LIMIT_ROWS));

        return new NodeSettingsConfigID(settings);
    }

    private static void saveConfigIDSettingsTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        sls.getSiteSettings().saveSettingsTo(settings);
        sls.getListSettings().saveSettingsTo(settings);
    }

    private static void saveConfigIDAdvancedTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        final NodeSettingsWO timeout = settings.addNodeSettings(CFG_TIMEOUT);
        sls.getTimeoutSettings().saveSettingsTo(timeout);
    }

    private static void saveConfigIDLimitRowsTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
        final var trc = config.getTableReadConfig();
        settings.addBoolean(CFG_SKIP_ROWS, trc.skipRows());
        settings.addLong(CFG_SKIP_ROW_NUM, trc.getNumRowsToSkip());
        settings.addBoolean(CFG_LIMIT_ROWS, trc.limitRows());
        settings.addLong(CFG_LIMIT_ROW_NUM, trc.getMaxRows());
    }

    @Override
    public ConfigID createFromSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        return new NodeSettingsConfigID(settings.getNodeSettings(CFG_SETTINGS_ID));
    }

    @Override
    public void loadInDialog(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings,
            final PortObjectSpec[] specs) throws NotConfigurableException {
        loadSettingsTabInDialog(config, SettingsUtils.getOrEmpty(settings, CFG_SETTINGS_TAB));
        loadAdvancedTabInDialog(config, SettingsUtils.getOrEmpty(settings, SUB_CFG_ADVANCED));
        loadLimitRowsTabInDialog(config, SettingsUtils.getOrEmpty(settings, SUB_CFG_LIMIT_ROWS));
        if (settings.containsKey(CFG_TABLE_SPEC_CONFIG)) {
            try {
                config.setTableSpecConfig(m_tableSpecSerializer.load(settings.getNodeSettings(CFG_TABLE_SPEC_CONFIG)));
            } catch (InvalidSettingsException ex) { // NOSONAR, see below
                /*
                 * Can only happen in TableSpecConfig#load, since we checked
                 * #NodeSettingsRO#getNodeSettings(String) before. The framework takes care that
                 * #validate is called before load so we can assume that this exception does not
                 * occur.
                 */
            }
        } else {
            config.setTableSpecConfig(null);
        }
    }

    private static void loadSettingsTabInDialog(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        try {
            sls.getSiteSettings().loadSettingsFrom(settings);
            sls.getListSettings().loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex) {
            LOGGER.debug("Unexpected InvalidSettingsException occured", ex);
        }
    }

    private static void loadAdvancedTabInDialog(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        try {
            sls.getTimeoutSettings().loadSettingsFrom(settings.getNodeSettings(CFG_TIMEOUT));
        } catch (InvalidSettingsException ex) {
            LOGGER.debug("Unexpected InvalidSettingsException occured", ex);
        }
    }

    private static void loadLimitRowsTabInDialog(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) {
        final var trc = config.getTableReadConfig();
        trc.setSkipRows(settings.getBoolean(CFG_SKIP_ROWS, false));
        trc.setNumRowsToSkip(settings.getLong(CFG_SKIP_ROW_NUM, 1L));
        trc.setLimitRows(settings.getBoolean(CFG_LIMIT_ROWS, false));
        trc.setMaxRows(settings.getLong(CFG_LIMIT_ROW_NUM, 50L));
    }

    @Override
    public void loadInModel(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings)
            throws InvalidSettingsException {
        loadSettingsTabInModel(config, settings.getNodeSettings(CFG_SETTINGS_TAB));
        loadAdvancedTabInModel(config, settings.getNodeSettings(SUB_CFG_ADVANCED));
        loadLimitRowsTabInModel(config, settings.getNodeSettings(SUB_CFG_LIMIT_ROWS));
        if (settings.containsKey(CFG_TABLE_SPEC_CONFIG)) {
            config.setTableSpecConfig(m_tableSpecSerializer.load(settings.getNodeSettings(CFG_TABLE_SPEC_CONFIG)));
        } else {
            config.setTableSpecConfig(null);
        }
    }

    private static void loadSettingsTabInModel(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        sls.getSiteSettings().loadSettingsFrom(settings);
        sls.getListSettings().loadSettingsFrom(settings);
    }

    private static void loadAdvancedTabInModel(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        sls.getTimeoutSettings().loadSettingsFrom(settings.getNodeSettings(CFG_TIMEOUT));
    }

    private static void loadLimitRowsTabInModel(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        final var trc = config.getTableReadConfig();
        trc.setSkipRows(settings.getBoolean(CFG_SKIP_ROWS));
        trc.setNumRowsToSkip(settings.getLong(CFG_SKIP_ROW_NUM));
        trc.setLimitRows(settings.getBoolean(CFG_LIMIT_ROWS));
        trc.setMaxRows(settings.getLong(CFG_LIMIT_ROW_NUM));
    }

    @Override
    public void saveInModel(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsWO settings) {
        saveSettingsTab(config, SettingsUtils.getOrAdd(settings, CFG_SETTINGS_TAB));
        saveAdvancedTab(config, SettingsUtils.getOrAdd(settings, SUB_CFG_ADVANCED));
        saveLimitRowsTab(config, settings.addNodeSettings(SUB_CFG_LIMIT_ROWS));
        if (config.hasTableSpecConfig()) {
            m_tableSpecSerializer.save(config.getTableSpecConfig(), settings.addNodeSettings(CFG_TABLE_SPEC_CONFIG));
        }
    }

    private static void saveSettingsTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        sls.getSiteSettings().saveSettingsTo(settings);
        sls.getListSettings().saveSettingsTo(settings);
    }

    private static void saveAdvancedTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
        final var sls = config.getReaderSpecificConfig().getSharepointListSettings();
        final NodeSettingsWO timeout = settings.addNodeSettings(CFG_TIMEOUT);
        sls.getTimeoutSettings().saveSettingsTo(timeout);
    }

    private static void saveLimitRowsTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
        final var trc = config.getTableReadConfig();
        settings.addBoolean(CFG_SKIP_ROWS, trc.skipRows());
        settings.addLong(CFG_SKIP_ROW_NUM, trc.getNumRowsToSkip());
        settings.addBoolean(CFG_LIMIT_ROWS, trc.limitRows());
        settings.addLong(CFG_LIMIT_ROW_NUM, trc.getMaxRows());

    }

    @Override
    public void saveInDialog(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsWO settings)
            throws InvalidSettingsException {
        saveInModel(config, settings);
    }

    @Override
    public void validate(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings)
            throws InvalidSettingsException {
        validateSettingsTab(settings.getNodeSettings(CFG_SETTINGS_TAB));
        validateAdvancedTab(settings.getNodeSettings(SUB_CFG_ADVANCED));
        validateLimitRowsTab(settings.getNodeSettings(SUB_CFG_LIMIT_ROWS));
        if (settings.containsKey(CFG_TABLE_SPEC_CONFIG)) {
            m_tableSpecSerializer.load(settings.getNodeSettings(CFG_TABLE_SPEC_CONFIG));
        }
    }

    private static void validateSettingsTab(final NodeSettingsRO settings) throws InvalidSettingsException {
        final var sls = new SharepointListSettings();
        sls.getSiteSettings().validateSettings(settings);
        sls.getListSettings().validateSettings(settings);
    }

    private static void validateAdvancedTab(final NodeSettingsRO settings) throws InvalidSettingsException {
        final var sls = new SharepointListSettings();
        sls.getTimeoutSettings().validateSettings(settings.getNodeSettings(CFG_TIMEOUT));
    }

    public static void validateLimitRowsTab(final NodeSettingsRO settings) throws InvalidSettingsException {
        settings.getBoolean(CFG_SKIP_ROWS);
        CheckUtils.checkSetting(settings.getLong(CFG_SKIP_ROW_NUM) >= 0,
                "Number of lines to skip must be non-negative!");
        settings.getBoolean(CFG_LIMIT_ROWS);
        CheckUtils.checkSetting(settings.getLong(CFG_LIMIT_ROW_NUM) >= 0,
                "Number of lines to read must be non-negative!");
    }

}
