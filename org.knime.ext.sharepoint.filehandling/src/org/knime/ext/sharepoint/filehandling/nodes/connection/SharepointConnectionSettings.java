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
package org.knime.ext.sharepoint.filehandling.nodes.connection;

import java.io.IOException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.ext.sharepoint.filehandling.GraphApiUtil;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.ISiteRequestBuilder;

/**
 * Settings for {@link SharepointConnectionNodeModel}.
 *
 * @author Alexander Bondaletov
 */
public class SharepointConnectionSettings {

    private static final String KEY_WORKING_DIRECTORY = "workingDirectory";
    private static final String KEY_CONNECTION_TIMEOUT = "connectionTimeout";
    private static final String KEY_READ_TIMEOUT = "readTimeout";
    private static final String KEY_SITE_SITTINGS = "site";

    private static final int DEFAULT_TIMEOUT = 20;

    private final SiteSettings m_siteSettings;
    private final SettingsModelString m_workingDirectory;
    private final SettingsModelIntegerBounded m_connectionTimeout;
    private final SettingsModelIntegerBounded m_readTimeout;

    /**
     *
     */
    public SharepointConnectionSettings() {
        m_siteSettings = new SiteSettings();

        m_workingDirectory = new SettingsModelString(KEY_WORKING_DIRECTORY, "");
        m_connectionTimeout = new SettingsModelIntegerBounded(KEY_CONNECTION_TIMEOUT, DEFAULT_TIMEOUT, 0,
                Integer.MAX_VALUE);
        m_readTimeout = new SettingsModelIntegerBounded(KEY_READ_TIMEOUT, DEFAULT_TIMEOUT, 0, Integer.MAX_VALUE);
    }

    /**
     * Saves the settings in this instance to the given {@link NodeSettingsWO}
     *
     * @param settings
     *            Node settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_workingDirectory.saveSettingsTo(settings);
        m_connectionTimeout.saveSettingsTo(settings);
        m_readTimeout.saveSettingsTo(settings);
        m_siteSettings.saveSettingsTo(settings.addNodeSettings(KEY_SITE_SITTINGS));
    }

    /**
     * Validates the settings in a given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workingDirectory.validateSettings(settings);
        m_connectionTimeout.validateSettings(settings);
        m_readTimeout.validateSettings(settings);

        SharepointConnectionSettings temp = new SharepointConnectionSettings();
        temp.loadSettingsFrom(settings);
        temp.validate();
    }

    /**
     * Validates settings consistency for this instance.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        m_siteSettings.validate();
    }

    /**
     * Loads settings from the given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workingDirectory.loadSettingsFrom(settings);
        m_connectionTimeout.loadSettingsFrom(settings);
        m_readTimeout.loadSettingsFrom(settings);
        m_siteSettings.loadSettingsFrom(settings.getNodeSettings(KEY_SITE_SITTINGS));
    }

    /**
     * @return the siteSettings
     */
    public SiteSettings getSiteSettings() {
        return m_siteSettings;
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
     * @return the connectionTimeout settings model
     */
    public SettingsModelIntegerBounded getConnectionTimeoutModel() {
        return m_connectionTimeout;
    }

    /**
     * @return the connectionTimeout
     */
    public int getConnectionTimeout() {
        return m_connectionTimeout.getIntValue();
    }

    /**
     * @return the readTimeout settings model
     */
    public SettingsModelIntegerBounded getReadTimeoutModel() {
        return m_readTimeout;
    }

    /**
     * @return the readTimeout
     */
    public int getReadTimeout() {
        return m_readTimeout.getIntValue();
    }

    /**
     * Returns the selected site or subsite id.
     *
     * @param client
     *            The client instance.
     * @return The site id.
     * @throws IOException
     */
    public String getSiteId(final IGraphServiceClient client) throws IOException {
        return m_siteSettings.getTargetSiteId(client);

    }

    /**
     * Class represents chosen site settings.
     *
     * @author Alexander Bondaletov
     */
    public static class SiteSettings {
        private static final String KEY_SITE = "site";
        private static final String KEY_GROUP = "group";
        private static final String KEY_GROUP_NAME = "groupName";
        private static final String KEY_MODE = "mode";
        private static final String KEY_SUBSITE = "subsite";
        private static final String KEY_SUBSITE_NAME = "subsiteName";

        private static final String ROOT_SITE = "root";

        private final SettingsModelString m_site;
        private final SettingsModelString m_group;
        private final SettingsModelString m_groupName;
        private final SettingsModelString m_mode;
        private final SettingsModelString m_subsite;
        private final SettingsModelString m_subsiteName;

        /**
         * Creates new instance
         */
        public SiteSettings() {
            m_site = new SettingsModelString(KEY_SITE, "");
            m_group = new SettingsModelString(KEY_GROUP, "");
            m_groupName = new SettingsModelString(KEY_GROUP_NAME, "");
            m_mode = new SettingsModelString(KEY_MODE, SiteMode.SITE.name());
            m_subsite = new SettingsModelString(KEY_SUBSITE, "");
            m_subsiteName = new SettingsModelString(KEY_SUBSITE_NAME, "");
        }

        /**
         * Saves the settings in this instance to the given {@link NodeSettingsWO}
         *
         * @param settings
         *            Node settings.
         */
        public void saveSettingsTo(final NodeSettingsWO settings) {
            m_site.saveSettingsTo(settings);
            m_group.saveSettingsTo(settings);
            m_groupName.saveSettingsTo(settings);
            m_mode.saveSettingsTo(settings);
            m_subsite.saveSettingsTo(settings);
            m_subsiteName.saveSettingsTo(settings);
        }

        /**
         * Validates the settings in a given {@link NodeSettingsRO}
         *
         * @param settings
         *            Node settings.
         * @throws InvalidSettingsException
         */
        public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
            m_site.validateSettings(settings);
            m_group.validateSettings(settings);
            m_groupName.validateSettings(settings);
            m_subsite.validateSettings(settings);
            m_subsiteName.validateSettings(settings);
            m_mode.validateSettings(settings);
        }

        /**
         * Validates settings consistency for this instance.
         *
         * @throws InvalidSettingsException
         */
        public void validate() throws InvalidSettingsException {
            SiteMode mode = getMode();
            if (mode == SiteMode.GROUP && m_group.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Group is not selected.");
            }
        }

        /**
         * Loads settings from the given {@link NodeSettingsRO}
         *
         * @param settings
         *            Node settings.
         * @throws InvalidSettingsException
         */
        public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
            m_site.loadSettingsFrom(settings);
            m_group.loadSettingsFrom(settings);
            m_groupName.loadSettingsFrom(settings);
            m_mode.loadSettingsFrom(settings);
            m_subsite.loadSettingsFrom(settings);
            m_subsiteName.loadSettingsFrom(settings);
        }

