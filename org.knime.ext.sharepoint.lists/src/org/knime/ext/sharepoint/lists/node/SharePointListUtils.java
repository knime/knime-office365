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
 *   2022-03-01 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint.lists.node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.IGraphServiceClient;

/**
 * Sharepoint List utility method class.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharePointListUtils {

    // Pattern of the settings is actually "<displayName> (<internalName>)"
    private static final Pattern SETTINGS_INTERNAL_LIST_NAME_PATTERN = Pattern.compile(".*\\(([^)]+)\\)");

    // Matches everything until the last "("
    private static final Pattern SETTINGS_DISPLAY_LIST_NAME_PATTERN = Pattern.compile("(.*(?=\\())");

    private SharePointListUtils() {
        // hide constructor for Utils class
    }

    /**
     * Checks whether or not a list name matches the naming pattern.
     *
     * @param listSettingsName
     *            the list name from the settings
     * @return true or false whether or not a list name the naming pattern
     */
    public static boolean matchesSettingsListNamePattern(final String listSettingsName) {
        return SETTINGS_INTERNAL_LIST_NAME_PATTERN.matcher(listSettingsName).matches();
    }

    /**
     * Returns the display name from the list settings name by matching the the
     * settings pattern.
     *
     * @param listSettingsName
     *            the list name from the settings
     * @return the display name of the list settings name
     */
    public static String getListDisplayName(final String listSettingsName) {
        if (matchesSettingsListNamePattern(listSettingsName)) {
            final var m = SETTINGS_DISPLAY_LIST_NAME_PATTERN.matcher(listSettingsName);
            if (m.find()) {
                return m.group(1);
            }
        }
        return listSettingsName;
    }

    /**
     * Returns the internal name from the list settings name by matching the the
     * settings pattern.
     *
     * @param listSettingsName
     *            the list name from the settings
     * @return the internal name of the list settings name
     */
    public static String getInternalListName(final String listSettingsName) {
        if (matchesSettingsListNamePattern(listSettingsName)) {
            final var m = SETTINGS_INTERNAL_LIST_NAME_PATTERN.matcher(listSettingsName);
            if (m.find()) {
                return m.group(1);
            }
        }
        return listSettingsName;
    }

    /**
     * Get a list by name by checking which list internal name matches the name.
     *
     * @param client
     *            the {@link IGraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param listName
     *            the name of the list
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     */
    public static Optional<String> getListIdByName(final IGraphServiceClient client, final String siteId,
            final String listName) throws IOException {
        try {
            var resp = client.sites(siteId).lists().buildRequest().get();
            final var lists = new ArrayList<>(resp.getCurrentPage());

            var nextRequest = resp.getNextPage();
            while (nextRequest != null) {
                resp = nextRequest.buildRequest().get();
                final var listsTmp = resp.getCurrentPage();
                lists.addAll(listsTmp);
                nextRequest = resp.getNextPage();
            }

            final var name = getInternalListName(listName);

            return lists.stream().filter(l -> l.name.equals(name)).findAny().map(l -> l.id);
        } catch (GraphServiceException ex) {
            throw new IOException("Error during list name retrival: " + ex.getServiceError().message, ex);
        }
    }

}
