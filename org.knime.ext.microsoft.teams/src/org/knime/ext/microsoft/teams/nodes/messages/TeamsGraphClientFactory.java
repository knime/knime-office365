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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.oauth.api.AccessTokenAccessor;
import org.knime.credentials.base.oauth.api.AccessTokenWithScopesAccessor;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;
/**
 * Factory class for creating Microsoft Graph Service Clients specifically configured
 * for Microsoft Teams operations, handling authentication provider setup and scope management.
 */
public final class TeamsGraphClientFactory {

    // Minimal scopes for basic Teams functionality.
    public static final List<String> TEAMS_SCOPES_MINIMAL = List.of("User.Read",
            "Chat.ReadBasic", // /me/chats //
            "Team.ReadBasic.All", // /me/joinedTeams //
            "Channel.ReadBasic.All" // /teams/{id}/channels //
    );

    // Additional scopes to read chat members or expand members in chats.
    public static final List<String> TEAMS_SCOPES_READ_MEMBERS = List.of("User.Read",
            "Chat.ReadBasic",
            "Chat.Read", // needed for expand=members or /chats/{id}/members //
            "Team.ReadBasic.All",
            "Channel.ReadBasic.All"
    );

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    private TeamsGraphClientFactory() {
    }

    /**
     * Creates a Graph client for the given credential spec and scopes.
     *
     * @param spec
     *            The credential specification containing authentication details.
     * @param scopes
     *            The collection of OAuth scopes required for the Graph client.
     * @return A {@link GraphServiceClient} configured for Microsoft Teams.
     */
    public static GraphServiceClient<Request> fromPortObjectSpec(final CredentialPortObjectSpec spec,
            final Collection<String> scopes) {
        try {
            final var authProvider = createTeamsAuthenticationProvider(spec, scopes);
            return GraphApiUtil.createClient(authProvider, DIALOG_CLIENT_TIMEOUT_MILLIS, DIALOG_CLIENT_TIMEOUT_MILLIS);
        } catch (Exception teamsError) {
            final var msg = teamsError.getMessage();
            if (msg != null && msg.contains("AADSTS65001")) {
                try {
                    final var fallbackAuthProvider = GraphCredentialUtil.createAuthenticationProvider(spec);
                    return GraphApiUtil.createClient(fallbackAuthProvider, DIALOG_CLIENT_TIMEOUT_MILLIS,
                            DIALOG_CLIENT_TIMEOUT_MILLIS);
                } catch (Exception fallbackError) {
                    throw new IllegalStateException(
                            "Teams scopes require admin consent. Original error: " + teamsError.getMessage(),
                            teamsError);
                }
            }
            throw new IllegalStateException(
                    "Failed to create Microsoft Graph client with Teams scopes: " + teamsError.getMessage(),
                    teamsError);
        }
    }

    /**
     * Creates an authentication provider with Teams-specific scopes. This is a
     * Teams-specific implementation that doesn't modify the shared
     * GraphCredentialUtil.
     */
    private static IAuthenticationProvider createTeamsAuthenticationProvider(final CredentialPortObjectSpec credSpec,
            final Collection<String> scopes) throws Exception {

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
}
