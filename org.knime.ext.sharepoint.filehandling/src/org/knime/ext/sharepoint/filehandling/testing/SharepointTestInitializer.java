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
import java.nio.file.Path;
import java.util.Arrays;

import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.ext.sharepoint.filehandling.connections.SharepointConnection;
import org.knime.ext.sharepoint.filehandling.connections.SharepointFileSystem;
import org.knime.ext.sharepoint.filehandling.connections.SharepointPath;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.testing.FSTestInitializer;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.IDriveItemCollectionPage;

/**
 * Sharepoint test initializer.
 *
 * @author Alexander Bondaletov
 */
public class SharepointTestInitializer implements FSTestInitializer {

    private final SharepointConnection m_fsConnection;
    private final String m_drive;

    private final SharepointFileSystem m_filesystem;
    private final IGraphServiceClient m_client;
    private final String m_testFolder;

    /**
     * Creates new instance
     *
     * @param fsConnection
     *            {@link FSConnection} object.
     * @param drive
     *            Target drive name.
     * @param testFolder
     *            Test directory path.
     */
    public SharepointTestInitializer(final SharepointConnection fsConnection, final String drive,
            final String testFolder) {
        m_fsConnection = fsConnection;
        m_drive = drive;
        m_testFolder = testFolder;

        m_filesystem = (SharepointFileSystem) fsConnection.getFileSystem();
        m_client = m_filesystem.getClient();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FSConnection getFSConnection() {
        return m_fsConnection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path getRoot() {
        return m_filesystem.getPath("/", m_drive, m_testFolder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path createFile(final String... pathComponents) throws IOException {
        return createFileWithContent("", pathComponents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path createFileWithContent(final String content, final String... pathComponents) throws IOException {
        SharepointPath absoulutePath = //
                (SharepointPath) Arrays //
                        .stream(pathComponents) //
                        .reduce( //
                                getRoot(), //
                                (path, pathComponent) -> path.resolve(pathComponent), //
                                (p1, p2) -> p1.resolve(p2) //
                        ); //

        Files.createDirectories(absoulutePath.getParent());
        String parentId = absoulutePath.getParent().getDriveItem().id;

        String driveId = absoulutePath.getDriveId();
        String name = absoulutePath.getFileName().toString();

        try {
            m_client.drives(driveId).items(parentId).itemWithPath(SharepointPath.toUrlString(name)).content()
                    .buildRequest().put(content.getBytes());
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }

        return absoulutePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterTestCase() throws IOException {
        SharepointPath root = (SharepointPath) getRoot();

        try {
            IDriveItemCollectionPage childred = m_client.drives(root.getDriveId()).root()
                    .itemWithPath(SharepointPath.toUrlString(root.getItemPath())).children().buildRequest().get();

            for (DriveItem item : childred.getCurrentPage()) {
                m_client.drives(root.getDriveId()).items(item.id).buildRequest().delete();
            }

            while (childred.getNextPage() != null) {
                childred = childred.getNextPage().buildRequest().get();
                for (DriveItem item : childred.getCurrentPage()) {
                    m_client.drives(root.getDriveId()).items(item.id).buildRequest().delete();
                }
            }
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }

        m_filesystem.clearAttributesCache();
    }
}
