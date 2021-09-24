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
package org.knime.ext.sharepoint.filehandling.node.listreader.framework;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.knime.ext.sharepoint.filehandling.node.listreader.framework.SharepointListRead.RandomAccessibleDataRow;
import org.knime.filehandling.core.node.table.reader.randomaccess.RandomAccessible;

import com.google.gson.JsonElement;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.extensions.IListItemCollectionPage;

/**
 * A class used to setup get information from the Microsoft Graph API.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointListAccessor {
    static final String LIST_LOONG = "07540548-5b44-4430-a1ab-9de37a102e79";
    static final String LIST_SHORT = "d999e2ab-89ac-4611-b99e-20937224de3a";

    private final IGraphServiceClient m_client;

    private final String m_site = "root";

    private final String m_list = LIST_SHORT;
    static final List<Option> OPTIONS = Collections.singletonList(new QueryOption("expand", "fields"));
    static final List<Option> OPTIONS_COLUMNS_ITEMS = Collections.singletonList(new QueryOption("select", "name"));

    /**
     * Create an object used to setup get information from the Microsoft Graph API.
     *
     * @param client
     *            the client used to make the connections
     */
    public SharepointListAccessor(final IGraphServiceClient client) {
        m_client = client;
    }

    // TODO one can put multiple requests into one list. Maybe do that? (also cache
    // TODO stuff to avoid unnecessary requests?)

    /**
     * @return a {@link Stream} of the display names of the columns in the list
     */
    public Stream<String> getColumnsDisplayNames() {
        // TODO there could be multiple pages of columns!
        return StreamSupport
                .stream(m_client.sites(m_site).lists(m_list).columns().buildRequest(Collections.emptyList()).get()//
                        .getRawObject()//
                        .getAsJsonArray("value")//
                        .spliterator(), false)//
                .map(JsonElement::getAsJsonObject)//
                .map(o -> o.get("displayName"))//
                .map(JsonElement::getAsString);
    }

    /**
     * @return an iterator that allows iteration over all items in a list and
     *         returns them as {@link RandomAccessible}s
     */
    public Iterator<RandomAccessibleDataRow> getItems() {
        return new ItemIterator();
    }

    private class ItemIterator implements Iterator<RandomAccessibleDataRow> {

        private IListItemCollectionPage m_page = null;

        private boolean m_finishedRead = false;

        private Iterator<JsonElement> m_itemSetIterator = Collections.emptyIterator();

        private final Map<String, Integer> m_idIndexMapping = new LinkedHashMap<>();

        private void makeNextRequest() {
            if (!m_finishedRead) {
                if (m_page == null) { // first read
                    // TODO there could be multiple pages of columns!
                    // there seems to be a way to do multiple requests in batch but there is no Java
                    // interface for that
                    // read columns for assigning correctly
                    final var columns = m_client.sites(m_site).lists(m_list).columns()
                            .buildRequest(OPTIONS_COLUMNS_ITEMS).get()//
                            .getRawObject()//
                            .get("value")//
                            .getAsJsonArray();

                    final var iter = columns.iterator();
                    var idx = 0;
                    while (iter.hasNext()) {
                        m_idIndexMapping.put(iter.next()//
                                .getAsJsonObject()//
                                .get("name")//
                                .getAsString(), idx);
                        idx++;
                    }
                    // read the first items
                    m_page = m_client.sites(m_site).lists(m_list).items().buildRequest(OPTIONS).get();
                } else {
                    final var req = m_page.getNextPage();
                    if (req == null) {
                        m_page = null;
                        m_finishedRead = true;
                    } else {
                        m_page = req.buildRequest(OPTIONS).get();
                    }
                }
            }
        }

        /**
         * {@inheritDoc}
         */
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
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RandomAccessibleDataRow next() {
            final var res = new String[m_idIndexMapping.size()];
            final var properties = m_itemSetIterator.next()//
                    .getAsJsonObject()//
                    .get("fields")//
                    .getAsJsonObject().entrySet();
            for (final var e : properties) {
                final var idx = m_idIndexMapping.getOrDefault(e.getKey(), -1);
                if (idx != -1) {
                    res[idx] = e.getValue().toString();
                }
            }

            return new RandomAccessibleDataRow(res);
        }

    }
}
