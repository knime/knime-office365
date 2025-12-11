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

/**
 *
 * @author halilyerlikaya
 */

import java.util.ArrayList;
import java.util.List;

import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;

import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * ChoicesProvider for available Teams.
 *
 * Loads the user's joined Teams and exposes them as dropdown entries.
 */
public final class TeamChoicesProvider extends AbstractTeamsChoicesProvider implements StringChoicesProvider {

    /** Public no-arg constructor required by KNIME reflection. */
    public TeamChoicesProvider() {
        // no-op
    }

    @Override
    public List<StringChoice> computeState(final NodeParametersInput context) {
        if (shouldSkipApiCall()) {
            return List.of(new StringChoice("__not_selected__",
                    "⚠️ Network timeout - wait 30 seconds and reopen dialog to retry"));
        }

        try {
            final GraphServiceClient<Request> graphClient = getGraphClient();
            if (graphClient == null) {
                return List.of(new StringChoice("__not_selected__", "Connect Microsoft Authenticator node first"));
            }

            var teams = graphClient.me().joinedTeams().buildRequest().get();
            var teamChoices = new ArrayList<StringChoice>();

            for (var team : teams.getCurrentPage()) {
                teamChoices.add(new StringChoice(team.id, team.displayName));
            }

            if (teamChoices.isEmpty()) {
                return List.of(new StringChoice("__not_selected__", "No Teams found for this user"));
            }

            teamChoices.add(0, new StringChoice("__not_selected__", "Select a team…"));
            return teamChoices;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Failed to get license information")) {
                return List.of(new StringChoice("__not_selected__",
                        "❌ Teams license required - assign Office 365/Teams license to user"));
            }

            if (e.getMessage() != null && (e.getMessage().contains("Connect timed out")
                    || e.getMessage().contains("SocketTimeoutException")
                    || e.getMessage().contains("Error executing the request"))) {
                markNetworkError();
                return List.of(new StringChoice("__not_selected__",
                        "Network timeout - check connection, proxy settings, or firewall"));
            }

            return List.of(new StringChoice("__not_selected__", "Error loading teams: " + e.getMessage()));
        }
    }
}