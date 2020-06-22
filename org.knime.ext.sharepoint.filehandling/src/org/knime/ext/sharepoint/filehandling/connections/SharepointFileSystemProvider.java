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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.filehandling.core.connections.base.BaseFileSystemProvider;
import org.knime.filehandling.core.connections.base.attributes.BaseFileAttributes;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.models.extensions.Folder;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.ItemReference;

/**
 * File system provider for {@link SharepointFileSystem}.
 *
 * @author Alexander Bondaletov
 */
public class SharepointFileSystemProvider extends BaseFileSystemProvider<SharepointPath, SharepointFileSystem> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected SeekableByteChannel newByteChannelInternal(final SharepointPath path, final Set<? extends OpenOption> options,
            final FileAttribute<?>... attrs) throws IOException {
        return new SharepointSeekableByteChannel(path, options);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    protected void moveInternal(final SharepointPath source, final SharepointPath target, final CopyOption... options)
            throws IOException {
        IGraphServiceClient client = source.getFileSystem().getClient();
        DriveItem targetItem = target.getDriveItem(true);

        if (targetItem != null) {
            if (targetItem.folder != null && targetItem.folder.childCount > 0) {
                throw new DirectoryNotEmptyException(
                        String.format("Target directory %s exists and is not empty", target.toString()));
            }

            delete(target);
        }

        DriveItem sourceItem = source.getDriveItem(true);
        DriveItem targetParent = target.getParent().getDriveItem();

        ItemReference parentRef = new ItemReference();
        parentRef.id = targetParent.id;

        targetItem = new DriveItem();
        targetItem.parentReference = parentRef;
        targetItem.name = target.getFileName().toString();

        try {
            DriveItem resultItem = client.drives(source.getDriveId()).items(sourceItem.id).buildRequest()
                    .patch(targetItem);

            if (sourceItem.folder == null || sourceItem.folder.childCount == 0) {
                getFileSystemInternal().removeFromAttributeCache(source);
            } else {
                getFileSystemInternal().removeFromAttributeCacheDeep(source);
            }

            if (resultItem != null) {
                getFileSystemInternal().addToAttributeCache(target, new SharepointFileAttributes(target, resultItem));
            }
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    protected void copyInternal(final SharepointPath source, final SharepointPath target, final CopyOption... options)
            throws IOException {
        IGraphServiceClient client = source.getFileSystem().getClient();
        DriveItem targetItem = target.getDriveItem(true);

        if (targetItem != null) {
            if (targetItem.folder != null && targetItem.folder.childCount > 0) {
                throw new DirectoryNotEmptyException(
                        String.format("Target directory %s exists and is not empty", target.toString()));
            }

            delete(target);
        }

        DriveItem sourceItem = source.getDriveItem();
        DriveItem targetParent = target.getParent().getDriveItem();

        ItemReference parentRef = new ItemReference();
        parentRef.driveId = target.getDriveId();
        parentRef.id = targetParent.id;
        String name = target.getFileName().toString();

        try {
            client.drives(source.getDriveId()).items(sourceItem.id).copy(name, parentRef)
                    .buildRequest().post();
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    protected InputStream newInputStreamInternal(final SharepointPath path, final OpenOption... options) throws IOException {
        IGraphServiceClient client = path.getFileSystem().getClient();
        DriveItem item = path.getDriveItem();

        if (item == null) {
            throw new NoSuchFileException(path.toString());
        }

        try {
            return client.drives(path.getDriveId()).items(item.id).content().buildRequest().get();
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected OutputStream newOutputStreamInternal(final SharepointPath path, final OpenOption... options) throws IOException {
        final Set<OpenOption> opts = new HashSet<>(Arrays.asList(options));
        return Channels.newOutputStream(newByteChannel(path, opts));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Iterator<SharepointPath> createPathIterator(final SharepointPath dir, final Filter<? super Path> filter)
            throws IOException {
        return SharepointPathIterator.create(dir, filter);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    protected void createDirectoryInternal(final SharepointPath dir, final FileAttribute<?>... attrs) throws IOException {
        IGraphServiceClient client = dir.getFileSystem().getClient();
        if (dir.getItemPath() != null) {
            String parentId = dir.getParent().getDriveItem().id;

            DriveItem item = new DriveItem();
            item.name = dir.getFileName().toString();
            item.folder = new Folder();

            try {
                DriveItem resultItem = client.drives(dir.getDriveId()).items(parentId).children().buildRequest()
                        .post(item);

                if (resultItem != null) {
                    getFileSystemInternal().addToAttributeCache(dir, new SharepointFileAttributes(dir, resultItem));
                }
            } catch (ClientException e) {
                throw GraphApiUtil.unwrapIOE(e);
            }
        } else {
            throw new UnsupportedOperationException("Cannot create drive");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean exists(final SharepointPath path) throws IOException {
        if (path.getDriveName() == null) {// Virtual root
            return true;
        }

        if (path.getItemPath() == null) {
            // Drive exists if present in drives cache
            return path.getDriveId() != null;
        }

        return path.fetchDriveItem() != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BaseFileAttributes fetchAttributesInternal(final SharepointPath path, final Class<?> type) throws IOException {
        DriveItem item = null;
        if (path.getNameCount() > 0) {
            item = path.fetchDriveItem();
        }
        return new SharepointFileAttributes(path, item);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkAccessInternal(final SharepointPath path, final AccessMode... modes) throws IOException {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    protected void deleteInternal(final SharepointPath path) throws IOException {
        IGraphServiceClient client = path.getFileSystem().getClient();
        DriveItem item = path.getDriveItem(true);

        if (item.folder != null && item.folder.childCount > 0) {
            throw new DirectoryNotEmptyException(path.toString());
        }

        try {
            client.drives(path.getDriveId()).items(item.id).buildRequest().delete();
        } catch (ClientException ex) {
            GraphApiUtil.unwrapIOE(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScheme() {
        return SharepointFileSystem.FS_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHidden(final Path path) throws IOException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    public FileStore getFileStore(final Path path) throws IOException {
        return path.getFileSystem().getFileStores().iterator().next();
    }

}
