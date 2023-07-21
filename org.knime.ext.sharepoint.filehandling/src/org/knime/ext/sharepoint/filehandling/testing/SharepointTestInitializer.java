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
package org.knime.ext.sharepoint.filehandling.testing;

import java.io.IOException;
import java.nio.file.Files;

import org.knime.ext.sharepoint.filehandling.FSGraphApiUtil;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnection;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFileSystem;
import org.knime.ext.sharepoint.filehandling.fs.SharepointPath;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.testing.DefaultFSTestInitializer;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Sharepoint test initializer.
 *
 * @author Alexander Bondaletov
 */
public class SharepointTestInitializer extends DefaultFSTestInitializer<SharepointPath, SharepointFileSystem> {

    private final GraphServiceClient<Request> m_client;

    private boolean m_workingDirExists = false;

    /**
     * Creates new instance
     *
     * @param fsConnection
     *            {@link FSConnection} object.
     */
    public SharepointTestInitializer(final SharepointFSConnection fsConnection) {
        super(fsConnection);
        m_client = getFileSystem().getClient();
    }



    @Override
    public SharepointPath createFileWithContent(final String content, final String... pathComponents)
            throws IOException {

        final SharepointPath path = makePath(pathComponents);

        Files.createDirectories(path.getParent());
        String parentId = path.getParent().getDriveItem().id;

        String driveId = path.getDriveId();
        String name = path.getFileName().toString();

        try {
            m_client.drives(driveId).items(parentId).itemWithPath(name).content()
                    .buildRequest().put(content.getBytes());
        } catch (ClientException ex) {
            throw FSGraphApiUtil.unwrapClientEx(ex);
        }

        return path;
    }


    @Override
    protected void beforeTestCaseInternal() throws IOException {
        final SharepointPath scratchDir = getTestCaseScratchDir();

        if (!m_workingDirExists) {
            Files.createDirectories(scratchDir.getParent());
            m_workingDirExists = true;
        }

        Files.createDirectory(scratchDir);
    }

    @Override
    protected void afterTestCaseInternal() throws IOException {
        final SharepointPath scratchDir = getTestCaseScratchDir();

        try {
            DriveItemCollectionPage children = m_client.drives(scratchDir.getDriveId()).root()
                    .itemWithPath(scratchDir.getItemPath()).children().buildRequest().get();

            for (DriveItem item : children.getCurrentPage()) {
                m_client.drives(scratchDir.getDriveId()).items(item.id).buildRequest().delete();
            }

            while (children.getNextPage() != null) {
                children = children.getNextPage().buildRequest().get();
                for (DriveItem item : children.getCurrentPage()) {
                    m_client.drives(scratchDir.getDriveId()).items(item.id).buildRequest().delete();
                }
            }

            m_client.drives(scratchDir.getDriveId()).items(scratchDir.getDriveItem().id).buildRequest().delete();
        } catch (ClientException ex) {
            throw FSGraphApiUtil.unwrapClientEx(ex);
        }

        getFileSystem().clearAttributesCache();
    }

    @Override
    public void afterClass() throws IOException {
        final SharepointPath scratchDir = getTestCaseScratchDir();

        if (m_workingDirExists) {
            try {
                FSFiles.deleteRecursively(scratchDir.getParent());
            } finally {
                m_workingDirExists = false;
            }
        }
    }

}
