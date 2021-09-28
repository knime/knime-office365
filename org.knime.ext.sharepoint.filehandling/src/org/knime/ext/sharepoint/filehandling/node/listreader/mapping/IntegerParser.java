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
 *   Jul 2, 2020 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader.mapping;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.knime.ext.sharepoint.filehandling.node.listreader.SharepointListReaderConfig;

/**
 * Parses integer and long values from Strings. Allows to specify a thousands separator.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class IntegerParser {

    private final String m_thousandsSeparator;

    private final Pattern m_thousandsPattern;

    private final boolean m_replace;

    IntegerParser(final SharepointListReaderConfig config) {
        char thousandsSeparator = '\0';
        m_thousandsSeparator = Pattern.quote(Character.toString(thousandsSeparator));
        m_replace = thousandsSeparator != '\0';
        m_thousandsPattern = Pattern.compile("(?i)[+-]?\\d{0,3}(?:" + m_thousandsSeparator + "\\d{3})*");
    }

    int parseInt(final String value) {
        return Integer.parseInt(format(value));
    }

    long parseLong(final String value) {
        return Long.parseLong(format(value));
    }

    private String format(final String value) {
        if (m_replace) {
            final Matcher thousandMatcher = m_thousandsPattern.matcher(value);
            if (thousandMatcher.matches()) {
                return value.replaceAll(m_thousandsSeparator, "");
            } else {
                throw new NumberFormatException("Integer format didn't match.");
            }
        } else {
            return value;
        }
    }

}
