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
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.knime.core.util.Pair;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.List;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Sharepoint List utility method class.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharePointListUtils {

    // Pattern of the settings is actually "<displayName> (<internalName>)"
    private static final Pattern SETTINGS_INTERNAL_LIST_NAME_PATTERN = Pattern.compile("(.+) \\(([^)]+)\\)");

    private SharePointListUtils() {
        // hide constructor for Utils class
    }

    /**
     * Returns the internal name from the legacy list settings name by matching the
     * settings pattern.
     *
     * @param listSettingsName
     *            the list name from the legacy settings
     * @return the internal name of the list settings name, or {@code null} if no
     *         internal name component was found.
     */
    public static String getInternalListName(final String listSettingsName) {
        final var m = SETTINGS_INTERNAL_LIST_NAME_PATTERN.matcher(listSettingsName);
        if (m.matches()) {
            return m.group(2);
        }
        return null;
    }

    /**
     * Returns the display and internal name from the legacy list settings name by
     * matching the settings pattern.
     *
     * @param listSettingsName
     *            the list name from the legacy settings
     * @return the display and internal name of the list settings name, or
     *         {@code null} if the string was not in legacy settings pattern.
     */
    public static Pair<String, String> getDisplayAndInternalListName(final String listSettingsName) {
        final var m = SETTINGS_INTERNAL_LIST_NAME_PATTERN.matcher(listSettingsName);
        if (m.matches()) {
            return Pair.create(m.group(1), m.group(2));
        }
        return null;
    }

    /**
     * Get a list by name by checking which list internal name matches the given
     * name.
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param internalName
     *            the internal name of the list
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     *             if an error happened during the request
     *
     * @apiNote This method exists for backwards compatibility with the questionable
     *          behavior of the "Delete Sharepoint List" node. Please use
     *          {@link #getListIdByInternalOrDisplayName(GraphServiceClient, String, String)}
     *          instead!
     */
    public static Optional<String> getListIdByInternalName(final GraphServiceClient<Request> client, //
            final String siteId, final String internalName) throws IOException {
        // no last resort needed as the internal name may not contain parentheses
        return getListIdByFilter(client, siteId, l -> l.name.equals(internalName), null);
    }

    /**
     * Get a list by name by checking which list internal name matches the given
     * name if it's in "display name (internal name)" form, otherwise the whole
     * string will be assumed to be a display name.
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param listName
     *            the name of the list, if it does not match the "display name
     *            (internal name)" form, the whole string will be assumed to be a
     *            display name.
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     *             if an error happened during the request
     */
    public static Optional<String> getListIdByInternalOrDisplayName(final GraphServiceClient<Request> client, //
            final String siteId, final String listName) throws IOException {
        final var name = getInternalListName(listName);
        if (name != null) {
            // if the list display name ended with parentheses and thus accidentally matched
            // the pattern, use the full name as a last resort
            return getListIdByFilter(client, siteId, l -> l.name.equals(name), l -> l.displayName.equals(listName));
        } else {
            return getListIdByFilter(client, siteId, l -> l.displayName.equals(listName), null);
        }
    }

    /**
     * Get a list by name by checking that the given property matches the given
     * value.
     *
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param filter
     *            predicate used to select list whose id to return
     * @param lastResortFilter
     *            predicate used to select list whose id to return if no list
     *            matched the normal filter. May be {@code null}.
     * @param lastResortValue
     *            if the given property value does not match any of the lists, try
     *            to match this value instead. May be null.
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     *             if an error happened during the request
     */
    private static Optional<String> getListIdByFilter(final GraphServiceClient<Request> client, final String siteId,
            final Predicate<List> filter, final Predicate<List> lastResortFilter)
            throws IOException {
        var result = Optional.<String>empty();
        final var lists = new LinkedList<List>();
        try {
            var nextRequest = client.sites(siteId).lists();

            while (nextRequest != null) {
                final var resp = nextRequest.buildRequest().get();
                result = resp.getCurrentPage().stream().filter(filter).findFirst().map(l -> l.id);
                if (result.isPresent()) {
                    return result;
                }
                lists.addAll(resp.getCurrentPage());
                nextRequest = resp.getNextPage();
            }

            if (lastResortFilter == null) {
                return result;
            } else {
                return result.or(() -> lists.stream().filter(lastResortFilter).findFirst().map(l -> l.id));
            }
        } catch (GraphServiceException ex) {
            throw new IOException("Error during list ID retrival: " + ex.getServiceError().message, ex);
        }
    }
}
