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
import java.util.Collections;

import org.knime.core.node.util.FileSystemBrowser;
import org.knime.ext.sharepoint.filehandling.nodes.connection.SharepointConnectionSettings;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.FSFileSystem;
import org.knime.filehandling.core.filechooser.NioFileSystemBrowser;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Sharepoint implementation of {@link FSConnection} interface.
 *
 * @author Alexander Bondaletov
 */
public class SharepointConnection implements FSConnection {

    private final SharepointFileSystem m_filesystem;
    private final long m_cacheTTL = 60000;

    /**
     * @param authProvider
     *            Authentication provider
     * @param settings
     *            Connection settings.
     * @throws URISyntaxException
     * @throws IOException
     *
     */
    public SharepointConnection(final IAuthenticationProvider authProvider,
            final SharepointConnectionSettings settings)
            throws IOException {
        URI uri = null;
        try {
            uri = new URI(SharepointFileSystem.FS_TYPE, "sharepoint", null, null);
        } catch (URISyntaxException ex) {
            // never happens
        }
        SharepointFileSystemProvider provider = new SharepointFileSystemProvider(authProvider, settings, m_cacheTTL);
        m_filesystem = provider.getOrCreateFileSystem(uri, Collections.emptyMap());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FSFileSystem<?> getFileSystem() {
        return m_filesystem;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileSystemBrowser getFileSystemBrowser() {
        return new NioFileSystemBrowser(this);
    }

}
