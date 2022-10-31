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
 *   2021-09-29 (lars.schweikardt): created
 */

package org.knime.ext.sharepoint.settings;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.microsoft.authentication.port.oauth2.ScopeType;

/**
 * Abstract class for Sharepoint settings which are common for certain
 * Sharepoint nodes.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @param <T>
 *            the concrete implementation type (S for self), i.e. the class that
 *            is extending this class
 */
public abstract class AbstractSharePointSettings<T extends AbstractSharePointSettings<T>> {

    private final SiteSettings m_siteSettings;

    private final TimeoutSettings m_timeoutSettings;

    /**
     * Constructor.
     */
    protected AbstractSharePointSettings() {
        m_siteSettings = new SiteSettings();
        m_timeoutSettings = new TimeoutSettings();
    }

    /**
     * Copy constructor.
     *
     * @param toCopy
     *            the settings to be copied
     */
    protected AbstractSharePointSettings(final T toCopy) {
        m_siteSettings = toCopy.getSiteSettings();
        m_timeoutSettings = toCopy.getTimeoutSettings();
    }

    /**
     * Returns a copy of the settings.
     *
     * @param settings
     *            the settings object to be copied
     * @return the copy of the settings object
     */
    public abstract T copy(final T settings);

    /**
     * Saves the settings in this instance to the given {@link NodeSettingsWO}
     *
     * @param settings
     *            Node settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_siteSettings.saveSettingsTo(settings);
        m_timeoutSettings.saveSettingsTo(settings);
    }

    /**
     * Validates the settings in a given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_siteSettings.validateSettings(settings);
        m_timeoutSettings.validateSettings(settings);
    }

    /**
     * Loads settings from the given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_siteSettings.loadSettingsFrom(settings);
        m_timeoutSettings.loadSettingsFrom(settings);
    }

    /**
     * @return the siteSettings
     */
    public SiteSettings getSiteSettings() {
        return m_siteSettings;
    }

    /**
     * @return the {@link TimeoutSettings}
     */
    public TimeoutSettings getTimeoutSettings() {
        return m_timeoutSettings;
    }

    /**
     * @param credential
     *            {@link MicrosoftCredential}
     * @throws InvalidSettingsException
     *             if Group site is used with application scope type
     */
    public void validateCredential(final MicrosoftCredential credential) throws InvalidSettingsException {
        final var mode = SiteMode.valueOf(getSiteSettings().getModeModel().getStringValue());

        if (!(credential instanceof OAuth2Credential)) {
            throw new InvalidSettingsException("Provided credentials cannot be used with SharePoint");
        }
        if (mode == SiteMode.GROUP && ((OAuth2Credential) credential).getScopeType() == ScopeType.APPLICATION) {
            throw new InvalidSettingsException("Client Secret Authentication cannot be used with Group site");
        }
    }
}
