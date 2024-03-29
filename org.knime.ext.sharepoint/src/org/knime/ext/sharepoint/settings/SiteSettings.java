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
 *   2021-10-05 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint.settings;

import java.util.Objects;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * Class represents chosen site settings.
 *
 * @author Alexander Bondaletov
 */
public class SiteSettings {

    private static final String KEY_SITE = "site";

    private static final String KEY_GROUP = "group";

    private static final String KEY_GROUP_NAME = "groupName";

    private static final String KEY_MODE = "mode";

    private static final String KEY_SUBSITE = "subsite";

    private static final String KEY_SUBSITE_NAME = "subsiteName";

    private static final String KEY_CONNECT_TO_SUBSITE = "connectToSubsite";

    private final SettingsModelString m_webURL;

    private final SettingsModelString m_group;

    private final SettingsModelString m_groupName;

    private final SettingsModelString m_mode;

    private final SettingsModelString m_subsite;

    private final SettingsModelString m_subsiteName;

    private final SettingsModelBoolean m_connectToSubsite;

    /**
     * Creates new instance.
     */
    public SiteSettings() {
        m_webURL = new SettingsModelString(KEY_SITE, "");
        m_group = new SettingsModelString(KEY_GROUP, "");
        m_groupName = new SettingsModelString(KEY_GROUP_NAME, "");
        m_mode = new SettingsModelString(KEY_MODE, SiteMode.ROOT.name());
        m_subsite = new SettingsModelString(KEY_SUBSITE, "");
        m_subsiteName = new SettingsModelString(KEY_SUBSITE_NAME, "");
        m_connectToSubsite = new SettingsModelBoolean(KEY_CONNECT_TO_SUBSITE, false);

        m_webURL.addChangeListener(e -> resetSubsite());
        m_group.addChangeListener(e -> resetSubsite());
        m_mode.addChangeListener(e -> resetSubsite());
    }

    /**
     * Creates new instance from {@link SiteSettings}.
     *
     * @param toCopy
     *            the {@link SiteSettings} to be copied
     */
    public SiteSettings(final SiteSettings toCopy) {
        m_webURL = toCopy.getWebURLModel();
        m_group = toCopy.getGroupModel();
        m_groupName = toCopy.getGroupNameModel();
        m_mode = toCopy.getModeModel();
        m_subsite = toCopy.getSubsiteModel();
        m_subsiteName = toCopy.getSubsiteNameModel();
        m_connectToSubsite = toCopy.getConnectToSubsiteModel();
    }

    private void resetSubsite() {
        m_subsite.setStringValue("");
        m_subsiteName.setStringValue("");
        m_connectToSubsite.setBooleanValue(false);
    }

    /**
     * Saves the settings in this instance to the given {@link NodeSettingsWO}
     *
     * @param settings
     *            Node settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        final NodeSettingsWO siteSettings = settings.addNodeSettings(KEY_SITE);
        m_webURL.saveSettingsTo(siteSettings);
        m_group.saveSettingsTo(siteSettings);
        m_groupName.saveSettingsTo(siteSettings);
        m_mode.saveSettingsTo(siteSettings);
        m_subsite.saveSettingsTo(siteSettings);
        m_subsiteName.saveSettingsTo(siteSettings);
        m_connectToSubsite.saveSettingsTo(siteSettings);
    }

    /**
     * Validates the settings in a given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        final NodeSettingsRO siteSettings = settings.getNodeSettings(KEY_SITE);
        m_webURL.validateSettings(siteSettings);
        m_group.validateSettings(siteSettings);
        m_groupName.validateSettings(siteSettings);
        m_subsite.validateSettings(siteSettings);
        m_subsiteName.validateSettings(siteSettings);
        m_mode.validateSettings(siteSettings);
        m_connectToSubsite.validateSettings(siteSettings);
    }

    /**
     * Validates settings consistency for this instance.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        validateParentSiteSettings();
        if (m_connectToSubsite.getBooleanValue() && m_subsite.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Subsite is not selected.");
        }
    }

    /**
     * Validates parent site settings (site/group settings depending on the current
     * mode)
     *
     * @throws InvalidSettingsException
     */
    public void validateParentSiteSettings() throws InvalidSettingsException {
        final SiteMode mode = getMode();
        if (mode == SiteMode.GROUP && m_group.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Group is not selected.");
        }
        if (mode == SiteMode.WEB_URL) {
            if (m_webURL.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Web URL is not specified.");
            }
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
        final NodeSettingsRO siteSettings = settings.getNodeSettings(KEY_SITE);
        m_webURL.loadSettingsFrom(siteSettings);
        m_group.loadSettingsFrom(siteSettings);
        m_groupName.loadSettingsFrom(siteSettings);
        m_mode.loadSettingsFrom(siteSettings);
        m_subsite.loadSettingsFrom(siteSettings);
        m_subsiteName.loadSettingsFrom(siteSettings);
        m_connectToSubsite.loadSettingsFrom(siteSettings);
    }

    /**
     * @return the web URL model
     */
    public SettingsModelString getWebURLModel() {
        return m_webURL;
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
            return SiteMode.ROOT;
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
     * @return the connectToSubsite model
     */
    public SettingsModelBoolean getConnectToSubsiteModel() {
        return m_connectToSubsite;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_connectToSubsite.getBooleanValue(), m_group.getStringValue(),
                m_groupName.getStringValue(), m_mode.getStringValue(), m_subsite.getStringValue(),
                m_subsiteName.getStringValue(), m_webURL.getStringValue());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final var other = (SiteSettings) obj;
        return Objects.equals(m_connectToSubsite.getBooleanValue(), other.m_connectToSubsite.getBooleanValue())
                && Objects.equals(m_group.getStringValue(), other.m_group.getStringValue())
                && Objects.equals(m_groupName.getStringValue(), other.m_groupName.getStringValue())
                && Objects.equals(m_mode.getStringValue(), other.m_mode.getStringValue())
                && Objects.equals(m_subsite.getStringValue(), other.m_subsite.getStringValue())
                && Objects.equals(m_subsiteName.getStringValue(), other.m_subsiteName.getStringValue())
                && Objects.equals(m_webURL.getStringValue(), other.m_webURL.getStringValue());
    }
}
