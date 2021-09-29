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
 *   2021-09-28 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.ext.sharepoint.filehandling.node.SiteSettings;

/**
 * Class represents chosen site and list settings.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public class ListSettings {
    private static final String KEY_LIST = "list";
    private static final String KEY_LIST_NAME = "listName";

    private final SettingsModelString m_list;
    private final SettingsModelString m_listName;

    private final SiteSettings m_siteSettings;

    /**
     * Creates new instance
     */
    public ListSettings(final SiteSettings siteSettings) {
        super();
        m_list = new SettingsModelString(KEY_LIST, "");
        m_listName = new SettingsModelString(KEY_LIST_NAME, "");
        m_siteSettings = siteSettings;

    }

    // TODO
    private void resetDropdowns() {
        m_list.setStringValue("");
        m_listName.setStringValue("");
    }

    /**
     * Saves the settings in this instance to the given {@link NodeSettingsWO}
     *
     * @param settings
     *            Node settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_list.saveSettingsTo(settings);
        m_listName.saveSettingsTo(settings);
    }

    /**
     * Validates the settings in a given {@link NodeSettingsRO}
     *
     * @param settings
     *            Node settings.
     * @throws InvalidSettingsException
     */
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_list.validateSettings(settings);
        m_listName.validateSettings(settings);
    }

    /**
     * Validates settings consistency for this instance.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        if (m_list.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("No list is selected.");
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
        m_list.loadSettingsFrom(settings);
        m_listName.loadSettingsFrom(settings);
    }

    /**
     * @return the list model
     */
    public SettingsModelString getListModel() {
        return m_list;
    }

    /**
     * @return the listName model
     */
    public SettingsModelString getListNameModel() {
        return m_listName;
    }

    public SiteSettings getSiteSettings() {
        return m_siteSettings;
    }
}