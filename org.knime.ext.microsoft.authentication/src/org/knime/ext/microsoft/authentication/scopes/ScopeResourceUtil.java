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
 *   2025-05-21 (bjoern): created
 */
package org.knime.ext.microsoft.authentication.scopes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.knime.credentials.base.Credential;
import org.knime.credentials.base.oauth.api.AccessTokenWithScopesCredential;
import org.knime.credentials.base.oauth.api.JWTCredential;

/**
 * Utility class for parsing the resource identifiers from scope strings.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class ScopeResourceUtil {

    /**
     * Resource identifier for Microsoft Graph API.
     */
    public static final String GRAPH_RESOURCE = "https://graph.microsoft.com/";

    private ScopeResourceUtil() {
        // prevent instantiation
    }

    /**
     * Represents a list of scopes for which an access token should be fetched.
     *
     * @param scopes
     *            The scopes for which to immediately fetch an access token (and
     *            potentially ask consent for)
     * @param isMultiResource
     *            If true, the resulting {@link Credential} should be
     *            {@link AccessTokenWithScopesCredential} (which ad-hoc fetches
     *            access tokens, depending on ad-hoc requested scopes), otherwise it
     *            should be a {@link JWTCredential}.
     */
    public record ScopeList(//
            Set<String> scopes, //
            boolean isMultiResource) {
    }

    /**
     * Heuristically parses the resource from a scope string.
     *
     * @param scope
     *            The scope string.
     * @return The likely resource identifier of the scope.
     */
    public static String parseResource(final String scope) {
        // it seems that also the OIDC scopes (openid, etc) belong to the Microsoft
        // Graph resource
        if (!scope.contains("/")) {
            return GRAPH_RESOURCE;
        } else {
            return scope.substring(0, scope.lastIndexOf("/") + 1);
        }
    }

    /**
     * Groups the given scopes by their resource identifier.
     *
     * @param scopes
     *            The list of scopes to group.
     * @return A map where the keys are resource identifiers and the values are sets
     *         of scopes belonging to that resource.
     */
    private static Map<String, Set<String>> groupScopesByResource(final Set<String> scopes) { // NOSONAR currently dead
                                                                                              // code, for future use

        Map<String, Set<String>> groupedScopes = new HashMap<>();
        for (final var scope : scopes) {
            final var resource = parseResource(scope);
            groupedScopes.computeIfAbsent(resource, k -> new HashSet<>()).add(scope);
        }

        return groupedScopes;
    }

    /**
     * Computes a {@link ScopeList} of delegated scopes/permissions, based on
     * user-requested scopes.
     *
     * There are two scenarios:
     * <ul>
     * <li>The user requests no scopes. In this case this method ensures that at
     * least offline_access is requested (and isMultiResource is true).</li>
     * <li>Otherwise this method outputs the same scopes that the user provided (and
     * isMultiResource is false). This behavior should be backwards-compatible.</li>
     * </ul>
     *
     * @param requestedScopes
     *            The scopes requested by the user (may be empty).
     * @return a {@link ScopeList} to use towards Entra.
     */
    public static ScopeList computeDelegatedScopeList(final Set<String> requestedScopes) {

        // order scopes so that the graph scopes (which include oidc) are first
        final var scopes = new LinkedHashSet<String>();
        boolean isMultiResource = false;

        if (requestedScopes.isEmpty()) {
            // if the user specifies no scopes then the
            // resulting credential should be able to fetch access tokens on-demand with
            // scopes from any resource (AccessTokenWithScopesCredential).

            // We put the oidc/graph scopes first because they are the only ones that should
            // be granted on the initial token request (to get the refresh token).
            isMultiResource = true;
            scopes.add("offline_access");
        } else {
            // due to limitations of MSAL4J we cannot distinguish between scopes for which
            // to ask consent, and scopes that will be sent to the token endpoint. Scopes
            // from multiple resources very likely will not work on the token endpoint, but
            // maybe there are isolated cases which somehow work or don't fail immediately,
            // hence we don't change the behavior for backwards compatibility.
            isMultiResource = false;
            scopes.addAll(requestedScopes);
        }

        return new ScopeList(scopes, isMultiResource);
    }

    /**
     * Computes a {@link ScopeList} of application scopes/permissions, based on
     * user-requested scopes.
     *
     * There are two scenarios:
     * <ul>
     * <li>The user requests no scopes. In this case this method ensures that the
     * .default scope is requested initially (and isMultiResource is true).</li>
     * <li>Otherwise this method outputs the same scopes that the user provided (and
     * isMultiResource is false). This behavior should be backwards-compatible.</li>
     * </ul>
     *
     * @param applicationScopes
     *            The scopes requested by the user (may be empty).
     * @return a {@link ScopeList} to use towards Entra.
     */
    public static ScopeList computeApplicationScopeList(final Set<String> applicationScopes) {

        // order scopes so that the graph scopes (which include oidc) are first
        final var scopes = new LinkedHashSet<String>();
        boolean isMultiResource = false;

        if (applicationScopes.isEmpty()) {
            // if the user specifies no scopes then the resulting credential should be able
            // to fetch access tokens on-demand with scopes from any resource
            // (AccessTokenWithScopesCredential).

            isMultiResource = true;
            scopes.add(".default");
        } else {
            // multiple resources very likely will not work on the token endpoint, but maybe
            // there are isolated cases which somehow work or don't fail immediately, hence
            // we don't change the behavior for backwards compatibility.
            isMultiResource = false;
            scopes.addAll(applicationScopes);
        }

        return new ScopeList(scopes, isMultiResource);
    }
}