        /**
         * @return the site model
         */
        public SettingsModelString getSiteModel() {
            return m_site;
        }

        /**
         * @return the group model
         */
        public SettingsModelString getGroupModel() {
            return m_group;
        }

        /**
         * @return the groupName model
         */
        public SettingsModelString getGroupNameModel() {
            return m_groupName;
        }

        /**
         * @return the mode model
         */
        public SettingsModelString getModeModel() {
            return m_mode;
        }

        /**
         * @return the mode
         */
        public SiteMode getMode() {
            try {
                return SiteMode.valueOf(m_mode.getStringValue());
            } catch (IllegalArgumentException e) {
                return SiteMode.SITE;
            }
        }

        /**
         * @return the subsite model
         */
        public SettingsModelString getSubsiteModel() {
            return m_subsite;
        }

        /**
         * @return the subsiteName model
         */
        public SettingsModelString getSubsiteNameModel() {
            return m_subsiteName;
        }

        /**
         * Returns the selected site or subsite id.
         *
         * @param client
         *            The client.
         * @return The site id.
         * @throws IOException
         */
        public String getTargetSiteId(final IGraphServiceClient client) throws IOException {
            if (!m_subsite.getStringValue().isEmpty()) {
                return m_subsite.getStringValue();
            }

            return getParentSiteId(client);
        }

        /**
         * The selected site id.
         *
         * @param client
         *            The client
         * @return The site id.
         * @throws IOException
         */
        public String getParentSiteId(final IGraphServiceClient client) throws IOException {
            SiteMode m = getMode();
            ISiteRequestBuilder req = null;

            if (m == SiteMode.SITE) {
                String id = m_site.getStringValue();
                if (id.isEmpty()) {
                    id = ROOT_SITE;
                }
                req = client.sites(id);
            } else {
                req = client.groups(m_group.getStringValue()).sites(ROOT_SITE);
            }

            try {
                return req.buildRequest().get().id;
            } catch (ClientException e) {
                throw GraphApiUtil.unwrapIOE(e);
            }
        }
    }

    enum SiteMode {
        SITE("By the site id"), //
        GROUP("By the group name");

        private String m_selectorLabel;

        private SiteMode(final String selectorLable) {
            m_selectorLabel = selectorLable;
        }

        /**
         * @return the selectorLabel
         */
        public String getSelectorLabel() {
            return m_selectorLabel;
        }
    }
}