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
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.knime.core.node.util.CheckUtils;
import org.knime.ext.microsoft.authentication.scopes.Scope;
import org.knime.ext.microsoft.authentication.util.testing.OAuth2TestAuthenticator;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnection;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnectionConfig;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSDescriptorProvider;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFileSystem;
import org.knime.ext.sharepoint.settings.SiteMode;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.meta.FSType;
import org.knime.filehandling.core.testing.DefaultFSTestInitializerProvider;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Initializer provider for Sharepoint.
 *
 * @author Alexander Bondaletov
 */
@SuppressWarnings("deprecation")
public class SharepointTestInitializerProvider extends DefaultFSTestInitializerProvider {

    @SuppressWarnings("resource")
    @Override
    public SharepointTestInitializer setup(final Map<String, String> configuration) throws IOException {

        validateConfiguration(configuration);

        final IAuthenticationProvider authProvider = authenticate(configuration);

        final String workingDir = generateRandomizedWorkingDir(configuration.get("workingDirPrefix"),
                SharepointFileSystem.PATH_SEPARATOR);

        final var fsConfig = new SharepointFSConnectionConfig(workingDir, authProvider);
        fsConfig.setMode(SiteMode.WEB_URL);
        fsConfig.setWebURL(getParameter(configuration, "siteWebURL"));
        fsConfig.setConnectionTimeOut(Duration.ofSeconds(SharepointFSConnectionConfig.DEFAULT_TIMEOUT));
        fsConfig.setReadTimeOut(Duration.ofSeconds(SharepointFSConnectionConfig.DEFAULT_TIMEOUT));

        final var fsConnection = new SharepointFSConnection(fsConfig);

        return new SharepointTestInitializer(fsConnection);
    }

    private static void validateConfiguration(final Map<String, String> configuration) {
        CheckUtils.checkArgumentNotNull(configuration.get("siteWebURL"), "siteWebURL must be specified.");
        CheckUtils.checkArgumentNotNull(configuration.get("workingDirPrefix"), "workingDirPrefix must be specified.");
        CheckUtils.checkArgumentNotNull(configuration.get("username"), "username must be specified.");
        CheckUtils.checkArgumentNotNull(configuration.get("password"), "password must be specified.");
    }

    private IAuthenticationProvider authenticate(final Map<String, String> config) throws IOException {

        final var credential = OAuth2TestAuthenticator.authenticateWithUsernamePassword(
                getParameter(config, "username"), //
                getParameter(config, "password"),//
                Set.of(Scope.SITES_READ_WRITE.getScope()));
        return GraphApiUtil.createAuthenticationProvider(credential);
    }

    @Override
    public FSType getFSType() {
        return SharepointFSDescriptorProvider.FS_TYPE;
    }

    @Override
    public FSLocationSpec createFSLocationSpec(final Map<String, String> configuration) {
        validateConfiguration(configuration);
        return SharepointFSDescriptorProvider.FS_LOCATION_SPEC;
    }
}
