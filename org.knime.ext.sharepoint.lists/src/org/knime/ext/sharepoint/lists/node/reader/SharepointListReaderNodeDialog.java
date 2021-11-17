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
package org.knime.ext.sharepoint.lists.node.reader;

import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.sharepoint.dialog.TimeoutPanel;
import org.knime.ext.sharepoint.lists.SharepointListSettingsPanel;
import org.knime.ext.sharepoint.lists.SharepointListSettingsPanel.ListSettings;
import org.knime.ext.sharepoint.lists.node.reader.framework.SharepointListClient;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
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
 * “SharePoint List Reader” implementation of a {@link NodeDialogPane}.</br>
 * It takes care of creating and managing the table preview.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public class SharepointListReaderNodeDialog extends DataAwareNodeDialogPane {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListReaderNodeDialog.class);

    private final TableReaderPreviewTransformationCoordinator<SharepointListClient, SharepointListReaderConfig, DataType> m_coordinator;

    private final List<TableReaderPreviewView> m_previews = new ArrayList<>();

    private final TableReaderPreviewModel m_previewModel;

    private final TableTransformationPanel m_specTransformer;

    private final boolean m_disableIOComponents;

    private boolean m_ignoreEvents = false;

    private final SharepointListSettingsPanel m_listSettingsPanel;

    private final SharepointListReaderMultiTableReadConfig m_config;

    private final SharepointListClientAccessor m_itemClient = new SharepointListClientAccessor();

    // advanced tab
    private final TimeoutPanel<ListSettings, SharepointListSettings> m_timeoutPanel;

    // limit rows tab
    private final JCheckBox m_skipRowsEnabled;
    private final JSpinner m_skipRowsNumber;
    private final JCheckBox m_limitRowsEnabled;
    private final JSpinner m_limitRowsNumber;

    SharepointListReaderNodeDialog() {
        m_config = SharepointListReaderNodeModel.createConfig();
        final var readFactory = SharepointListReaderNodeModel.createReadFactory();
        final var productionPathProvider = SharepointListReaderNodeModel.createProductionPathProvider();
        final var analysisComponentModel = new AnalysisComponentModel();
        m_previewModel = new TableReaderPreviewModel(analysisComponentModel);
        final var transformationModel = new TableTransformationTableModel<>(productionPathProvider);
        m_coordinator = new TableReaderPreviewTransformationCoordinator<>(readFactory, transformationModel,
                analysisComponentModel, m_previewModel, this::getConfig, this::getItemClient, false);
        m_specTransformer = new TableTransformationPanel(transformationModel, false, false);
        m_disableIOComponents = CheckNodeContextUtil.isRemoteWorkflowContext();

        m_listSettingsPanel = new SharepointListSettingsPanel(
                m_config.getReaderSpecificConfig().getSharepointListSettings());

        m_timeoutPanel = new TimeoutPanel<>(m_config.getReaderSpecificConfig().getSharepointListSettings());

        final var stepSize = Long.valueOf(1);
        final var rowStart = Long.valueOf(0);
        final var rowEnd = Long.valueOf(Long.MAX_VALUE);
        final var skipOne = Long.valueOf(1);
        final var initLimit = Long.valueOf(50);

        m_skipRowsEnabled = new JCheckBox("Skip first data rows");
        m_skipRowsNumber = new JSpinner(new SpinnerNumberModel(skipOne, rowStart, rowEnd, stepSize));
        m_skipRowsEnabled.addActionListener(e -> controlSpinner(m_skipRowsEnabled, m_skipRowsNumber));
        m_skipRowsEnabled.doClick();

        m_limitRowsEnabled = new JCheckBox("Limit data rows");
        m_limitRowsNumber = new JSpinner(new SpinnerNumberModel(initLimit, rowStart, rowEnd, initLimit));
        m_limitRowsEnabled.addActionListener(e -> controlSpinner(m_limitRowsEnabled, m_limitRowsNumber));
        m_limitRowsEnabled.doClick();

        registerPreviewListeners();

        addTab("Settings", createSettingsPanel());
        addTab("Transformation", createTransformationTab());
        addTab("Advanced", createAdvancedTab());
        addTab("Limit Rows", createLimitRowsTab());
    }

    private void registerPreviewListeners() {
        final ActionListener action = a -> configChanged();
        final ChangeListener change = c -> configChanged();

        m_timeoutPanel.addChangeListener(change);
        m_listSettingsPanel.addListener(change);

        m_skipRowsEnabled.addActionListener(action);
        m_skipRowsNumber.addChangeListener(change);
        m_limitRowsEnabled.addActionListener(action);
        m_limitRowsNumber.addChangeListener(change);
    }

    /**
     * Enables a {@link JSpinner} based on a corresponding {@link JCheckBox}.
     *
     * @param checker
     *            the {@link JCheckBox} which controls if a {@link JSpinner} should
     *            be enabled
     * @param spinner
     *            a {@link JSpinner} controlled by the {@link JCheckBox}
     */
    private static void controlSpinner(final JCheckBox checker, final JSpinner spinner) {
        spinner.setEnabled(checker.isSelected());
    }

    private static Border createBorder(final String title) {
        return BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title);
    }

    GenericItemAccessor<SharepointListClient> getItemClient() {
        return m_itemClient;
    }

    private JPanel createSettingsPanel() {
        final var panel = new JPanel(new GridBagLayout());
        final var gbc = new GBCBuilder().resetPos().weight(1, 0).anchorFirstLineStart().fillHorizontal();
        panel.add(m_listSettingsPanel, gbc.build());

        panel.add(createPreview(), gbc.incY().fillBoth().setWeightY(1).build());
        return panel;
    }

    private JPanel createAdvancedTab() {
        final var panel = new JPanel(new GridBagLayout());

        final var gbc = new GBCBuilder().resetPos().weight(1, 0).insets(5, 0, 5, 5).anchorFirstLineStart()
                .fillHorizontal();
        panel.add(m_timeoutPanel, gbc.build());

        panel.add(createPreview(), gbc.incY().fillBoth().setWeightY(1).build());
        return panel;

    }

    private JPanel createLimitRowsTab() {
        final var panel = new JPanel(new GridBagLayout());

        final var specLimitPanel = new JPanel(new GridBagLayout());
        specLimitPanel.setBorder(createBorder("Limit rows"));
        final var gbc = new GBCBuilder().resetPos().weight(0, 0).insets(5, 0, 5, 5).anchorFirstLineStart().fillNone();
        specLimitPanel.add(m_skipRowsEnabled, gbc.build());
        specLimitPanel.add(m_skipRowsNumber, gbc.incX().build());
        specLimitPanel.add(Box.createVerticalBox(), gbc.incX().setWeightX(1).fillHorizontal().build());
        gbc.fillNone().resetX().setWeightX(0).incY();
        specLimitPanel.add(m_limitRowsEnabled, gbc.build());
        specLimitPanel.add(m_limitRowsNumber, gbc.incX().build());
        specLimitPanel.add(Box.createVerticalBox(), gbc.incX().setWeightX(1).fillHorizontal().build());
        gbc.resetPos().insets(0, 0, 0, 0).weight(1, 0).fillHorizontal().anchorFirstLineStart();
        panel.add(specLimitPanel, gbc.build());

        panel.add(createPreview(), gbc.incY().fillBoth().setWeightY(1).build());
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
        final var tableReadConfig = m_config.getTableReadConfig();

        tableReadConfig.setSkipRows(m_skipRowsEnabled.isSelected());
        tableReadConfig.setNumRowsToSkip((Long) m_skipRowsNumber.getValue());
        tableReadConfig.setLimitRows(m_limitRowsEnabled.isSelected());
        tableReadConfig.setMaxRows((Long) m_limitRowsNumber.getValue());
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
            final var client = SharepointListReaderNodeModel
                    .createSourceGroup(input, m_config.getReaderSpecificConfig().getSharepointListSettings()).iterator()
                    .next();
            loadSettings(settings, new PortObjectSpec[] { input[0].getSpec() }, client);
        } catch (InvalidSettingsException e) {
            throw new NotConfigurableException(e.getMessage(), e);
        } catch (IOException e) {
            loadSettings(settings, new PortObjectSpec[] { input[0].getSpec() }, null);
        }
    }

    private void loadSettings(final NodeSettingsRO settings, final PortObjectSpec[] specs,
            final SharepointListClient input) throws NotConfigurableException {
        ignoreEvents(true);
        setPreviewEnabled(false);

        if (specs[0] == null || ((MicrosoftCredentialPortObjectSpec) specs[0]).getMicrosoftCredential() == null) {
            throw new NotConfigurableException("Authentication required!");
        }

        final var connection = ((MicrosoftCredentialPortObjectSpec) specs[0]).getMicrosoftCredential();

        m_config.loadInDialog(settings, specs);
        if (m_config.hasTableSpecConfig()) {
            m_coordinator.load(m_config.getTableSpecConfig().getTableTransformation());
        }

        m_itemClient.setClient(input);

        loadTableReadSettings();
        // enable/disable spinners
        controlSpinner(m_skipRowsEnabled, m_skipRowsNumber);
        controlSpinner(m_limitRowsEnabled, m_limitRowsNumber);
        m_listSettingsPanel.settingsLoaded(connection);

        ignoreEvents(false);
        refreshPreview(true);
    }

    /**
     * Fill in the setting values in {@link TableReadConfig} using values from
     * dialog.
     */
    private void loadTableReadSettings() {
        final var tableReadConfig = m_config.getTableReadConfig();

        m_skipRowsEnabled.setSelected(tableReadConfig.skipRows());
        m_skipRowsNumber.setValue(tableReadConfig.getNumRowsToSkip());
        m_limitRowsEnabled.setSelected(tableReadConfig.limitRows());
        m_limitRowsNumber.setValue(tableReadConfig.getMaxRows());
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
        m_listSettingsPanel.onClose();
        m_coordinator.onClose();
        m_specTransformer.onClose();
        super.onClose();
    }

    @Override
    public void onOpen() {
        m_listSettingsPanel.onOpen();
        super.onOpen();
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

    private static final class SharepointListClientAccessor implements GenericItemAccessor<SharepointListClient> {

        private List<SharepointListClient> m_clients;

        private SharepointListClientAccessor() {
            m_clients = Collections.emptyList();
        }

        private void setClient(final SharepointListClient client) {
            if (client == null) {
                m_clients = Collections.emptyList();
            } else {
                m_clients = Collections.singletonList(client);
            }
        }

        @Override
        public void close() throws IOException {
            // nothing to close
        }

        @Override
        public List<SharepointListClient> getItems(final Consumer<StatusMessage> statusMessageConsumer)
                throws IOException, InvalidSettingsException {
            return m_clients;
        }

        @Override
        public SharepointListClient getRootItem(final Consumer<StatusMessage> statusMessageConsumer)
                throws IOException, InvalidSettingsException {
            if (m_clients.isEmpty()) {
                throw new InvalidSettingsException(
                        "No access token found. Please re-execute the Microsoft Authentication node.");
            } else {
                return m_clients.get(0);
            }
        }
    }
}
