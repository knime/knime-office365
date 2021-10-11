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
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

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

    private static final int DEFAULT_TIMEOUT = 20;

    private static final String KEY_CONNECTION_TIMEOUT = "connectionTimeout";

    private static final String KEY_READ_TIMEOUT = "readTimeout";

    private static final String KEY_SITE_SITTINGS = "site";

    private final S m_settings;

    private final SettingsModelIntegerBounded m_connectionTimeout;

    private final SettingsModelIntegerBounded m_readTimeout;

    /**
     * Constructor.
     *
     * @param settings
     *            the Settings object which is an instance of {@link SiteSettings}
     */
    protected AbstractSharePointSettings(final S settings) {
        m_settings = settings;
        m_connectionTimeout = new SettingsModelIntegerBounded(KEY_CONNECTION_TIMEOUT, DEFAULT_TIMEOUT, 0,
                Integer.MAX_VALUE);
        m_readTimeout = new SettingsModelIntegerBounded(KEY_READ_TIMEOUT, DEFAULT_TIMEOUT, 0, Integer.MAX_VALUE);
    }

    /**
     * Copy constructor.
     *
     * @param toCopy
     *            the settings to be copied
     */
    protected AbstractSharePointSettings(final T toCopy) {
        m_settings = toCopy.getSiteSettings();
        m_connectionTimeout = toCopy.getConnectionTimeoutModel();
        m_readTimeout = toCopy.getReadTimeoutModel();
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
        m_connectionTimeout.saveSettingsTo(settings);
        m_readTimeout.saveSettingsTo(settings);
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
        m_connectionTimeout.validateSettings(settings);
        m_readTimeout.validateSettings(settings);
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
        m_connectionTimeout.loadSettingsFrom(settings);
        m_readTimeout.loadSettingsFrom(settings);
    }

    /**
     * @return the siteSettings
     */
    public S getSiteSettings() {
        return m_settings;
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

}
