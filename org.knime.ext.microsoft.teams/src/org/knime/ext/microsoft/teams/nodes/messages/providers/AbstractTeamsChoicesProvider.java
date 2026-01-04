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

import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.microsoft.teams.nodes.messages.TeamsGraphClientFactory;
import org.knime.node.parameters.NodeParametersInput;

import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Package-private base class with helper methods for Teams dropdown choices
 * providers (chats, teams, channels).
 *
 * There is no shared mutable state. Credentials are resolved from the
 * {@link NodeParametersInput} provided to each computeState(...) call and a
 * fresh Graph client is created per invocation.
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */
abstract class AbstractTeamsChoicesProvider implements org.knime.node.parameters.widget.choices.StringChoicesProvider {

    /**
     * Gets a Graph client using the credential from the dialog context with
     * minimal Teams-specific scopes.
     */
    protected static GraphServiceClient<Request> getGraphClient(final NodeParametersInput context) {
        return getGraphClientWithScopes(context, false);
    }

    /**
     * Gets a Graph client using the credential from the dialog context with
     * scopes that allow reading chat members.
     */
    protected static GraphServiceClient<Request> getGraphClientWithMembersScope(final NodeParametersInput context) {
        return getGraphClientWithScopes(context, true);
    }

    private static GraphServiceClient<Request> getGraphClientWithScopes(final NodeParametersInput context,
            final boolean needMembers) {
        final var specOpt = context.getInPortSpec(0);
        final var spec = specOpt.filter(CredentialPortObjectSpec.class::isInstance)
                .map(CredentialPortObjectSpec.class::cast)
                .orElseThrow(() -> new IllegalStateException("Input port not connected"));

        final var scopes = needMembers ? TeamsGraphClientFactory.TEAMS_SCOPES_READ_MEMBERS
                : TeamsGraphClientFactory.TEAMS_SCOPES_MINIMAL;
        return TeamsGraphClientFactory.fromPortObjectSpec(spec, scopes);
    }
}
