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
 *   2025-10-24 (halilyerlikaya): created
 */

/**
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */
package org.knime.ext.microsoft.teams.nodes.messages;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.credentials.base.oauth.api.AccessTokenAccessor;
import org.knime.credentials.base.oauth.api.AccessTokenWithScopesAccessor;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.node.parameters.NodeParametersInput;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Factory class for creating Microsoft Graph Service Clients specifically
 * configured for Microsoft Teams operations, handling authentication provider
 * setup and scope management.
 */
final class TeamsGraphClientUtils {

    public static final List<String> TEAMS_SCOPES_READ = List.of(//
            "User.Read", //
            "Chat.ReadBasic", // Chat.Read may be required to read names :O
            "Team.ReadBasic.All", //
            "Channel.ReadBasic.All"
    );

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    private TeamsGraphClientUtils() {
    }

    /**
     * Creates a Graph client for the given credential spec and scopes.
     *
     * @param spec
     *            The credential specification containing authentication details.
     * @return A {@link GraphServiceClient} configured for Microsoft Teams.
     * @throws IOException
     *             if the client could not be created
     */
    static GraphServiceClient<Request> fromPortObjectSpec(final CredentialPortObjectSpec spec) throws IOException {
        try {
            final var authProvider = createTeamsAuthenticationProvider(spec, TEAMS_SCOPES_READ);
            return GraphApiUtil.createClient(authProvider, DIALOG_CLIENT_TIMEOUT_MILLIS, DIALOG_CLIENT_TIMEOUT_MILLIS);
        } catch (IOException | NoSuchCredentialException e) {
            throw new IOException("Could not authenticate." + e.getMessage(), e);
        } catch (GraphServiceException e) {
            throw new IOException("Could not authenticate.", e);
        }

    }

    /**
     * Creates an authentication provider with Teams-specific scopes. This is a
     * Teams-specific implementation that doesn't modify the shared
     * GraphCredentialUtil.
     *
     * @throws NoSuchCredentialException
     * @throws IOException
     */
    private static IAuthenticationProvider createTeamsAuthenticationProvider(final CredentialPortObjectSpec credSpec,
            final Collection<String> scopes) throws IOException, NoSuchCredentialException {

        final AccessTokenAccessor tokenAccessor;

        if (credSpec.hasAccessor(AccessTokenWithScopesAccessor.class) && scopes != null && !scopes.isEmpty()) {
            // Uses the custom scopes with AccessTokenWithScopesAccessor
            tokenAccessor = credSpec.toAccessor(AccessTokenWithScopesAccessor.class)
                    .getAccessTokenWithScopes(Set.copyOf(scopes));
        } else if (credSpec.hasAccessor(AccessTokenAccessor.class)) {
            // For simple AccessTokenAccessor
            tokenAccessor = credSpec.toAccessor(AccessTokenAccessor.class);
        } else {
            throw new IllegalStateException("The provided credential is incompatible.");
        }
        return GraphCredentialUtil.createAuthenticationProvider(tokenAccessor);
    }

    /**
     * Gets a Graph client using the credential from the dialog context with scopes
     * that allow reading chat members.
     */
    static GraphServiceClient<Request> getGraphClient(final NodeParametersInput context) throws IOException {
        final var spec = context.getInPortSpec(0)
                .orElseThrow(() -> new IllegalStateException("Input port not connected"));
        return fromPortObjectSpec((CredentialPortObjectSpec) spec);
    }
}
