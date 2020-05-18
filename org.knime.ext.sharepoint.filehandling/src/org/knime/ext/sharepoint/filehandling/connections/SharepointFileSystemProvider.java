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
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.filehandling.core.connections.base.BaseFileSystemProvider;
import org.knime.filehandling.core.connections.base.attributes.BaseFileAttributes;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.models.extensions.Folder;
import com.microsoft.graph.models.extensions.IGraphServiceClient;

/**
 * File system provider for {@link SharepointFileSystem}.
 *
 * @author Alexander Bondaletov
 */
public class SharepointFileSystemProvider extends BaseFileSystemProvider<SharepointPath, SharepointFileSystem> {

    /**
     * Sharepoint URI scheme.
     */
    public static final String SCHEME = "azure-sharepoint";

    private final IAuthenticationProvider m_authProvider;
    private final String m_site;
    private final long m_cacheTTL;

    /**
     *
     * @param authProvider
     *            The authentication provider
     * @param site
     *            The site id or path in a form of
     *            <code>{hostname}:/{server-relative-path}</code>
     * @param cacheTTL
     *            The time to live for cached elements in milliseconds.
     *
     */
    public SharepointFileSystemProvider(final IAuthenticationProvider authProvider, final String site,
            final long cacheTTL) {
        m_authProvider = authProvider;
        m_site = site;
        m_cacheTTL = cacheTTL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SharepointFileSystem createFileSystem(final URI uri, final Map<String, ?> env) throws IOException {
        return new SharepointFileSystem(this, uri, m_cacheTTL, m_authProvider, m_site);
    }

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
    @Override
    protected void moveInternal(final SharepointPath source, final SharepointPath target, final CopyOption... options)
            throws IOException {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void copyInternal(final SharepointPath source, final SharepointPath target, final CopyOption... options)
            throws IOException {
        // TODO Auto-generated method stub

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
                client.drives(dir.getDriveId()).items(parentId).children().buildRequest().post(item);
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
    @Override
    protected void deleteInternal(final SharepointPath path) throws IOException {
        // TODO Auto-generated method stub

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScheme() {
        return SCHEME;
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
