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
 *   Aug 14, 2020 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader;

import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.event.ChangeEvent;

import org.knime.core.data.DataType;
import org.knime.core.node.DataAwareNodeDialogPane;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnection;
import org.knime.ext.sharepoint.filehandling.node.SiteSettingsPanel;
import org.knime.ext.sharepoint.filehandling.node.listreader.framework.SharepointListAccessor;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.filehandling.core.node.table.reader.config.DefaultTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.MultiTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.TableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.tablespec.DefaultTableSpecConfig;
import org.knime.filehandling.core.node.table.reader.config.tablespec.TableSpecConfig;
import org.knime.filehandling.core.node.table.reader.preview.dialog.AnalysisComponentModel;
import org.knime.filehandling.core.node.table.reader.preview.dialog.GenericItemAccessor;
import org.knime.filehandling.core.node.table.reader.preview.dialog.TableReaderPreviewModel;
import org.knime.filehandling.core.node.table.reader.preview.dialog.TableReaderPreviewTransformationCoordinator;
import org.knime.filehandling.core.node.table.reader.preview.dialog.TableReaderPreviewView;
import org.knime.filehandling.core.node.table.reader.preview.dialog.transformer.TableTransformationPanel;
import org.knime.filehandling.core.node.table.reader.preview.dialog.transformer.TableTransformationTableModel;
import org.knime.filehandling.core.util.CheckNodeContextUtil;
import org.knime.filehandling.core.util.GBCBuilder;

