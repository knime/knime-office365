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
import java.util.Map;

import org.knime.core.node.util.CheckUtils;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.microsoft.authentication.port.oauth2.testing.OAuth2TestAuthenticator;
import org.knime.ext.sharepoint.filehandling.GraphApiAuthenticationProvider;
import org.knime.ext.sharepoint.filehandling.fs.SharepointConnection;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFileSystem;
import org.knime.ext.sharepoint.filehandling.node.SharepointConnectionSettings;
import org.knime.ext.sharepoint.filehandling.node.SharepointConnectionSettings.SiteMode;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.testing.DefaultFSTestInitializerProvider;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Initializer provider for Sharepoint.
 *
 * @author Alexander Bondaletov
 */
public class SharepointTestInitializerProvider extends DefaultFSTestInitializerProvider {

    @Override
    public SharepointTestInitializer setup(final Map<String, String> configuration) throws IOException {

        validateConfiguration(configuration);

        final IAuthenticationProvider authProvider = authenticate(configuration);

        final String workingDir = generateRandomizedWorkingDir(configuration.get("workingDirPrefix"),
                SharepointFileSystem.PATH_SEPARATOR);

        final SharepointConnectionSettings settings = new SharepointConnectionSettings();
        settings.getSiteSettings().getModeModel().setStringValue(SiteMode.WEB_URL.toString());
        settings.getSiteSettings().getWebURLModel().setStringValue(configuration.get("siteWebURL"));
        settings.getWorkingDirectoryModel().setStringValue(workingDir);

        final SharepointConnection fsConnection = new SharepointConnection(authProvider, settings);

        return new SharepointTestInitializer(fsConnection);
    }

    private static void validateConfiguration(final Map<String, String> configuration) {
        CheckUtils.checkArgumentNotNull(configuration.get("siteWebURL"), "siteWebURL must be specified.");
        CheckUtils.checkArgumentNotNull(configuration.get("workingDirPrefix"), "workingDirPrefix must be specified.");
        CheckUtils.checkArgumentNotNull(configuration.get("username"), "username must be specified.");
        CheckUtils.checkArgumentNotNull(configuration.get("password"), "password must be specified.");
    }

    private static IAuthenticationProvider authenticate(final Map<String, String> config)
            throws IOException {

        final OAuth2Credential credential = OAuth2TestAuthenticator
                .authenticateWithUsernamePassword(config.get("username"), config.get("password"));
        return new GraphApiAuthenticationProvider(credential.getAccessToken().getToken());
    }

    @Override
    public String getFSType() {
        return SharepointFileSystem.FS_TYPE;
    }

    @Override
    public FSLocationSpec createFSLocationSpec(final Map<String, String> configuration) {
        validateConfiguration(configuration);
        return SharepointFileSystem.createFSLocationSpec();
    }
}
