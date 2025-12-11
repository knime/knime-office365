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
 *   2025-11-19 (halilyerlikaya): created
 */

package org.knime.ext.microsoft.teams.nodes.messages.providers;


import java.util.List;

import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.microsoft.teams.nodes.messages.TeamsGraphClientFactory;

import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Abstract base class that encapsulates shared context and helper methods for
 * Teams dropdown choices providers (chats, teams, channels).
 *
 * It holds the credential spec, Graph client creation and network error caching
 * so that concrete providers can stay focused on their own logic.
 *
 * @author halilyerlikaya
 */
public abstract class AbstractTeamsChoicesProvider {

    /** Minimal Teams scopes (no member expansion). */
    private static final List<String> TEAMS_SCOPES_MIN = TeamsGraphClientFactory.TEAMS_SCOPES_MIN;

    /** Extended Teams scopes (for reading members etc.). */
    private static final List<String> TEAMS_SCOPES_PLUS = TeamsGraphClientFactory.TEAMS_SCOPES_PLUS;

    /** Static context for credential spec - updated by node model. */
    private static volatile CredentialPortObjectSpec s_credentialSpec = null;

    /** Cache for failed calls to avoid repeated timeout errors. */
    private static volatile boolean s_hasNetworkIssue = false;
    private static volatile long s_lastNetworkErrorTime = 0L;
    /** Cache network error flag for 30 seconds. */
    private static final long NETWORK_ERROR_CACHE_MS = 30_000L;

    /**
     * Updates the credential context for all ChoicesProviders. Called by the node
     * model when the credential port is available.
     *
     * NOTE: this method also performs a manual connectivity test if the credential
     * spec is present, to help diagnose network / auth issues.
     *
     * @param credSpec
     *            the credential spec from the credential port
     */
    public static void updateCredentialContext(final CredentialPortObjectSpec credSpec) {
        if (credSpec != null) {
            // Store the credential spec regardless of isPresent() status.
            s_credentialSpec = credSpec;

            // Set network properties for better connectivity
            if (credSpec.isPresent()) {
                System.setProperty("java.net.useSystemProxies", "true");
                System.setProperty("okhttp.protocols", "http/1.1");
            }
        } else {
            s_credentialSpec = null;
        }
    }

    /**
     * Gets the Graph client using the current credential context with minimal
     * Teams-specific scopes.
     *
     * @return GraphServiceClient or {@code null} if credentials are not available
     */
    protected static GraphServiceClient<Request> getGraphClient() {
        return getGraphClient(false);
    }

    /**
     * Gets the Graph client using the current credential context with
     * Teams-specific scopes.
     *
     * @param needMembers
     *            whether member-related scopes are required
     * @return GraphServiceClient or {@code null} if credentials are not available
     */
    protected static GraphServiceClient<Request> getGraphClient(final boolean needMembers) {
        try {
            if (s_credentialSpec == null) {
                return null;
            }

            final var scopes = needMembers ? TEAMS_SCOPES_PLUS : TEAMS_SCOPES_MIN;
            return TeamsGraphClientFactory.fromCredentialPort(s_credentialSpec, scopes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if we should skip API calls due to recent network errors.
     *
     * @return {@code true} if API calls should be skipped for now
     */
    protected static boolean shouldSkipApiCall() {
        if (s_hasNetworkIssue) {
            final long timeSinceError = System.currentTimeMillis() - s_lastNetworkErrorTime;
            if (timeSinceError < NETWORK_ERROR_CACHE_MS) {
                return true;
            }
            s_hasNetworkIssue = false;
        }
        return false;
    }

    /** Mark that a network error has occurred. */
    protected static void markNetworkError() {
        s_hasNetworkIssue = true;
        s_lastNetworkErrorTime = System.currentTimeMillis();
    }
}
