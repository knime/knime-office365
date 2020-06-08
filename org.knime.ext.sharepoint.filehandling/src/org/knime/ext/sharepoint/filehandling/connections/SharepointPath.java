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
 *   2020-05-03 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.connections;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.filehandling.core.connections.base.UnixStylePath;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.IDriveItemRequestBuilder;

/**
 * {@link Path} implementation for the {@link SharepointFileSystem}.
 *
 * @author Alexander Bondaletov
 */
public class SharepointPath extends UnixStylePath {

    /**
     * @param fileSystem
     *            The file system.
     * @param first
     *            The first name component.
     * @param more
     *            More name components. the string representation of the path.
     */
    public SharepointPath(final SharepointFileSystem fileSystem, final String first, final String[] more) {
        super(fileSystem, first, more);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SharepointFileSystem getFileSystem() {
        return (SharepointFileSystem) super.getFileSystem();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SharepointPath getParent() {
        return (SharepointPath) super.getParent();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    public Path toAbsolutePath() {
        return getFileSystem().getWorkingDirectory().resolve(this);
    }

    /**
     * @return The drive name. May be null.
     */
    public String getDriveName() {
        if (!isAbsolute()) {
            throw new IllegalStateException("Drive name cannot be determined for relative paths.");
        }
        if (m_pathParts.isEmpty()) {
            return null;
        }
        return m_pathParts.get(0);
    }

    /**
     * @return The driveId for a current path. May be null.
     */
    @SuppressWarnings("resource")
    public String getDriveId() {
        return getFileSystem().getDriveId(getDriveName());
    }

    /**
     * @return the drive item path. May be null
     */
    public String getItemPath() {
        if (!isAbsolute()) {
            throw new IllegalStateException("Blob name cannot be determined for relative paths.");
        }
        if (m_pathParts.size() <= 1) {
            return null;
        } else {
            return subpath(1, getNameCount()).toString();
        }
    }

    /**
     * Fetches {@link DriveItem} corresponding to the path. May be null if item
     * doesn't exist.
     *
     * @return The {@link DriveItem}
     * @throws IOException
     */
    @SuppressWarnings("resource")
    public DriveItem fetchDriveItem() throws IOException {
        IGraphServiceClient client = getFileSystem().getClient();
        String driveId = getDriveId();

        if (driveId == null) {
            return null;
        }

        IDriveItemRequestBuilder req = client.drives(driveId).root();

        String itemPath = getItemPath();
        if (itemPath != null) {
            req = req.itemWithPath(toUrlString(itemPath));
        }

        try {
            return req.buildRequest().get();
        } catch (GraphServiceException e) {
            if (e.getResponseCode() == 404) {
                return null;
            }
            throw GraphApiUtil.unwrapIOE(e);
        }
    }

    /**
     * Returns {@link DriveItem} corresponding to the path. Makes an attempt to
     * fetch it from the attribute cache first. Updates attributes cache if new
     * drive item was fetched.
     *
     * @return Drive item or null if the file doesn't exist.
     * @throws IOException
     */
    public DriveItem getDriveItem() throws IOException {
        return getDriveItem(false);
    }

    /**
     * Returns {@link DriveItem} corresponding to the path. Makes an attempt to
     * fetch it from the attribute cache if the <code>force</code> flag is set to
     * <code>false</code>. Updates attributes cache if new drive item was fetched.
     *
     * @param force
     *            When set to <code>true</code> new drive item will be loaded
     *            without trying to fetch it from the cache.
     *
     * @return Drive item or null if the file doesn't exist.
     * @throws IOException
     */
    @SuppressWarnings("resource")
    public DriveItem getDriveItem(final boolean force) throws IOException {
        DriveItem item = null;

        if (!force) {
            item = getFileSystem().getCachedAttributes(this).filter(attrs -> attrs instanceof SharepointFileAttributes)
                    .map(attrs -> ((SharepointFileAttributes) attrs).getDriveItem()).orElse(null);
        }

        if (item == null && getNameCount() > 0) {
            item = fetchDriveItem();
            if (item != null) {
                getFileSystem().addToAttributeCache(this, new SharepointFileAttributes(this, item));
            }
        }

        return item;
    }

    /**
     * Converts provided string into url-encoded format expected by msgraph API
     *
     * @param str
     *            String to convert.
     * @return Converted string.
     */
    public static String toUrlString(final String str) {
        try {
            return new URI(null, str, null).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);// Should not happen
        }
    }
}