/**
 * Table manipulator implementation of a {@link NodeDialogPane}.</br>
 * It takes care of creating and managing the table preview.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public class SharepointListReaderNodeDialog extends DataAwareNodeDialogPane {
    private static final class SharepointListAccessorAccessor implements GenericItemAccessor<SharepointListAccessor> {

        private List<SharepointListAccessor> m_clients;

        private SharepointListAccessorAccessor(final List<SharepointListAccessor> clients) {
            m_clients = clients;
        }

        private void setTables(final List<SharepointListAccessor> client) {
            m_clients = client;
        }

        @Override
        public void close() throws IOException {
            // nothing to close
        }

        @Override
        public List<SharepointListAccessor> getItems(final Consumer<StatusMessage> statusMessageConsumer)
                throws IOException, InvalidSettingsException {
            return m_clients;
        }

        @Override
        public SharepointListAccessor getRootItem(final Consumer<StatusMessage> statusMessageConsumer)
                throws IOException, InvalidSettingsException {
            return m_clients.get(0);
        }
    }

    private final TableReaderPreviewTransformationCoordinator<SharepointListAccessor, SharepointListReaderConfig, DataType> m_coordinator;

    private final List<TableReaderPreviewView> m_previews = new ArrayList<>();

    // TODO extract common stuff
    private final SharepointListsSettings m_sharePointConnectionSettings = new SharepointListsSettings();

    private MicrosoftCredential m_connection;

    private final SiteSettingsPanel m_listSettingsPanel;

    private final TableReaderPreviewModel m_previewModel;

    private final TableTransformationPanel m_specTransformer;

    private final boolean m_disableIOComponents;

    private boolean m_ignoreEvents = false;

    private final SharepointListReaderMultiTableReadConfig m_config;

    private final SharepointListAccessorAccessor m_itemAccessor = new SharepointListAccessorAccessor(
            Collections.<SharepointListAccessor>emptyList());

    SharepointListReaderNodeDialog() {
        m_config = SharepointListReaderNodeModel.createConfig();
        final var readFactory = SharepointListReaderNodeModel.createReadFactory();
        final var productionPathProvider = SharepointListReaderNodeModel.createProductionPathProvider();
        final var analysisComponentModel = new AnalysisComponentModel();
        m_listSettingsPanel = new SiteSettingsPanel(m_config.getReaderSpecificConfig().getSiteSettings(),
                this::createFSConnection);
        m_previewModel = new TableReaderPreviewModel(analysisComponentModel);
        final var transformationModel = new TableTransformationTableModel<>(productionPathProvider);
        m_coordinator = new TableReaderPreviewTransformationCoordinator<>(readFactory, transformationModel,
                analysisComponentModel, m_previewModel, this::getConfig, this::getItemAccessor, true);
        m_specTransformer = new TableTransformationPanel(transformationModel, false, false);
        m_disableIOComponents = CheckNodeContextUtil.isRemoteWorkflowContext();
        addTab("Settings", createSettingsPanel());
    }

    private FSConnection createFSConnection() throws IOException {
        final var clonedSettings = m_sharePointConnectionSettings.clone();

        final String accessToken = ((OAuth2Credential) m_connection).getAccessToken().getToken();

        return new SharepointFSConnection(
                clonedSettings.toFSConnectionConfig(a -> a.addHeader("Authorization", "Bearer " + accessToken)));
    }

    GenericItemAccessor<SharepointListAccessor> getItemAccessor() {
        return m_itemAccessor;
    }

    private JPanel createSettingsPanel() {
        final var panel = new JPanel(new GridBagLayout());
        final var gbc = new GBCBuilder().resetPos().anchorFirstLineStart().fillHorizontal().setWeightX(1.0);
        panel.add(m_listSettingsPanel, gbc.fillBoth().build());
        panel.add(createTransformationTab(), gbc.fillBoth().setWeightY(1.0).incY().build());
        return panel;
    }

    /**
     * Enables/disables all previews created with {@link #createPreview()}.
     *
     * @param enabled
     *            {@code true} if enabled, {@code false} otherwise
     */
    protected final void setPreviewEnabled(final boolean enabled) {
        for (TableReaderPreviewView preview : m_previews) {
            preview.setEnabled(enabled);
        }
    }

    /**
     * Creates a {@link TableReaderPreviewView} that is synchronized with all other
     * previews created by this method. This means that scrolling in one preview
     * will scroll to the same position in all other previews.
     *
     * @return a {@link TableReaderPreviewView}
     */
    protected final TableReaderPreviewView createPreview() {
        final var preview = new TableReaderPreviewView(m_previewModel);
        m_previews.add(preview);
        preview.addScrollListener(this::updateScrolling);
        return preview;
    }

    /**
     * Convenience method that creates a {@link JSplitPane} containing the
     * {@link TableTransformationPanel} and a {@link TableReaderPreviewView}. NOTE:
     * If this method is called multiple times, then the
     * {@link TableTransformationPanel} will only be shown in the {@link JSplitPane}
     * created by the latest call.
     *
     * @return a {@link JSplitPane} containing the {@link TableTransformationPanel}
     *         and a {@link TableReaderPreviewView}
     */
    protected final JSplitPane createTransformationTab() {
        final var splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setLeftComponent(getTransformationPanel());
        splitPane.setRightComponent(createPreview());
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerSize(15);
        return splitPane;
    }

    private void updateScrolling(final ChangeEvent changeEvent) {
        final var updatedView = (TableReaderPreviewView) changeEvent.getSource();
        for (final var preview : m_previews) {
            if (preview != updatedView) {
                preview.updateViewport(updatedView);
            }
        }
    }

    /**
     * Returns the {@link TableTransformationPanel} that allows to alter the table
     * structure.
     *
     * @return the {@link TableTransformationPanel}
     */
    protected final TableTransformationPanel getTransformationPanel() {
        return m_specTransformer;
    }

    /**
     * Should be called by inheriting classes whenever the config changed i.e. if
     * the user interacts with the dialog.
     */
    protected final void configChanged() {
        if (areIOComponentsDisabled()) {
            m_coordinator.setDisabledInRemoteJobViewInfo();
        } else if (!areEventsIgnored()) {
            m_coordinator.configChanged();
        }
    }

    /**
     * Sets whether this dialog should react to calls of {@link #configChanged()}.
     * Call when loading the settings to avoid unnecessary I/O due to many calls to
     * {@link #configChanged()}.
     *
     * @param ignoreEvents
     *            whether events should be ignored or not
     */
    protected final void ignoreEvents(final boolean ignoreEvents) {
        m_ignoreEvents = ignoreEvents;
    }

    /**
     * Indicates whether events should be ignored. If this returns {@code true},
     * calls to {@link #configChanged()} won't have any effect.
     *
     * @return {@code true} if {@link #configChanged()} doesn't react to calls
     */
    protected final boolean areEventsIgnored() {
        return m_ignoreEvents;
    }

    /**
     * Indicates whether the components doing IO, such as the preview, are disabled
     * (by default when opened in remote job view).
     *
     * @return if IO components are disabled
     */
    protected boolean areIOComponentsDisabled() {
        return m_disableIOComponents;
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_specTransformer.commitChanges();
        saveConfig();
        m_config.saveInModel(settings);
    }

    /**
     * Save the current config.
     *
     * @throws InvalidSettingsException
     *             if a setting could not be saved
     */
    protected void saveConfig() throws InvalidSettingsException {
        saveTableReadSettings();
        m_config.setTableSpecConfig(getTableSpecConfig());
    }

    /**
     * Fill in the setting values in {@link TableReadConfig} using values from
     * dialog.
     */
    private void saveTableReadSettings() {
        final DefaultTableReadConfig<SharepointListReaderConfig> tableReadConfig = m_config.getTableReadConfig();

        // TODO: true throws mapping exception because of strange index mapper
        tableReadConfig.setRowIDIdx(-1);

        tableReadConfig.setUseColumnHeaderIdx(false);
        tableReadConfig.setColumnHeaderIdx(0);
    }

    @Override
    protected final void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        loadSettings(settings, specs, null);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObject[] input)
            throws NotConfigurableException {
        try {
            final var client = SharepointListReaderNodeModel.createSourceGroup(input).iterator().next();
            // TODO no array creation?
            loadSettings(settings, new PortObjectSpec[] { input[0].getSpec() }, client);
        } catch (IOException | InvalidSettingsException e) {
            NodeLogger.getLogger(getClass()).error(e);
        }
    }

    private void loadSettings(final NodeSettingsRO settings, final PortObjectSpec[] specs,
            final SharepointListAccessor input) throws NotConfigurableException {
        ignoreEvents(true);
        setPreviewEnabled(false);

        m_connection = ((MicrosoftCredentialPortObjectSpec) specs[0]).getMicrosoftCredential();
        if (m_connection == null) {
            throw new NotConfigurableException("Authentication required");
        }

        m_listSettingsPanel.settingsLoaded(m_connection);

        m_config.loadInDialog(settings, specs);
        if (m_config.hasTableSpecConfig()) {
            m_coordinator.load(m_config.getTableSpecConfig().getTableTransformation());
        }

        m_itemAccessor.setTables(Collections.singletonList(input));

        ignoreEvents(false);
        refreshPreview(true);
    }

    /**
     * Retrieves the currently configured {@link DefaultTableSpecConfig} or
     * {@code null} if none is available e.g. if the current settings are invalid
     * and thus no preview could be loaded.
     *
     * @return the currently configured {@link DefaultTableSpecConfig} or
     *         {@code null} if none is available
     */
    protected final TableSpecConfig<DataType> getTableSpecConfig() {
        return m_coordinator.getTableSpecConfig();
    }

    /**
     * This method must return the current {@link MultiTableReadConfig}. It is used
     * to load the preview, so please make sure that all settings are stored in the
     * config, otherwise the preview will be incorrect.</br>
     * {@link RuntimeException} should be wrapped into
     * {@link InvalidSettingsException} if they indicate an invalid configuration.
     *
     * @return the current configuration
     * @throws InvalidSettingsException
     *             if the settings are invalid
     */
    protected MultiTableReadConfig<SharepointListReaderConfig, DataType> getConfig() throws InvalidSettingsException {
        return saveAndGetConfig();
    }

    private MultiTableReadConfig<SharepointListReaderConfig, DataType> saveAndGetConfig()
            throws InvalidSettingsException {
        try {
            saveConfig();
        } catch (RuntimeException e) {
            throw new InvalidSettingsException(e.getMessage(), e);
        }
        return m_config;
    }

    @Override
    public void onClose() {
        m_coordinator.onClose();
        m_specTransformer.onClose();
        super.onClose();
    }

    /**
     * Enables/disables the preview depending on what
     * {@link #areIOComponentsDisabled()} returns. Refreshes the preview if the
     * passed parameter is {@code true} and the preview enabled.
     *
     * @param refreshPreview
     *            whether the preview should be refreshed
     */
    public void refreshPreview(final boolean refreshPreview) {
        final boolean enabled = !areIOComponentsDisabled();
        setPreviewEnabled(enabled);
        if (enabled && refreshPreview) {
            configChanged();
        }
    }
}
