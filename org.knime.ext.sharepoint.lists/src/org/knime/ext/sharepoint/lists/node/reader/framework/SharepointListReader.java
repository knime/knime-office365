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
 *   Feb 6, 2020 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.lists.node.reader.framework;

import java.io.IOException;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataType;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.streamable.RowInput;
import org.knime.ext.sharepoint.lists.node.reader.SharepointListReaderConfig;
import org.knime.filehandling.core.node.table.reader.GenericTableReader;
import org.knime.filehandling.core.node.table.reader.config.TableReadConfig;
import org.knime.filehandling.core.node.table.reader.read.Read;
import org.knime.filehandling.core.node.table.reader.read.ReadUtils;
import org.knime.filehandling.core.node.table.reader.spec.TypedReaderColumnSpec;
import org.knime.filehandling.core.node.table.reader.spec.TypedReaderTableSpec;

/**
 * {@link GenericTableReader} that reads {@link RowInput}s.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointListReader
        implements GenericTableReader<SharepointListClient, SharepointListReaderConfig, DataType, Object> {

    @Override
    @SuppressWarnings("resource") // closing the read is the responsibility of the caller
    public Read<Object> read(final SharepointListClient in, final TableReadConfig<SharepointListReaderConfig> config)
            throws IOException {
        final var read = new SharepointListRead(in, config.getReaderSpecificConfig().getSharepointListSettings());
        return decorateForReading(read, config);
    }

    @Override
    public TypedReaderTableSpec<DataType> readSpec(final SharepointListClient in,
            final TableReadConfig<SharepointListReaderConfig> config, final ExecutionMonitor exec) throws IOException {
        final var columnSpecs = in.getColumns(config.getReaderSpecificConfig().getSharepointListSettings()).stream()
                .map(SharepointListReader::getColumnSpec)//
                .collect(Collectors.toList());

        return new TypedReaderTableSpec<>(columnSpecs);
    }

    private static TypedReaderColumnSpec<DataType> getColumnSpec(final SharepointListColumn<?> column) {
        return TypedReaderColumnSpec.createWithName(column.getColumnName(), column.getCanonicalType(), true);
    }

    @Override
    public DataColumnSpec createIdentifierColumnSpec(final SharepointListClient item, final String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataCell createIdentifierCell(final SharepointListClient item) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a decorated {@link Read} from {@link SharepointListRead}, taking into
     * account how many rows should be skipped or what is the maximum number of rows
     * to read.
     *
     * @param read
     *            the path of the file to read
     * @param config
     *            the {@link TableReadConfig} used
     * @return a decorated read of type {@link Read}
     */
    @SuppressWarnings("resource") // closing the read is the responsibility of the caller
    private static Read<Object> decorateForReading(final SharepointListRead read,
            final TableReadConfig<SharepointListReaderConfig> config) {
        Read<Object> filtered = read;
        if (config.skipRows()) {
            final var numRowsToSkip = config.getNumRowsToSkip();
            filtered = ReadUtils.skip(filtered, numRowsToSkip);
        }
        if (config.limitRows()) {
            final var numRowsToKeep = config.getMaxRows();
            filtered = ReadUtils.limit(filtered, numRowsToKeep);
        }
        return filtered;
    }
}
