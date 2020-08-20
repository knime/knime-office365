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
 *   2020-05-05 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.fs;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.filehandling.core.connections.base.CloseablePathIterator;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.Drive;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.requests.extensions.IDriveItemCollectionPage;
import com.microsoft.graph.requests.extensions.IDriveItemRequestBuilder;

/**
 * Class to iterate through the files and folders in the path
 *
 * @author Alexander Bondaletov
 */
public abstract class SharepointPathIterator implements CloseablePathIterator<SharepointPath> {

    private final Filter<? super Path> m_filter;
    /**
     * File system instance
     */
    protected final SharepointFileSystem m_fs;

    private SharepointPath m_nextPath;

    /**
     * Creates {@link SharepointPathIterator} for a given path.
     *
     * @param path
     *            The path to iterate.
     * @param filter
     *            the filter.
     * @return The iterator.
     * @throws IOException
     */
    public static SharepointPathIterator create(final SharepointPath path, final Filter<? super Path> filter)
            throws IOException {
        if (path.getNameCount() == 0) {
            return new DriveIterator(path, filter);
        } else {
            return new DriveItemIterator(path, filter);
        }
    }

    /**
     * @param path
     *            The path to iterate.
     * @param filter
     *            The filter.
     *
     */
    public SharepointPathIterator(final SharepointPath path, final Filter<? super Path> filter) {
        m_filter = filter;
        m_fs = path.getFileSystem();
    }

    /**
     * Performs initialization by fetching the first path. Cannot be called in the
     * base class constructor, so has to be called in the derived classes
     * constructors.
     *
     * @throws IOException
     */
    protected void init() throws IOException {
        m_nextPath = getNextFilteredPath();
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return m_nextPath != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SharepointPath next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        SharepointPath current = m_nextPath;
        try {
            m_nextPath = getNextFilteredPath();
        } catch (IOException ex) {
            throw new DirectoryIteratorException(ex);
        }
        return current;
    }

    private SharepointPath getNextFilteredPath() throws IOException {
        SharepointPath next = getNextPath();
        while (next != null) {
            if (m_filter.accept(next)) {
                return next;
            }
            next = getNextPath();
        }
        return null;
    }

    /**
     * @return The next {@link SharepointPath}.
     * @throws IOException
     */
    protected abstract SharepointPath getNextPath() throws IOException;

    private static class DriveIterator extends SharepointPathIterator {

        private final Iterator<SharepointPath> m_iterator;

        /**
         * @param path
         *            The path to iterate.
         * @param filter
         *            The filter.
         * @throws IOException
         */
        public DriveIterator(final SharepointPath path, final Filter<? super Path> filter) throws IOException {
            super(path, filter);
            m_iterator = m_fs.getDrives().stream().map(this::createPath).iterator();
            init();
        }

        private SharepointPath createPath(final Drive drive) {
            SharepointPath path = m_fs.getPath(m_fs.getSeparator(), GraphApiUtil.escapeDriveName(drive.name));

            SharepointFileAttributes attrs = new SharepointFileAttributes(path, null);
            m_fs.addToAttributeCache(path, attrs);

            return path;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected SharepointPath getNextPath() {
            if (m_iterator.hasNext()) {
                return m_iterator.next();
            }
            return null;
        }

    }

    private static class DriveItemIterator extends SharepointPathIterator {

        private final SharepointPath m_path;
        private IDriveItemCollectionPage m_currentPage;
        private Iterator<DriveItem> m_iterator;

        /**
         * @param path
         *            The path to iterate.
         * @param filter
         *            The filter.
         * @throws IOException
         */
        public DriveItemIterator(final SharepointPath path, final Filter<? super Path> filter) throws IOException {
            super(path, filter);
            m_path = path;

            IDriveItemRequestBuilder req = m_fs.getClient().drives(m_path.getDriveId()).root();
            if (path.getItemPath() != null) {
                req = req.itemWithPath(SharepointPath.toUrlString(path.getItemPath()));
            }

            try {
                m_currentPage = req.children().buildRequest().get();
            } catch (ClientException e) {
                throw GraphApiUtil.unwrapIOE(e);
            }

            m_iterator = m_currentPage.getCurrentPage().iterator();

            init();
        }

        /**
         * {@inheritDoc}
         *
         * @throws IOException
         */
        @Override
        protected SharepointPath getNextPath() throws IOException {
            if (!m_iterator.hasNext() && m_currentPage.getNextPage() != null) {
                loadNextPage();
            }
            if (m_iterator.hasNext()) {
                return createPath(m_iterator.next());
            }
            return null;
        }

        private void loadNextPage() throws IOException {
            try {
                m_currentPage = m_currentPage.getNextPage().buildRequest().get();
            } catch (ClientException e) {
                throw GraphApiUtil.unwrapIOE(e);
            }
            m_iterator = m_currentPage.getCurrentPage().iterator();
        }

        private SharepointPath createPath(final DriveItem item) {
            SharepointPath path = m_fs.getPath(m_path.toString(), item.name);

            SharepointFileAttributes attrs = new SharepointFileAttributes(path, item);
            m_fs.addToAttributeCache(path, attrs);

            return path;
        }
    }
}
