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

import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.dialog.SiteSettingsPanel;
import org.knime.ext.sharepoint.dialog.TimeoutPanel;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnection;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.base.ui.WorkingDirectoryChooser;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;

/**
 * Node dialog for the Sharepoint Connector node.
 *
 * @author Alexander Bondaletov
 */
final class SharepointConnectionNodeDialog extends NodeDialogPane {

    private final SharepointConnectionSettings m_settings = new SharepointConnectionSettings();

    private final SiteSettingsPanel m_sitePanel = new SiteSettingsPanel(m_settings.getSiteSettings());

    private final TimeoutPanel m_timeoutPanel = new TimeoutPanel(m_settings.getTimeoutSettings());

    private final WorkingDirectoryChooser m_workingDirChooser = new WorkingDirectoryChooser("sharepoint.workingDir",
            this::createFSConnection);

    private CredentialPortObjectSpec m_credentialPortSpec;

    /**
     * Creates new instance.
     */
    SharepointConnectionNodeDialog() {
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
        final SharepointConnectionSettings clonedSettings = m_settings.copy(m_settings);

        try {
            final var fsConfig = clonedSettings
                    .toFSConnectionConfig(GraphCredentialUtil.createAuthenticationProvider(m_credentialPortSpec));

            return new SharepointFSConnection(fsConfig);
        } catch (Exception ex) {
            throw ExceptionUtil.wrapAsIOException(ex);
        }
    }

    private JComponent createTimeoutsPanel() {
        return m_timeoutPanel;
    }

    private void validateSettingsBeforeSave() throws InvalidSettingsException {
        m_settings.validate();
        m_workingDirChooser.addCurrentSelectionToHistory();
    }

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

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        try {
            m_settings.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex) {// NOSONAR
            // ignore
        }

        try {
            m_credentialPortSpec = (CredentialPortObjectSpec) specs[0];
            // resolve to check that a credential is present (not necessarily compatible)
            m_credentialPortSpec.resolveCredential(Credential.class);
        } catch (NoSuchCredentialException ex) {
            throw new NotConfigurableException(ex.getMessage(), ex);
        }

        m_sitePanel.settingsLoaded(m_credentialPortSpec);
        settingsLoaded();
    }
}
