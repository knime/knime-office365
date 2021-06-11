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
 *   2020-05-17 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.fs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.knime.filehandling.core.connections.base.attributes.BaseFileAttributes;
import org.knime.filehandling.core.connections.base.attributes.PosixAttributes;
import org.knime.filehandling.core.util.CheckedExceptionFunction;

import com.microsoft.graph.models.extensions.DriveItem;

/**
 * Sharepoint implementation of the {@link BaseFileAttributes}.
 *
 * @author Alexander Bondaletov
 */
class SharepointFileAttributes extends BaseFileAttributes {

    private final DriveItem m_driveItem;

    /**
     * @param fileKey
     *            The Path of the file.
     * @param driveItem
     *            The {@link DriveItem} corresponding to the file.
     * @param posixAttributesFunction
     *            The function to fetch POSIX metadata.
     */
    public SharepointFileAttributes(final Path fileKey, final DriveItem driveItem,
            final CheckedExceptionFunction<Path, PosixAttributes, IOException> posixAttributesFunction) {
        super(driveItem != null && driveItem.folder == null, //
                fileKey, //
                getLastModifiedTime(driveItem), //
                getLastModifiedTime(driveItem), //
                getCreationTime(driveItem), //
                driveItem == null || driveItem.size == null ? 0 : driveItem.size,
                false, false, posixAttributesFunction);
        m_driveItem = driveItem;
    }

    /**
     * @param fileKey
     *            The Path of the file.
     * @param driveItem
     *            The {@link DriveItem} corresponding to the file.
     */
    public SharepointFileAttributes(final Path fileKey, final DriveItem driveItem) {
        this(fileKey, driveItem, null);
    }

    /**
     * @return the driveItem
     */
    public DriveItem getDriveItem() {
        return m_driveItem;
    }

    private static FileTime getLastModifiedTime(final DriveItem item) {
        if (item == null) {
            return FileTime.fromMillis(0);
        }
        return FileTime.from(item.lastModifiedDateTime.toInstant());
    }

    private static FileTime getCreationTime(final DriveItem item) {
        if (item == null) {
            return FileTime.fromMillis(0);
        }
        return FileTime.from(item.createdDateTime.toInstant());
    }
}
