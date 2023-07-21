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
package org.knime.ext.sharepoint.filehandling.fs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;

import org.knime.ext.sharepoint.filehandling.FSGraphApiUtil;
import org.knime.filehandling.core.connections.base.TempFileSeekableByteChannel;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCreateUploadSessionParameterSet;
import com.microsoft.graph.models.DriveItemUploadableProperties;
import com.microsoft.graph.models.UploadSession;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.tasks.LargeFileUploadTask;

import okhttp3.Request;

/**
 * Sharepoint implementation of {@link TempFileSeekableByteChannel}.
 *
 * @author Alexander Bondaletov
 */
class SharepointSeekableByteChannel extends TempFileSeekableByteChannel<SharepointPath> {

    private static final int SIMPLE_UPLOAD_LIMIT = 4 * 1024 * 1024;

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

    @Override
    public void copyFromRemote(final SharepointPath remoteFile, final Path tempFile) throws IOException {
        Files.copy(remoteFile, tempFile);
    }

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
        GraphServiceClient<Request> client = remoteFile.getFileSystem().getClient();
        final var parentId = remoteFile.getParent().getDriveItem().id;
        final var filename = remoteFile.getFileName().toString();
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
            throw FSGraphApiUtil.unwrapClientEx(ex);
        }
    }

    @SuppressWarnings("resource")
    private static void uploadLarge(final SharepointPath remoteFile, final Path tempFile) throws IOException {
        GraphServiceClient<Request> client = remoteFile.getFileSystem().getClient();
        final var parentId = remoteFile.getParent().getDriveItem().id;
        final var filename = remoteFile.getFileName().toString();

        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(tempFile))) {
            UploadSession session = client//
                    .drives(remoteFile.getDriveId())//
                    .items(parentId)//
                    .itemWithPath(filename)//
                    .createUploadSession(DriveItemCreateUploadSessionParameterSet.newBuilder()//
                                    .withItem(new DriveItemUploadableProperties()).build())//
                    .buildRequest()//
                    .post();

            LargeFileUploadTask<DriveItem> uploadProvider = new LargeFileUploadTask<>(session, client, inputStream,
                    Files.size(tempFile), DriveItem.class);

            uploadProvider.upload();
        } catch (ClientException ex) {
            throw FSGraphApiUtil.unwrapClientEx(ex);
        }
    }
}
