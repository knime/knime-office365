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
 *   2020-05-17 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.node;

import java.time.Duration;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnectionConfig;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFileSystem;
import org.knime.filehandling.core.connections.meta.FSConnectionConfig;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Node settings for the Sharepoint Connector node.
 *
 * @author Alexander Bondaletov
 */
@SuppressWarnings("deprecation")
class SharepointConnectionSettings extends AbstractSharepointSettings {

    private static final String KEY_WORKING_DIRECTORY = "workingDirectory";

    private final SettingsModelString m_workingDirectory;

    SharepointConnectionSettings() {
        super();
        m_workingDirectory = new SettingsModelString(KEY_WORKING_DIRECTORY, SharepointFileSystem.PATH_SEPARATOR);
    }

    /**
     * Saves the settings in this instance to the given {@link NodeSettingsWO}
     *
     * @param settings
     *            Node settings.
     */
    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_workingDirectory.saveSettingsTo(settings);
        super.saveSettingsTo(settings);
    }

    /**
     * Validates the settings in a given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    @Override
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workingDirectory.validateSettings(settings);
        super.validateSettings(settings);
    }

    /**
     * Validates settings consistency for this instance.
     *
     * @throws InvalidSettingsException
     */
    @Override
    public void validate() throws InvalidSettingsException {
        super.validate();
        if (m_workingDirectory.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Working directory must be specified.");
        }

        if (!m_workingDirectory.getStringValue().startsWith(SharepointFileSystem.PATH_SEPARATOR)) {
            throw new InvalidSettingsException(
                    String.format("Working directory must be an absolute path that starts with '%s'.",
                            SharepointFileSystem.PATH_SEPARATOR));
        }
    }

    /**
     * Loads settings from the given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workingDirectory.loadSettingsFrom(settings);
        super.loadSettingsFrom(settings);
    }

    /**
     * @return the workingDirectory settings model
     */
    public SettingsModelString getWorkingDirectoryModel() {
        return m_workingDirectory;
    }

    /**
     *
     * @param defaultDir
     *            Default value to return in case the corresponding settings is
     *            empty
     * @return the workingDirectory
     */
    public String getWorkingDirectory(final String defaultDir) {
        String workDir = m_workingDirectory.getStringValue();
        if (workDir.isEmpty()) {
            return defaultDir;
        }
        return workDir;
    }

    /**
     *
     * @param authProvider
     *            The authentication provider.
     * @return The {@link FSConnectionConfig} for Sharepoint
     */
    public SharepointFSConnectionConfig toFSConnectionConfig(final IAuthenticationProvider authProvider) {
        final SharepointFSConnectionConfig config = new SharepointFSConnectionConfig(
                getWorkingDirectory(SharepointFileSystem.PATH_SEPARATOR), authProvider);
        config.setReadTimeOut(Duration.ofSeconds(getReadTimeout()));
        config.setConnectionTimeOut(Duration.ofSeconds(getConnectionTimeout()));
        config.setMode(getSiteSettings().getMode());
        config.setGroup(getSiteSettings().getGroupModel().getStringValue());
        config.setWebURL(getSiteSettings().getWebURLModel().getStringValue());
        if (getSiteSettings().getConnectToSubsiteModel().getBooleanValue()
                && !getSiteSettings().getSubsiteModel().getStringValue().isEmpty()) {
            config.setSubsite(getSiteSettings().getSubsiteModel().getStringValue());
        }
        return config;
    }

    @Override
    public SharepointConnectionSettings clone() {
        var transferSettings = new NodeSettings("ignored");
        saveSettingsTo(transferSettings);

        final var clone = new SharepointConnectionSettings();
        try {
            clone.loadSettingsFrom(transferSettings);
        } catch (InvalidSettingsException ex) {
            throw new IllegalStateException(ex);
        }
        return clone;
    }

}
