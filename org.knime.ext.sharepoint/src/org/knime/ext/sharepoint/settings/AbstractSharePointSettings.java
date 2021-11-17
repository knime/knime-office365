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

/**
 * Abstract class for Sharepoint settings which are common for certain
 * Sharepoint nodes.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @param <S>
 *            the type of {@link SiteSettings}
 * @param <T>
 *            the concrete implementation type (S for self), i.e. the class that
 *            is extending this class
 */
public abstract class AbstractSharePointSettings<S extends SiteSettings, T extends AbstractSharePointSettings<S, T>> {

    private static final String KEY_SITE_SITTINGS = "site";

    private final S m_settings;

    private final TimeoutSettings m_timeoutSettings;


    /**
     * Constructor.
     *
     * @param settings
     *            the Settings object which is an instance of {@link SiteSettings}
     */
    protected AbstractSharePointSettings(final S settings) {
        m_settings = settings;
        m_timeoutSettings = new TimeoutSettings();
    }

    /**
     * Copy constructor.
     *
     * @param toCopy
     *            the settings to be copied
     */
    protected AbstractSharePointSettings(final T toCopy) {
        m_settings = toCopy.getSiteSettings();
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
        m_settings.saveSettingsTo(settings.addNodeSettings(KEY_SITE_SITTINGS));
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
        m_settings.validateSettings(settings.getNodeSettings(KEY_SITE_SITTINGS));
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
        m_settings.loadSettingsFrom(settings.getNodeSettings(KEY_SITE_SITTINGS));
        m_timeoutSettings.loadSettingsFrom(settings);
    }

    /**
     * @return the siteSettings
     */
    public S getSiteSettings() {
        return m_settings;
    }

    /**
     * @return the {@link TimeoutSettings}
     */
    public TimeoutSettings getTimeoutSettings() {
        return m_timeoutSettings;
    }
}
