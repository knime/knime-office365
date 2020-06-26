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
package org.knime.ext.sharepoint.filehandling.node;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.microsoft.authentication.port.MicrosoftConnection;
import org.knime.ext.microsoft.authentication.port.MicrosoftConnectionPortObjectSpec;
import org.knime.ext.sharepoint.filehandling.fs.SharepointConnection;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.base.ui.WorkingDirectoryChooser;

/**
 * Sharepoint Connection node dialog.
 *
 * @author Alexander Bondaletov
 */
public class SharepointConnectionNodeDialog extends NodeDialogPane {

    private final SharepointConnectionSettings m_settings = new SharepointConnectionSettings();

    private final SiteSettingsPanel m_sitePanel = new SiteSettingsPanel(m_settings.getSiteSettings());

    private final WorkingDirectoryChooser m_workingDirChooser = new WorkingDirectoryChooser("sharepoint.workingDir",
            this::createFSConnection);

    private MicrosoftConnection m_connection;

    /**
     * Creates new instance.
     */
    public SharepointConnectionNodeDialog() {
        addTab("Settings", createSettingsPanel());
        addTab("Advanced", createTimeoutsPanel());
    }


    private Box createSettingsPanel() {
        m_workingDirChooser.setBorder(BorderFactory.createTitledBorder("File system settings"));

        Box box = new Box(BoxLayout.PAGE_AXIS);
        box.add(m_sitePanel);
        box.add(m_workingDirChooser);
        return box;
    }


    private FSConnection createFSConnection() throws IOException {
        final SharepointConnectionSettings clonedSettings = m_settings.clone();
        return new SharepointConnection(SharepointConnectionNodeModel.createGraphAuthProvider(m_connection),
                clonedSettings);
    }

    private JComponent createTimeoutsPanel() {
        final DialogComponentNumber connectionTimeout = new DialogComponentNumber(
                m_settings.getConnectionTimeoutModel(),
                "",
                1);
        connectionTimeout.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));
        final DialogComponentNumber readTimeout = new DialogComponentNumber(m_settings.getReadTimeoutModel(), "", 1);
        readTimeout.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));

        final JPanel panel = new JPanel(new GridBagLayout());
        final GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Connection timeout (seconds): "), c);

        c.gridy = 1;
        panel.add(new JLabel("Read timeout (seconds): "), c);

        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        panel.add(connectionTimeout.getComponentPanel(), c);

        c.gridy = 1;
        panel.add(readTimeout.getComponentPanel(), c);

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        c.weighty = 1;
        panel.add(Box.createVerticalGlue(), c);

        panel.setBorder(BorderFactory.createTitledBorder("Connection settings"));
        return panel;
    }

    private void validateSettingsBeforeSave() throws InvalidSettingsException {
        m_settings.validate();
        m_workingDirChooser.addCurrentSelectionToHistory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        validateSettingsBeforeSave();
        m_settings.saveSettingsTo(settings);
    }

    private void settingsLoaded() {
        m_workingDirChooser.setSelectedWorkingDirectory(m_settings.getWorkingDirectoryModel().getStringValue());
        m_workingDirChooser.addListener((e) -> m_settings.getWorkingDirectoryModel()
                .setStringValue(m_workingDirChooser.getSelectedWorkingDirectory()));
    }

    @Override
    public void onClose() {
        m_workingDirChooser.onClose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        try {
            m_settings.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex) {
            // ignore
        }

        m_connection = ((MicrosoftConnectionPortObjectSpec) specs[0]).getMicrosoftConnection();
        if (m_connection == null) {
            throw new NotConfigurableException("Authentication required");
        }

        m_sitePanel.settingsLoaded(m_connection);
        settingsLoaded();
    }
}
