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
 *   2021-11-16 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint.settings;

import java.time.Duration;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.ext.sharepoint.dialog.TimeoutPanel;

/**
 * Settings for the {@link TimeoutPanel}.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class TimeoutSettings {

    /**
     * Max timeout value to prevent overflow since the timeout needs to be set in
     * milliseconds and this requires a multiplication by 1000 later
     */
    private static final int MAX_TIMEOUT = Integer.MAX_VALUE / 1000;

    private static final int DEFAULT_TIMEOUT = 20;

    private static final String KEY_CONNECTION_TIMEOUT = "connectionTimeout";

    private static final String KEY_READ_TIMEOUT = "readTimeout";

    private final SettingsModelIntegerBounded m_connectionTimeout;

    private final SettingsModelIntegerBounded m_readTimeout;

    /**
     * Constructor.
     */
    public TimeoutSettings() {
        m_connectionTimeout = new SettingsModelIntegerBounded(KEY_CONNECTION_TIMEOUT, DEFAULT_TIMEOUT, 0, MAX_TIMEOUT);
        m_readTimeout = new SettingsModelIntegerBounded(KEY_READ_TIMEOUT, DEFAULT_TIMEOUT, 0, MAX_TIMEOUT);
    }

    /**
     * Copy constructor.
     *
     * @param toCopy
     *            the {@link TimeoutSettings} to copy
     */
    public TimeoutSettings(final TimeoutSettings toCopy) {
        m_connectionTimeout = toCopy.getConnectionTimeoutModel();
        m_readTimeout = toCopy.getReadTimeoutModel();
    }

    /**
     * Saves the settings in this instance to the given {@link NodeSettingsWO}
     *
     * @param settings
     *            Node settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
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
        m_connectionTimeout.loadSettingsFrom(settings);
        m_readTimeout.loadSettingsFrom(settings);
    }

    /**
     * @return the connectionTimeout settings model
     */
    public SettingsModelIntegerBounded getConnectionTimeoutModel() {
        return m_connectionTimeout;
    }

    /**
     * @return the connectionTimeout in milliseconds
     */
    public int getConnectionTimeout() {
        return Math.toIntExact(Duration.ofSeconds(m_connectionTimeout.getIntValue()).toMillis());
    }

    /**
     * @return the readTimeout settings model
     */
    public SettingsModelIntegerBounded getReadTimeoutModel() {
        return m_readTimeout;
    }

    /**
     * @return the readTimeout in milliseconds
     */
    public int getReadTimeout() {
        return Math.toIntExact(Duration.ofSeconds(m_readTimeout.getIntValue()).toMillis());
    }

}
