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
package org.knime.ext.sharepoint.filehandling.fs;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.filehandling.FSGraphApiUtil;
import org.knime.filehandling.core.connections.base.BaseFileSystem;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.logger.DefaultLogger;
import com.microsoft.graph.logger.LoggerLevel;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.requests.DriveCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Sharepoint implementation of the {@link FileSystem}.
 *
 * @author Alexander Bondaletov
 */
public class SharepointFileSystem extends BaseFileSystem<SharepointPath> {

    /**
     * Character to use as path separator
     */
    public static final String PATH_SEPARATOR = "/";

    private final GraphServiceClient<Request> m_client;
    private final String m_siteId;
    private final Map<String, Drive> m_drives;
    private final SharepointFSConnectionConfig m_config;

    /**
     * @param config
     *            Connection configuration
     * @param cacheTTL
     *            The time to live for cached elements in milliseconds.
     * @throws IOException
     */

    public SharepointFileSystem(final SharepointFSConnectionConfig config, final long cacheTTL) throws IOException {
        super(new SharepointFileSystemProvider(), cacheTTL, config.getWorkingDirectory(),
                SharepointFSDescriptorProvider.FS_LOCATION_SPEC);

        DefaultLogger logger = new DefaultLogger();
        logger.setLoggingLevel(LoggerLevel.ERROR);
        m_config = config;
        try {
            int connectionTimeout = Math.toIntExact(m_config.getConnectionTimeOut().toMillis());
            int readTimeout = Math.toIntExact(m_config.getReadTimeOut().toMillis());
            m_client = GraphApiUtil.createClient(m_config.getAuthenticationProvider(), connectionTimeout, readTimeout);

            final var siteResolver = new SharepointSiteResolver(m_client, m_config.getMode(), m_config.getSubsite(),
                    m_config.getWebURL(), m_config.getGroup());
            m_siteId = siteResolver.getTargetSiteId();
        } catch (ClientException ex) {
            throw FSGraphApiUtil.unwrapClientEx(ex);
        }

        m_drives = new HashMap<>();
        fetchDrives();
    }

    private void fetchDrives() throws IOException {
        try {
            DriveCollectionPage result = m_client.sites(m_siteId).drives().buildRequest().get();
            storeDrives(result.getCurrentPage());

            while (result.getNextPage() != null) {
                result = result.getNextPage().buildRequest().get();
                storeDrives(result.getCurrentPage());
            }
        } catch (ClientException ex) {
            throw FSGraphApiUtil.unwrapClientEx(ex);
        }
    }

    private void storeDrives(final List<Drive> drives) {
        for (Drive drive : drives) {
            m_drives.put(FSGraphApiUtil.escapeDriveName(drive.name), drive);
        }
    }

    /**
     * @return the cached drives list for a current site.
     */
    public Collection<Drive> getDrives() {
        return m_drives.values();
    }

    /**
     * @param driveName
     *            The drive name.
     * @return The driveId for a given drive.
     */
    public String getDriveId(final String driveName) {
        Drive drive = m_drives.get(driveName);
        if (drive != null) {
            return drive.id;
        }
        return null;
    }

    /**
     * @return the client
     */
    public GraphServiceClient<Request> getClient() {
        return m_client;
    }

    @Override
    protected void prepareClose() {
        // Nothing to do
    }

    @Override
    public SharepointPath getPath(final String first, final String... more) {
        return new SharepointPath(this, first, more);
    }

    @Override
    public String getSeparator() {
        return PATH_SEPARATOR;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(getPath(PATH_SEPARATOR));
    }
}
