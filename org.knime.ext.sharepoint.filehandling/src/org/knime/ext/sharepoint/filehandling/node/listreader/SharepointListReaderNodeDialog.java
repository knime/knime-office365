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

import java.awt.GridBagLayout;
import java.awt.event.ActionListener;

import javax.json.JsonObject;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.SettingsModelReaderFileChooser;
import org.knime.filehandling.core.node.table.reader.MultiTableReadFactory;
import org.knime.filehandling.core.node.table.reader.ProductionPathProvider;
import org.knime.filehandling.core.node.table.reader.config.DefaultTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.TableReadConfig;
import org.knime.filehandling.core.node.table.reader.dialog.SourceIdentifierColumnPanel;
import org.knime.filehandling.core.node.table.reader.preview.dialog.AbstractTableReaderNodeDialog;
import org.knime.filehandling.core.node.table.reader.preview.dialog.GenericItemAccessor;
import org.knime.filehandling.core.util.GBCBuilder;

/**
 * The dialog for the “SharePoint List Reader” node
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 *
 *
 */
final class SharepointListReaderNodeDialog
        extends AbstractTableReaderNodeDialog<JsonObject, SharepointListReaderConfig, Class<?>> {

    private static final Long ROW_START = Long.valueOf(0);

    private static final Long ROW_END = Long.valueOf(Long.MAX_VALUE);

    private static final Long INIT_LIMIT = Long.valueOf(50);

    private final SharepointListReaderMultiTableReadConfig m_config;

    private final JCheckBox m_limitRowsChecker = new JCheckBox();

    private final JSpinner m_limitRowsSpinner = new JSpinner(
            new SpinnerNumberModel(INIT_LIMIT, ROW_START, ROW_END, INIT_LIMIT));

    private final JCheckBox m_skipRowsChecker = new JCheckBox();

    private final JSpinner m_skipRowsSpinner = new JSpinner(
            new SpinnerNumberModel(INIT_LIMIT, ROW_START, ROW_END, INIT_LIMIT));

    private final SourceIdentifierColumnPanel m_pathColumnPanel = new SourceIdentifierColumnPanel("Path");

    /**
     * Constructor.
     *
     * @param settingsModelFileChooser
     *            the {@link SettingsModelReaderFileChooser}
     * @param config
     *            the {@link DefaultMultiTableReadConfig}
     * @param creationConfig
     *            the {@link NodeCreationConfiguration}
     * @param multiReader
     *            the {@link MultiTableReadFactory}
     * @param productionPathProvider
     *            the {@link ProductionPathProvider}
     */
    SharepointListReaderNodeDialog(final SharepointListReaderMultiTableReadConfig config,
            final NodeCreationConfiguration creationConfig,
            final MultiTableReadFactory<JsonObject, SharepointListReaderConfig, Class<?>> multiReader,
            final ProductionPathProvider<Class<?>> productionPathProvider) {
        super(multiReader, productionPathProvider, true);

        m_config = config;

        registerPreviewChangeListeners();

        createDialogPanels();

        m_limitRowsChecker.addActionListener(e -> controlSpinner(m_limitRowsChecker, m_limitRowsSpinner));
        m_limitRowsChecker.doClick();

        m_skipRowsChecker.addActionListener(e -> controlSpinner(m_skipRowsChecker, m_skipRowsSpinner));
        m_skipRowsChecker.doClick();
    }

    /**
     * Register the listeners for the preview. Only when the file changes
     */
    private void registerPreviewChangeListeners() {

        final ActionListener actionListener = l -> configChanged();

        final ChangeListener changeListener = l -> configChanged();

        final DocumentListener documentListener = new DocumentListener() {

            @Override
            public void removeUpdate(final DocumentEvent e) {
                configChanged();
            }

            @Override
            public void insertUpdate(final DocumentEvent e) {
                configChanged();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                configChanged();
            }
        };

        m_limitRowsChecker.getModel().addActionListener(actionListener);
        m_limitRowsSpinner.getModel().addChangeListener(changeListener);
        m_skipRowsChecker.getModel().addActionListener(actionListener);
        m_skipRowsSpinner.getModel().addChangeListener(changeListener);
        m_pathColumnPanel.addChangeListener(changeListener);
    }

    private void createDialogPanels() {
        addTab("Settings", createSettingsPanel());
        addTab("Limit Rows", createLimitRowsPanel());

    }

    /**
     * Creates a standard setup {@link GBCBuilder}.
     *
     * @return returns a {@link GBCBuilder}
     */
    private static final GBCBuilder createGBCBuilder() {
        return new GBCBuilder().resetPos().fillHorizontal().anchorFirstLineStart();
    }

    /**
     * Creates the {@link JPanel} for the Settings tab.
     *
     * @return the settings panel {@link JPanel}
     */
    private JPanel createSettingsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        GBCBuilder gbc = createGBCBuilder().fillHorizontal().setWeightX(1).anchorPageStart();
        panel.add(createSourcePanel(), gbc.build());
        panel.add(createColHeaderPanel(), gbc.incY().build());
        panel.add(m_pathColumnPanel, gbc.incY().build());
        gbc.setWeightY(1).resetX().widthRemainder().incY().fillBoth();
        // Adding the preview panel
        panel.add(createPreview(), gbc.build());
        return panel;
    }

    /**
     * Creates the source file {@link JPanel}.
     *
     * @return the source file {@link JPanel}
     */
    private JPanel createSourcePanel() {
        final JPanel sourcePanel = new JPanel(new GridBagLayout());
        sourcePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Input location"));
        GBCBuilder gbc = createGBCBuilder().setWeightX(1);
        return sourcePanel;
    }

    /**
     * Creates the column header {@link JPanel}.
     *
     * @return the column header {@link JPanel}
     */
    private JPanel createColHeaderPanel() {
        final JPanel colHeaderPanel = new JPanel(new GridBagLayout());
        GBCBuilder gbc = createGBCBuilder().fillHorizontal();
        colHeaderPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Column Header"));
        colHeaderPanel.add(new JPanel(), gbc.incX().setWeightX(1.0).build());
        return colHeaderPanel;
    }

    /**
     * Creates the row {@link JPanel}.
     *
     * @return the row {@link JPanel}
     */
    private JPanel createLimitRowsPanel() {
        final JPanel limitPanel = new JPanel(new GridBagLayout());
        GBCBuilder gbc = createGBCBuilder().fillHorizontal();
        limitPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Row Settings"));

        limitPanel.add(new JLabel("Limit rows:"), gbc.build());
        limitPanel.add(m_limitRowsChecker, gbc.incX().build());
        limitPanel.add(m_limitRowsSpinner, gbc.incX().build());

        limitPanel.add(new JLabel("Skip rows:"), gbc.resetX().incY().build());
        limitPanel.add(m_skipRowsChecker, gbc.incX().build());
        limitPanel.add(m_skipRowsSpinner, gbc.incX().build());

        limitPanel.add(new JPanel(), gbc.incX().setWeightX(1.0).build());
        gbc.setWeightY(1).resetX().widthRemainder().incY().insetBottom(0).fillBoth();
        limitPanel.add(createPreview(), gbc.build());
        return limitPanel;
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

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        getConfig().saveInDialog(settings);
    }

    @Override
    protected SharepointListReaderMultiTableReadConfig getConfig() throws InvalidSettingsException {
        saveTableReadSettings(m_config.getTableReadConfig());
        saveExampleReaderSettings(m_config.getTableReadConfig().getReaderSpecificConfig());
        m_config.setAppendItemIdentifierColumn(m_pathColumnPanel.isAppendSourceIdentifierColumn());
        m_config.setItemIdentifierColumnName(m_pathColumnPanel.getSourceIdentifierColumnName());
        return m_config;
    }

    private void saveExampleReaderSettings(final SharepointListReaderConfig config) {
    }

    @Override
    protected SharepointListReaderMultiTableReadConfig loadSettings(final NodeSettingsRO settings,
            final PortObjectSpec[] specs) throws NotConfigurableException {
        m_config.loadInDialog(settings, specs);

        final SharepointListReaderConfig exampleReaderConfig = m_config.getReaderSpecificConfig();

        final TableReadConfig<SharepointListReaderConfig> exampleCSVReaderConfig = m_config.getTableReadConfig();
        m_limitRowsChecker.setSelected(exampleCSVReaderConfig.limitRows());
        m_limitRowsSpinner.setValue(exampleCSVReaderConfig.getMaxRows());
        m_skipRowsChecker.setSelected(exampleCSVReaderConfig.skipRows());
        m_skipRowsSpinner.setValue(exampleCSVReaderConfig.getNumRowsToSkip());

        m_pathColumnPanel.load(m_config.appendItemIdentifierColumn(), m_config.getItemIdentifierColumnName());

        controlSpinner(m_limitRowsChecker, m_limitRowsSpinner);
        controlSpinner(m_skipRowsChecker, m_skipRowsSpinner);

        return m_config;
    }

    /**
     * Saves the {@link DefaultTableReadConfig}. We do not use the column header
     *
     * @param config
     *            the {@link DefaultTableReadConfig}
     */
    private void saveTableReadSettings(final DefaultTableReadConfig<SharepointListReaderConfig> config) {
        config.setUseColumnHeaderIdx(false);
        config.setLimitRows(m_limitRowsChecker.isSelected());
        config.setMaxRows((long) m_limitRowsSpinner.getValue());
        config.setSkipRows(m_skipRowsChecker.isSelected());
        config.setNumRowsToSkip((long) m_skipRowsSpinner.getValue());
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected GenericItemAccessor<JsonObject> createItemAccessor() {
        // TODO Auto-generated method stub
        return null;
    }
}
