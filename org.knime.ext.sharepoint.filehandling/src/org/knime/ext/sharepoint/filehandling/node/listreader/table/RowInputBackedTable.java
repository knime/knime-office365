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
 *   Nov 17, 2020 (Tobias): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader.table;

import java.util.NoSuchElementException;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.node.streamable.RowInput;

/**
 * {@link RowInput} backed Table implementation.
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
public class RowInputBackedTable implements Table {

    private static final class RowInputRowCursor extends CloseableRowIterator {

        private RowInput m_delegate;

        private DataRow m_nextRow;

        /**
         * @param rowInput
         */
        RowInputRowCursor(final RowInput delegate) {
            m_delegate = delegate;
            try {
                m_nextRow = m_delegate.poll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Data streaming was canceled", e);
            }
        }

        @Override
        public void close() {
            m_delegate.close();
        }

        @Override
        public boolean hasNext() {
            return m_nextRow != null;
        }

        @Override
        public DataRow next() {
            if (!hasNext()) {
                throw new NoSuchElementException("This iterator has no more rows.");
            }
            final DataRow currentRow = m_nextRow;
            try {
                m_nextRow = m_delegate.poll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Data streaming was canceled", e);
            }
            return currentRow;
        }

    }

    private final RowInput m_rowInput;

    /**
     * @param rowInput {@link RowInput} to use
     */
    public RowInputBackedTable(final RowInput rowInput) {
        m_rowInput = rowInput;
    }

    @Override
    public DataTableSpec getDataTableSpec() {
        return m_rowInput.getDataTableSpec();
    }
    @Override
    public CloseableRowIterator cursor() {
        return new RowInputRowCursor(m_rowInput);
    }

}
