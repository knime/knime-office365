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
 *   2020-05-14 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.connections;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;

import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.filehandling.core.connections.base.TempFileSeekableByteChannel;

import com.microsoft.graph.concurrency.ChunkedUploadProvider;
import com.microsoft.graph.concurrency.IProgressCallback;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.models.extensions.DriveItemUploadableProperties;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.UploadSession;

/**
 * Sharepoint implementation of {@link TempFileSeekableByteChannel}.
 *
 * @author Alexander Bondaletov
 */
public class SharepointSeekableByteChannel extends TempFileSeekableByteChannel<SharepointPath> {

    private static final int SIMPLE_UPLOAD_LIMIT = 4 * 1024 * 1024;

    private ClientException m_lastException;
    private DriveItem m_lastDriveItem;

    /**
     * Creates new instance.
     *
     * @param file
     *            The file for the channel.
     * @param options
     *            Open options.
     * @throws IOException
     */
    public SharepointSeekableByteChannel(final SharepointPath file, final Set<? extends OpenOption> options)
            throws IOException {
        super(file, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void copyFromRemote(final SharepointPath remoteFile, final Path tempFile) throws IOException {
        Files.copy(remoteFile, tempFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void copyToRemote(final SharepointPath remoteFile, final Path tempFile) throws IOException {
        if (Files.size(tempFile) < SIMPLE_UPLOAD_LIMIT) {
            uploadSimple(remoteFile, tempFile);
        } else {
            uploadLarge(remoteFile, tempFile);
        }
    }

    @SuppressWarnings("resource")
    private static void uploadSimple(final SharepointPath remoteFile, final Path tempFile) throws IOException {
        IGraphServiceClient client = remoteFile.getFileSystem().getClient();
        String parentId = remoteFile.getParent().getDriveItem().id;
        String filename = SharepointPath.toUrlString(remoteFile.getFileName().toString());
        byte[] bytes = Files.readAllBytes(tempFile);

        try {
            DriveItem item = client.drives(remoteFile.getDriveId())//
                    .items(parentId)//
                    .itemWithPath(filename)//
                    .content()//
                    .buildRequest()//
                    .put(bytes);

            if (item != null) {
                remoteFile.getFileSystem().addToAttributeCache(remoteFile,
                        new SharepointFileAttributes(remoteFile, item));
            }
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }
    }

    @SuppressWarnings("resource")
    private void uploadLarge(final SharepointPath remoteFile, final Path tempFile) throws IOException {
        IGraphServiceClient client = remoteFile.getFileSystem().getClient();
        String parentId = remoteFile.getParent().getDriveItem().id;
        String filename = SharepointPath.toUrlString(remoteFile.getFileName().toString());

        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(tempFile))) {
            UploadSession session = client//
                    .drives(remoteFile.getDriveId())//
                    .items(parentId)//
                    .itemWithPath(filename)//
                    .createUploadSession(
                            new DriveItemUploadableProperties())//
                    .buildRequest()//
                    .post();

            ChunkedUploadProvider<DriveItem> uploadProvider = new ChunkedUploadProvider<>(session, client, inputStream,
                    Files.size(tempFile), DriveItem.class);

            m_lastException = null;
            m_lastDriveItem = null;

            uploadProvider.upload(createProgressCallback());

            if (m_lastException != null) {
                throw m_lastException;
            }

            if (m_lastDriveItem != null) {
                remoteFile.getFileSystem().addToAttributeCache(remoteFile,
                        new SharepointFileAttributes(remoteFile, m_lastDriveItem));
            }
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(m_lastException);
        }
    }

    private IProgressCallback<DriveItem> createProgressCallback() {
        return new IProgressCallback<DriveItem>() {

            @Override
            public void success(final DriveItem item) {
                m_lastDriveItem = item;
            }

            @Override
            public void failure(final ClientException e) {
                m_lastException = e;
            }

            @Override
            public void progress(final long current, final long max) {
                // ignore
            }
        };
    }
}
