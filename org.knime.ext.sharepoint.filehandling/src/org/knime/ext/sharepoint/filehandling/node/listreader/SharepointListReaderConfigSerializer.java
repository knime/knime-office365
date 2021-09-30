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

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.sharepoint.filehandling.node.SiteSettings;
import org.knime.filehandling.core.node.table.ConfigSerializer;
import org.knime.filehandling.core.node.table.reader.config.tablespec.ConfigID;
import org.knime.filehandling.core.node.table.reader.config.tablespec.ConfigIDFactory;
import org.knime.filehandling.core.node.table.reader.config.tablespec.NodeSettingsConfigID;

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

    private static final String CFG_SETTINGS_ID = "sharepoint_list_reader";

    private static final String CFG_SETTINGS_TAB = "settings";

    @Override
    public ConfigID createFromConfig(final SharepointListReaderMultiTableReadConfig config) {
        final NodeSettings settings = new NodeSettings(CFG_SETTINGS_ID);
        // TODO create NodeSettings for each tab

        return new NodeSettingsConfigID(settings);
    }

    private static void saveConfigIDSettingsTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
    }

    @Override
    public ConfigID createFromSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        return new NodeSettingsConfigID(settings.getNodeSettings(CFG_SETTINGS_ID));
    }

    @Override
    public void loadInDialog(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings,
            final PortObjectSpec[] specs) throws NotConfigurableException {
        loadSettingsTabInDialog(config, settings);
    }

    private static void loadSettingsTabInDialog(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) {
        final var siteSettings = config.getReaderSpecificConfig().getSiteSettings();
        try {
            siteSettings.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex) { // NOSONAR: just load the defaults
            config.getReaderSpecificConfig().setSiteSettings(new SiteSettings());
        }

    }

    @Override
    public void loadInModel(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings)
            throws InvalidSettingsException {
        loadSettingsTabInModel(config, settings);
    }

    private static void loadSettingsTabInModel(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsRO settings) throws InvalidSettingsException {
        final var siteSetting = config.getReaderSpecificConfig().getSiteSettings();
        siteSetting.loadSettingsFrom(settings);

    }

    @Override
    public void saveInModel(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsWO settings) {
        saveSettingsTab(config, settings);
    }

    private static void saveSettingsTab(final SharepointListReaderMultiTableReadConfig config,
            final NodeSettingsWO settings) {
        final var siteSetting = config.getReaderSpecificConfig().getSiteSettings();
        siteSetting.saveSettingsTo(settings);
    }

    @Override
    public void saveInDialog(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsWO settings)
            throws InvalidSettingsException {
        saveInModel(config, settings);
    }

    @Override
    public void validate(final SharepointListReaderMultiTableReadConfig config, final NodeSettingsRO settings)
            throws InvalidSettingsException {
        final var siteSetting = config.getReaderSpecificConfig().getSiteSettings();
        siteSetting.validate();
    }

    public static void validateSettingsTab(final NodeSettingsRO settings) throws InvalidSettingsException {
        // Example
        // settings.getBoolean(CFG_SITE_SETTINGS);
    }

}
