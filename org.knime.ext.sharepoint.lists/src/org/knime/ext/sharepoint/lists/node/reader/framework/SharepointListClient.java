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
 *   2021-09-24 (loescher): created
 */
package org.knime.ext.sharepoint.lists.node.reader.framework;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.knime.core.util.Pair;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.lists.node.SharepointListSettings;
import org.knime.ext.sharepoint.lists.node.SharepointListSettingsPanel.ListSettings;
import org.knime.ext.sharepoint.lists.node.reader.framework.SharepointListRead.RandomAccessibleDataRow;
import org.knime.ext.sharepoint.settings.SiteSettings;
import org.knime.ext.sharepoint.settings.TimeoutSettings;
import org.knime.filehandling.core.node.table.reader.randomaccess.RandomAccessible;

import com.google.gson.JsonElement;
import com.microsoft.graph.core.DefaultConnectionConfig;
import com.microsoft.graph.core.IConnectionConfig;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.extensions.IColumnDefinitionCollectionPage;
import com.microsoft.graph.requests.extensions.IListItemCollectionPage;

/**
 * A class used to setup and get information from the Microsoft Graph API.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointListClient {

    private static final List<Option> OPTIONS_ITEMS = Collections.singletonList(new QueryOption("expand", "fields"));

    private static final Set<String> DISALLOW_LIST = Set.of("linktitlenomenu", "linktitle");

    private static final Predicate<SharepointListColumn<?>> ALLOWED = c -> !DISALLOW_LIST.contains(c.getIdName());

    private static final Pair<Integer, SharepointListColumn<?>> MISSING = Pair.create(-1, null);

    private List<SharepointListColumn<?>> m_columns;

    private final IGraphServiceClient m_client;

    private String m_siteId;

    private String m_listId;

    private int m_settingsHash;

    private IConnectionConfig m_connectionConfig;

    private SiteSettings m_siteSettings;

    private ListSettings m_listSettings;

    /**
     * Create an object used to setup and get information from the Microsoft Graph
     * API.
     *
     * @param client
     *            the {@link IGraphServiceClient} used to make the API calls
     * @param settings
     *            the {@link SharepointListSettings} used to setup the client
     */
    public SharepointListClient(final IGraphServiceClient client, final SharepointListSettings settings) {
        m_client = client;
        m_connectionConfig = new DefaultConnectionConfig();
        updateSiteAndListSettings(settings);
        updateHttpConfig(settings.getTimeoutSettings());
    }

    /**
     * Closes this client and releases its resources.
     */
    public void close() {
        m_client.shutdown();
    }

    private void updateSiteAndListSettings(final SharepointListSettings settings) {
        m_siteSettings = settings.getSiteSettings();
        m_listSettings = settings.getListSettings();
    }

    private void updateHttpConfig(final TimeoutSettings settings) {
        m_connectionConfig.setConnectTimeout(toMilis(settings.getConnectionTimeout()));
        m_connectionConfig.setReadTimeout(toMilis(settings.getReadTimeout()));
        m_client.getHttpProvider().setConnectionConfig(m_connectionConfig);
    }

    private static int toMilis(final int duration) {
        return Math.toIntExact(Duration.ofSeconds(duration).toMillisPart());
    }

    /**
     * @param listSettings
     *            the {@link ListSettings} used to decide the accessed list
     * @return a {@link List} of {@link SharepointListColumn}.
     * @throws IOException
     *             if the columns couldn't be read (most likely due to the list not
     *             existing in the settings configuration)
     */
    public List<SharepointListColumn<?>> getColumns(final SharepointListSettings listSettings) throws IOException {
        setSiteAndListId(listSettings);
        updateHttpConfig(listSettings.getTimeoutSettings());

        // have the columns be read or the settings changed?
        if (m_columns == null || m_listSettings.hashCode() != m_settingsHash) {
            m_settingsHash = m_listSettings.hashCode();
            try {
                m_columns = StreamSupport
                        .stream(Spliterators.spliteratorUnknownSize(new ColumnIterator(),
                                Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL), false)//
                        .map(JsonElement::getAsJsonObject)//
                        .map(SharepointListColumn::of)//
                        .filter(ALLOWED)//
                        .collect(Collectors.toUnmodifiableList());
                // check for columns with duplicate display names and make the column names
                // unique if necessary
                final var columnNames = new LinkedHashMap<String, List<SharepointListColumn<?>>>();
                for (final var col : m_columns) {
                    columnNames.computeIfAbsent(col.getDisplayName(), k -> new LinkedList<>()).add(col);
                }
                columnNames.values().stream()//
                        .filter(l -> l.size() >= 2)//
                        .flatMap(List::stream)//
                        .forEach(SharepointListColumn::makeColumnNameUnique);
            } catch (GraphServiceException e) {
                throw new IOException("Could not read list: " + e.getError().error.message, e);
            }
        }

        return m_columns;
    }

    /**
     * Returns an {@link Iterator} of {@link RandomAccessibleDataRow}.
     *
     * @param settings
     *            the {@link SharepointListSettings} used to decide the accessed
     *            list
     * @return an iterator that allows iteration over all items in a list and
     *         returns them as {@link RandomAccessible}s
     * @throws IOException
     */
    public Iterator<RandomAccessibleDataRow> getItems(final SharepointListSettings settings) throws IOException {
        setSiteAndListId(settings);
        updateHttpConfig(settings.getTimeoutSettings());
        return new ItemIterator();
    }

    /**
     * Sets the site Id and the list Id based on the current settings.
     *
     * @param listSettings
     *            the {@link ListSettings} used to determine the site and list Id
     * @throws IOException
     */
    private void setSiteAndListId(final SharepointListSettings listSettings) throws IOException {
        updateSiteAndListSettings(listSettings);
        final var siteResolver = new SharepointSiteResolver(m_client, m_siteSettings.getMode(),
                m_siteSettings.getSubsiteModel().getStringValue(), m_siteSettings.getWebURLModel().getStringValue(),
                m_siteSettings.getGroupModel().getStringValue());
        m_siteId = siteResolver.getTargetSiteId();
        m_listId = m_listSettings.getListModel().getStringValue();

        if (m_listId.isEmpty()) {
            throw new IllegalStateException("Please select a list.");
        }
    }

    private class ColumnIterator implements Iterator<JsonElement> {

        private Iterator<JsonElement> m_current = Collections.emptyIterator();

        private IColumnDefinitionCollectionPage m_page = null;

        private boolean m_finishedRead = false;

        @Override
        public boolean hasNext() {
            if (m_finishedRead) {
                return false;
            } else if (!m_current.hasNext()) {
                makeNextRequest();
                if (m_finishedRead) {
                    return false;
                }
                m_current = m_page//
                        .getRawObject()//
                        .getAsJsonArray("value")//
                        .iterator();
            }
            return m_current.hasNext();
        }

        @Override
        public JsonElement next() {
            return m_current.next();
        }

        private void makeNextRequest() {
            if (m_page == null) {
                m_page = m_client//
                        .sites(m_siteId)//
                        .lists(m_listId)//
                        .columns()//
                        .buildRequest()//
                        .get();
            } else {
                final var req = m_page.getNextPage();
                if (req == null) {
                    m_page = null;
                    m_finishedRead = true;
                } else {
                    m_page = req.buildRequest().get();
                }
            }
        }
    }

    private class ItemIterator implements Iterator<RandomAccessibleDataRow> {

        private final Map<String, Pair<Integer, SharepointListColumn<?>>> m_idIndexMapping;

        private Iterator<JsonElement> m_itemSetIterator = Collections.emptyIterator();

        private IListItemCollectionPage m_page = null;

        private boolean m_finishedRead = false;

        public ItemIterator() throws IOException {
            m_idIndexMapping = createColumnAssignment();
        }

        @Override
        public boolean hasNext() {
            if (m_finishedRead) {
                return false;
            } else if (!m_itemSetIterator.hasNext()) {
                makeNextRequest();
                if (m_finishedRead) {
                    return false;
                }
                m_itemSetIterator = m_page.getRawObject().getAsJsonArray("value").iterator();
            }
            return m_itemSetIterator.hasNext();
        }

        @Override
        public RandomAccessibleDataRow next() {
            final var res = new Object[m_idIndexMapping.size()];
            final var properties = m_itemSetIterator.next()//
                    .getAsJsonObject()//
                    .get("fields")//
                    .getAsJsonObject()//
                    .entrySet();

            for (final var p : properties) {
                final var lookup = m_idIndexMapping.getOrDefault(p.getKey().toLowerCase(), MISSING);
                if (lookup != MISSING) {
                    res[lookup.getFirst()] = lookup.getSecond()// [columnIndex] = column
                            .getCanonicalRepresentation(p.getValue());
                }
            }

            return new RandomAccessibleDataRow(res);
        }

        private void makeNextRequest() {
            if (!m_finishedRead) {
                if (m_page == null) {
                    m_page = m_client.sites(m_siteId).lists(m_listId).items()//
                            .buildRequest(OPTIONS_ITEMS)//
                            .get();
                } else {
                    final var req = m_page.getNextPage();
                    if (req == null) {
                        m_page = null;
                        m_finishedRead = true;
                    } else {
                        m_page = req.buildRequest(OPTIONS_ITEMS).get();
                    }
                }
            }
        }

        private Map<String, Pair<Integer, SharepointListColumn<?>>> createColumnAssignment() {
            return IntStream//
                    .range(0, m_columns.size())//
                    .boxed()//
                    .collect(Collectors.toMap(i -> ((SharepointListColumn<?>) m_columns.get(i)).getIdName(),
                            i -> Pair.create(i, m_columns.get(i)), (a, b) -> b));
        }
    }

}
