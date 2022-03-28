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
 *   Nov 14, 2020 (Tobias): created
 */
package org.knime.ext.sharepoint.lists.node.reader.framework;

import java.io.IOException;
import java.util.Iterator;
import java.util.OptionalLong;

import org.knime.ext.sharepoint.lists.node.SharepointListSettings;
import org.knime.filehandling.core.node.table.reader.randomaccess.AbstractRandomAccessible;
import org.knime.filehandling.core.node.table.reader.randomaccess.RandomAccessible;
import org.knime.filehandling.core.node.table.reader.read.Read;

/**
 * {@link Read} implementation that works with {@link SharepointListClient}s.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public class SharepointListRead implements Read<Object> {

    static class RandomAccessibleDataRow extends AbstractRandomAccessible<Object> {

        private final Object[] m_row;

        RandomAccessibleDataRow(final Object[] dataRow) {
            m_row = dataRow;
        }

        @Override
        public int size() {
            return m_row.length;
        }

        @Override
        public Object get(final int idx) {
            return m_row[idx];
        }

    }

    private long m_rowsRead = 0;

    private Iterator<RandomAccessibleDataRow> m_items;

    /**
     * Constructor.
     *
     * @param client
     *            the client to read from and use for requests
     * @param settings
     *            the {@link SharepointListSettings}
     * @throws IOException
     */
    public SharepointListRead(final SharepointListClient client, final SharepointListSettings settings)
            throws IOException {
        m_items = client.getItems(settings);
        m_rowsRead = 0;

    }

    @Override
    public RandomAccessible<Object> next() throws IOException {
        if (m_items.hasNext()) {
            m_rowsRead++;
            return m_items.next();
        } else {
            return null;
        }
    }

    @Override
    public OptionalLong getMaxProgress() {
        // TODO not found a solution yet to determine the no of rows via API
        return OptionalLong.empty();
    }

    @Override
    public long getProgress() {
        return m_rowsRead;
    }

    @Override
    public void close() throws IOException {
        // nothing to close
    }
}
