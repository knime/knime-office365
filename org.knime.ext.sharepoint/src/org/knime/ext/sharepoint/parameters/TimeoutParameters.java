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
 *  History
 *   2025-01-21 (Jannik Löscher): created
 */
package org.knime.ext.sharepoint.parameters;

import java.time.Duration;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.Migrate;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;

/**
 * Reusable timeout parameters component for SharePoint nodes.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public class TimeoutParameters implements NodeParameters {

    /**
     * Max timeout value to prevent overflow since the timeout needs to be set in
     * milliseconds and this requires a multiplication by 1000 later
     */
    private static final int MAX_TIMEOUT = Integer.MAX_VALUE / 1000;

    private static final int DEFAULT_TIMEOUT = 20;

    @Advanced
    @Section(title = "Timeouts")
    interface TimeoutsSection {
    }

    @Widget(title = "Connection timeout in seconds", //
            description = "The timeout in seconds to establish a connection, or 0 for an infinite timeout.")
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class, maxValidation = MaxSeconds.class)
    @Layout(TimeoutsSection.class)
    @Migrate(loadDefaultIfAbsent = true) // from SettingsModelInt which behaves like that
    int m_connectionTimeout = DEFAULT_TIMEOUT;

    @Widget(title = "Read timeout in seconds", description = """
            The timeout in seconds to read data from an established connection, or
            0 for an infinite timeout.""")
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class, maxValidation = MaxSeconds.class)
    @Layout(TimeoutsSection.class)
    @Migrate(loadDefaultIfAbsent = true) // from SettingsModelInt which behaves like that
    int m_readTimeout = DEFAULT_TIMEOUT;

    /**
     * Get the connection timeout in milliseconds.
     *
     * @return the connection timeout in milliseconds
     */
    public int getConnectionTimeoutMillis() {
        return Math.toIntExact(Duration.ofSeconds(m_connectionTimeout).toMillis());
    }

    /**
     * Get the read timeout in milliseconds.
     *
     * @return the read timeout in milliseconds
     */
    public int getReadTimeoutMillis() {
        return Math.toIntExact(Duration.ofSeconds(m_readTimeout).toMillis());
    }

    @Override
    public void validate() throws InvalidSettingsException {
        CheckUtils.checkSetting(m_readTimeout >= 0, "Please provide a non-negative read timeout.");
        CheckUtils.checkSetting(m_connectionTimeout >= 0, "Please provide a non-negative connection timeout.");
        CheckUtils.checkSetting(m_readTimeout <= MAX_TIMEOUT, "Please provide a read timeout which does not exceed %d.",
                MAX_TIMEOUT);
        CheckUtils.checkSetting(m_connectionTimeout <= MAX_TIMEOUT,
                "Please provide a connection timeout which does not exceed %d.", MAX_TIMEOUT);
    }

    static class MaxSeconds extends NumberInputWidgetValidation.MaxValidation {

        @Override
        protected double getMax() {
            return MAX_TIMEOUT;
        }
    }
}
