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
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.ext.sharepoint.filehandling.node.SharepointConnectionSettings;
import org.knime.filehandling.core.connections.DefaultFSLocationSpec;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.base.BaseFileSystem;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.core.DefaultConnectionConfig;
import com.microsoft.graph.core.IConnectionConfig;
import com.microsoft.graph.http.CoreHttpProvider;
import com.microsoft.graph.httpcore.HttpClients;
import com.microsoft.graph.httpcore.ICoreAuthenticationProvider;
import com.microsoft.graph.logger.DefaultLogger;
import com.microsoft.graph.logger.LoggerLevel;
import com.microsoft.graph.models.extensions.Drive;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import com.microsoft.graph.requests.extensions.IDriveCollectionPage;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Sharepoint implementation of the {@link FileSystem}.
 *
 * @author Alexander Bondaletov
 */
public class SharepointFileSystem extends BaseFileSystem<SharepointPath> {

    /**
     * Sharepoint URI scheme.
     */
    public static final String FS_TYPE = "microsoft-sharepoint";

    /**
     * Character to use as path separator
     */
    public static final String PATH_SEPARATOR = "/";

    private final IGraphServiceClient m_client;
    private final String m_siteId;
    private final Map<String, Drive> m_drives;


    /**
     * @param fileSystemProvider
     *            The {@link SharepointFileSystemProvider}
     * @param uri
     *            The URI
     * @param cacheTTL
     *            The time to live for cached elements in milliseconds.
     * @param authProvider
     *            The authentication provider
     * @param settings
     *            Connection settings.
     * @throws IOException
     */
    public SharepointFileSystem(
            final URI uri,
            final long cacheTTL, final IAuthenticationProvider authProvider,
            final SharepointConnectionSettings settings) throws IOException {
        super(new SharepointFileSystemProvider(), uri, cacheTTL, settings.getWorkingDirectory(PATH_SEPARATOR),
                createFSLocationSpec());

        DefaultLogger logger = new DefaultLogger();
        logger.setLoggingLevel(LoggerLevel.ERROR);

        try {
            m_client = GraphServiceClient.builder().authenticationProvider(authProvider).logger(logger).buildClient();
            IConnectionConfig connConfig = new DefaultConnectionConfig();
            connConfig.setReadTimeout(settings.getReadTimeout() * 1000);
            connConfig.setConnectTimeout(settings.getConnectionTimeout() * 1000);
            m_client.getHttpProvider().setConnectionConfig(connConfig);

            patchHttpProvider();

            m_siteId = settings.getSiteId(m_client);
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }

        m_drives = new HashMap<>();
        fetchDrives();
    }

    /**
     * @return the {@link FSLocationSpec} for a Sharepoint file system.
     */
    public static DefaultFSLocationSpec createFSLocationSpec() {
        return new DefaultFSLocationSpec(FSCategory.CONNECTED, SharepointFileSystem.FS_TYPE);
    }

    /**
     * Workaround to address this bug
     * (https://github.com/microsoftgraph/msgraph-sdk-java/issues/313). I haven't
     * encountered this error during normal node usage, but it causes random unit
     * tests to fail on a regular basis.
     *
     * To fix this we replace corehttpClient field of the {@link CoreHttpProvider}
     * with a new one that has retryOnConnectionFailure option enabled.
     */
    private void patchHttpProvider() {
        if (m_client.getHttpProvider() instanceof CoreHttpProvider) {
            IConnectionConfig connectionConfig = m_client.getHttpProvider().getConnectionConfig();

            OkHttpClient.Builder okBuilder = HttpClients.createDefault(new ICoreAuthenticationProvider() {
                @Override
                public Request authenticateRequest(final Request request) {
                    return request;
                }
            }).newBuilder();
            okBuilder.connectTimeout(connectionConfig.getConnectTimeout(), TimeUnit.MILLISECONDS);
            okBuilder.readTimeout(connectionConfig.getReadTimeout(), TimeUnit.MILLISECONDS);
            okBuilder.followRedirects(false);
            okBuilder.retryOnConnectionFailure(true);// The only field that is different from the original
            OkHttpClient corehttpClient = okBuilder.build();

            try {
                Field field = CoreHttpProvider.class.getDeclaredField("corehttpClient");
                field.setAccessible(true);
                field.set(m_client.getHttpProvider(), corehttpClient);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {

            }
        }
    }

    private void fetchDrives() throws IOException {
        try {
            IDriveCollectionPage result = m_client.sites(m_siteId).drives().buildRequest().get();
            storeDrives(result.getCurrentPage());

            while (result.getNextPage() != null) {
                result = result.getNextPage().buildRequest().get();
                storeDrives(result.getCurrentPage());
            }
        } catch (ClientException ex) {
            throw GraphApiUtil.unwrapIOE(ex);
        }
    }

    private void storeDrives(final List<Drive> drives) {
        for (Drive drive : drives) {
            m_drives.put(GraphApiUtil.escapeDriveName(drive.name), drive);
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
    public IGraphServiceClient getClient() {
        return m_client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareClose() {
        m_client.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSchemeString() {
        return provider().getScheme();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHostString() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SharepointPath getPath(final String first, final String... more) {
        return new SharepointPath(this, first, more);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSeparator() {
        return PATH_SEPARATOR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(getPath(PATH_SEPARATOR));
    }

}
