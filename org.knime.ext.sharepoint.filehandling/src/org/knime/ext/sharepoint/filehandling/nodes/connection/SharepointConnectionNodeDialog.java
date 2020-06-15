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
package org.knime.ext.sharepoint.filehandling.nodes.connection;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

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
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.sharepoint.filehandling.auth.data.AzureConnection;
import org.knime.ext.sharepoint.filehandling.auth.data.AzureConnectionPortObjectSpec;

/**
 * Sharepoint Connection node dialog.
 *
 * @author Alexander Bondaletov
 */
public class SharepointConnectionNodeDialog extends NodeDialogPane {

    private final SharepointConnectionSettings m_settings;
    private SiteSettingsPanel m_sitePanel;

    /**
     * Creates new instance.
     */
    public SharepointConnectionNodeDialog() {
        super();
        m_settings = new SharepointConnectionSettings();

        m_sitePanel = new SiteSettingsPanel(m_settings.getSiteSettings());

        DialogComponentString workingDir = new DialogComponentString(m_settings.getWorkingDirectoryModel(),
                "Working directory", false, 45);
        workingDir.getComponentPanel().setBorder(BorderFactory.createTitledBorder("File system settings"));
        workingDir.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));

        Box box = new Box(BoxLayout.PAGE_AXIS);
        box.add(m_sitePanel);
        box.add(workingDir.getComponentPanel());
        box.add(createTimeoutsPanel());

        addTab("Settings", box);
    }

    private JComponent createTimeoutsPanel() {
        DialogComponentNumber connectionTimeout = new DialogComponentNumber(m_settings.getConnectionTimeoutModel(), "",
                1);
        connectionTimeout.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));
        DialogComponentNumber readTimeout = new DialogComponentNumber(m_settings.getReadTimeoutModel(), "", 1);
        readTimeout.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Connection timeout in seconds"), c);

        c.gridy = 1;
        panel.add(new JLabel("Read timeout in seconds"), c);

        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        panel.add(connectionTimeout.getComponentPanel(), c);

        c.gridy = 1;
        panel.add(readTimeout.getComponentPanel(), c);

        panel.setBorder(BorderFactory.createTitledBorder("Connection settings"));
        return panel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_settings.saveSettingsTo(settings);
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

        AzureConnection connection = ((AzureConnectionPortObjectSpec) specs[0]).getAzureConnection();
        if (connection == null || !connection.isLoggedIn()) {
            throw new NotConfigurableException("Authentication required");
        }

        m_sitePanel.settingsLoaded(connection);
    }
}
