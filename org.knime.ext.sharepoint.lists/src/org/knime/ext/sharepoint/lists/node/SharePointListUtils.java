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
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Sharepoint List utility method class.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharePointListUtils {

    private static final String SYSTEM_LIST_COLLISION_MSG = //
            "Matching list is a read-only system list and cannot be used!";

    private static final String SELECT_NAME_DISPLAY_NAME_ID = "name,displayName";

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
     * This method will throw an exception if only a system list was found.
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
     *          {@link #getListIdByInternalOrDisplayName(GraphServiceClient, String, String, String)}
     *          instead!
     */
    public static Optional<String> getListIdByInternalName(final GraphServiceClient<Request> client, //
            final String siteId, final String internalName) throws IOException {
        // no last resort needed as the internal name may not contain parentheses
        return getListIdByFilter(client, siteId, l -> l.name.equals(internalName), "name", null);
    }

    /**
     * Get a non-system list by name by checking which list display name matches the
     * given name.
     *
     * This method will throw an exception if only a system list was found.
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param displayName
     *            the display name of the list
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     *             if an error happened during the request
     *
     * @apiNote This method exists for backwards compatibility with the questionable
     *          behavior of the "Delete Sharepoint List" node. Please use
     *          {@link #getListIdByInternalOrDisplayName(GraphServiceClient, String, String, String)}
     *          instead!
     */
    public static Optional<String> getListIdByDisplayName(final GraphServiceClient<Request> client, //
            final String siteId, final String displayName) throws IOException {
        return displayName != null ? getListIdByFilter(client, siteId, "displayName", displayName) : Optional.empty();
    }

    /**
     * Get a list by name by checking which list internal name matches the given
     * name if it was loaded from "display name (internal name)" form, otherwise the
     * internal name will be appended to the display name in parenthesis and
     * searched as display name to follow legacy behaviour.
     *
     * This method will throw an exception if only a system list was found.
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param internalName
     *            the internal name to search for, if {@code null} search for the
     *            display name instead.
     * @param displayName
     *            the display name to search for, used if internal name is not
     *            present or no list with given internal name exists (in the last
     *            case the internal name is appended in parentheses to the display
     *            name because it may have been grandfathered in from an old node
     *            configuration)
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     *             if an error happened during the request
     */
    public static Optional<String> getListIdByInternalOrDisplayNameLegacy(final GraphServiceClient<Request> client, //
            final String siteId, final String internalName, final String displayName) throws IOException {
        if (internalName != null) {
            final var fallback = "%s (%s)".formatted(displayName, internalName);
            // if the list display name ended with parentheses and thus accidentally matched
            // the pattern, use the full name as a last resort
            return getListIdByFilter(client, siteId, l -> l.name.equals(internalName), SELECT_NAME_DISPLAY_NAME_ID,
                    l -> l.displayName.equals(fallback));
        } else {
            return getListIdByFilter(client, siteId, l -> l.displayName.equals(displayName),
                    SELECT_NAME_DISPLAY_NAME_ID, null);
        }
    }

    /**
     * Get a list by name by checking which list internal name matches the given
     * name, if no such list is found the display name will be used instead. This
     * method will throw an exception if only a system list was found.
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param internalName
     *            the internal name to search for, if {@code null} search for the
     *            display name instead.
     * @param displayName
     *            the display name to search for, used if internal name is not
     *            present or no list with given internal name exists
     *
     * @return an {@link Optional} of {@link String} with the list id
     * @throws IOException
     *             if an error happened during the request
     */
    public static Optional<String> getListIdByInternalOrDisplayName(final GraphServiceClient<Request> client, //
            final String siteId, final String internalName, final String displayName) throws IOException {
        if (internalName != null) {
            return getListIdByFilter(client, siteId, l -> l.name.equals(internalName), SELECT_NAME_DISPLAY_NAME_ID,
                    l -> l.displayName.equals(displayName));
        } else {
            return getListIdByFilter(client, siteId, l -> l.displayName.equals(displayName),
                    SELECT_NAME_DISPLAY_NAME_ID, null);
        }
    }

    /**
     * Get a list by name by checking that the given property matches the given
     * value. This method will throw an exception if only a system list was found.
     *
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteId
     *            the ID of the SharePoint site
     * @param filter
     *            predicate used to select list whose id to return
     * @param select
     *            a string to pass as a select query to allow to select only
     *            required fields. This may lead to performance boosts.
     * @param lastResortFilter
     *            predicate used to select list whose id to return if no list
     *            matched the normal filter. May be {@code null}.
     * @throws IOException
     *             if an error happened during the request
     */
    private static Optional<String> getListIdByFilter(final GraphServiceClient<Request> client, final String siteId,
            final Predicate<List> filter, final String select, final Predicate<List> lastResortFilter)
            throws IOException {
        var result = Optional.<String>empty();
        final var lists = new LinkedList<List>();
        try {
            var nextRequest = client.sites(siteId).lists();

            while (nextRequest != null) {
                final var resp = nextRequest
                        .buildRequest(java.util.List.of(new QueryOption("select", "id,system," + select))).get();
                result = resp.getCurrentPage().stream() //
                        .filter(filter).filter(l -> l.system == null) //
                        .findFirst().map(l -> l.id);
                if (result.isPresent()) {
                    return result;
                }
                lists.addAll(resp.getCurrentPage());
                nextRequest = resp.getNextPage();
            }

            // no last resort; check for system list
            if (lastResortFilter == null && lists.stream().anyMatch(filter)) {
                throw new IOException(SYSTEM_LIST_COLLISION_MSG);
            } else if (lastResortFilter == null) {
                return Optional.empty();
            }

            // last resort
            result = lists.stream() //
                    .filter(lastResortFilter).filter(l -> l.system == null) //
                    .findFirst().map(l -> l.id);
            if (result.isEmpty() && lists.stream().anyMatch(filter.or(lastResortFilter))) {
                throw new IOException(SYSTEM_LIST_COLLISION_MSG);
            }
            return result;
        } catch (GraphServiceException ex) {
            throw new IOException("Error during list ID retrieval: " + ex.getServiceError().message, ex);
        }
    }

    /**
     * Get an list id using a direct property query filter. This is only possible
     * for certain properties such as "displayName" but not for others like "name"
     * because SharePoint. This method will throw an exception if only a system list
     * was found.
     */
    private static Optional<String> getListIdByFilter(final GraphServiceClient<Request> client, final String siteId,
            final String property, final String value) throws IOException {
        try {
            var nextRequest = client.sites(siteId).lists();
            var matchedSystemLists = false;
            while (nextRequest != null) {
                final var resp = nextRequest.buildRequest(getFilter(property, value)).get();
                matchedSystemLists |= !resp.getCurrentPage().isEmpty();
                final var result = resp.getCurrentPage().stream() //
                        .filter(l -> l.system == null).map(l -> l.id).findFirst();
                if (result.isPresent()) {
                    return result;
                }

                nextRequest = resp.getNextPage();
            }

            if (matchedSystemLists) {
                throw new IOException(SYSTEM_LIST_COLLISION_MSG);
            } else {
                return Optional.empty();
            }
        } catch (GraphServiceException ex) {
            throw new IOException("Error during list ID retrieval: " + ex.getServiceError().message, ex);
        }
    }

    private static java.util.List<Option> getFilter(final String key, final String value) {
        return java.util.List.of( //
                new QueryOption("select", "id,system," + key), //
                new QueryOption("filter", key + " eq '%s'".formatted(value.replace("'", "''"))));
    }
}
