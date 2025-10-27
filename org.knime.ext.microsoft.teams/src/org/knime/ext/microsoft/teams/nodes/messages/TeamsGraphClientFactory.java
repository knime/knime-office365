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
 * @author halilyerlikaya
 */
package org.knime.ext.microsoft.teams.nodes.messages;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.knime.core.node.port.PortObject;
import org.knime.credentials.base.CredentialPortObject;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.oauth.api.AccessTokenAccessor;
import org.knime.credentials.base.oauth.api.AccessTokenWithScopesAccessor;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;

import com.azure.core.credential.AccessToken;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;
import reactor.core.publisher.Mono;

public final class TeamsGraphClientFactory {

    // Minimal scopes for basic Teams functionality
    public static final List<String> TEAMS_SCOPES_MIN = List.of(
        "User.Read",           // /me
        "Chat.ReadBasic",      // /me/chats (no expand)
        "Team.ReadBasic.All",  // /me/joinedTeams
        "Channel.ReadBasic.All"// /teams/{id}/channels
    );

    // Required only if you insist on expanding members or reading chat members
    public static final List<String> TEAMS_SCOPES_PLUS = List.of(
        "User.Read",
        
        "Chat.ReadBasic",
        "Chat.Read",           // needed for expand=members or /chats/{id}/members
        "Team.ReadBasic.All",
        "Channel.ReadBasic.All"
    );

    private TeamsGraphClientFactory() {}

    /**
     * Accept either the PortObjectSpec or the PortObject with custom scopes
     */
    public static GraphServiceClient<Request> fromCredentialPort(final Object in, final Collection<String> scopes) {
        final CredentialPortObjectSpec spec;
        if (in instanceof CredentialPortObjectSpec) {
            spec = (CredentialPortObjectSpec) in;
        } else if (in instanceof PortObject) {
            final var po = (PortObject) in;
            if (po instanceof CredentialPortObject) {
                spec = ((CredentialPortObject) po).getSpec();
            } else {
                throw new IllegalArgumentException("Expected CredentialPortObject on input port 0");
            }
        } else {
            throw new IllegalArgumentException("Expected CredentialPortObjectSpec on input port 0");
        }

        // Try Teams-specific scopes first, then fallback to default
        if (scopes != null && !scopes.isEmpty()) {
            try {
                final var authProvider = createTeamsAuthenticationProvider(spec, scopes);
                final var graphClient = GraphApiUtil.createClient(authProvider, 60000, 120000);
                return graphClient;
            } catch (Exception teamsError) {
                // Check if it's a consent issue (AADSTS65001)
                if (teamsError.getMessage() != null && teamsError.getMessage().contains("AADSTS65001")) {
                    try {
                        // Fallback to the standard SharePoint approach (which the user has already consented to)
                        final var fallbackAuthProvider = GraphCredentialUtil.createAuthenticationProvider(spec);
                        final var fallbackClient = GraphApiUtil.createClient(fallbackAuthProvider, 60000, 120000);
                        return fallbackClient;
                    } catch (Exception fallbackError) {
                        // Throw the original Teams scope error since that's what we really want
                        throw new IllegalStateException("Teams scopes require admin consent. Original error: " + teamsError.getMessage(), teamsError);
                    }
                } else {
                    // Not a consent issue, throw the original error
                    throw new IllegalStateException("Failed to create Microsoft Graph client with Teams scopes: " + teamsError.getMessage(), teamsError);
                }
            }
        } else {
            // No specific scopes requested, use default approach
            try {
                final var defaultAuthProvider = GraphCredentialUtil.createAuthenticationProvider(spec);
                final var defaultClient = GraphApiUtil.createClient(defaultAuthProvider, 60000, 120000);
                return defaultClient;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create Microsoft Graph client: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates an authentication provider with Teams-specific scopes.
     * This is a Teams-specific implementation that doesn't modify the shared GraphCredentialUtil.
     */
    private static IAuthenticationProvider createTeamsAuthenticationProvider(
            final CredentialPortObjectSpec credSpec, final Collection<String> scopes) throws Exception {

        final AccessTokenAccessor tokenAccessor;

        if (credSpec.hasAccessor(AccessTokenWithScopesAccessor.class) && scopes != null && !scopes.isEmpty()) {
            // Use the custom scopes with AccessTokenWithScopesAccessor
            tokenAccessor = credSpec.toAccessor(AccessTokenWithScopesAccessor.class)
                    .getAccessTokenWithScopes(Set.copyOf(scopes));
        } else if (credSpec.hasAccessor(AccessTokenAccessor.class)) {
            // For simple AccessTokenAccessor, we can't customize scopes, use as-is
            tokenAccessor = credSpec.toAccessor(AccessTokenAccessor.class);
        } else {
            throw new IllegalStateException("The provided credential is incompatible.");
        }

        // Create the authentication provider (similar to GraphCredentialUtil but Teams-specific)
        final var accessTokenMono = Mono.fromCallable(() -> toAccessToken(tokenAccessor));
        final var authProvider = new TokenCredentialAuthProvider(ignored -> accessTokenMono);
        return authProvider;
    }

    private static AccessToken toAccessToken(final AccessTokenAccessor tokenAccessor) throws Exception {
        final var accessToken = tokenAccessor.getAccessToken();
        final var expiration = tokenAccessor.getExpiresAfter()
                .map(instant -> OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"))).orElseThrow();
        return new AccessToken(accessToken, expiration);
    }
}