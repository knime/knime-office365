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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.knime.ext.microsoft.teams.nodes.messages.TeamsMessageSenderNodeSettings;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;

import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * ChoicesProvider for available channels.
 *
 * Behaviour:
 * <ul>
 *   <li>If Team is selected -> list that team's channels (value = channelId),
 *       plus a "Select a channel…" placeholder at the top.</li>
 *   <li>If Team is NOT selected -> show a single entry "Select a team first".</li>
 * </ul>
 */
public final class ChannelChoicesProvider extends AbstractTeamsChoicesProvider
        implements StringChoicesProvider {

    private Supplier<String> m_teamSupplier;

    /** Public no-arg constructor required by KNIME reflection. */
    public ChannelChoicesProvider() {
        // no-op
    }

    @Override
    public void init(final StateProviderInitializer initializer) {
        initializer.computeAfterOpenDialog();
        m_teamSupplier =
                initializer.computeFromValueSupplier(TeamsMessageSenderNodeSettings.TeamRef.class);
    }

    @Override
    public List<StringChoice> computeState(final NodeParametersInput context) {
        try {
            final String selectedTeamId = m_teamSupplier != null ? m_teamSupplier.get() : null;

            if (selectedTeamId == null || selectedTeamId.isBlank()
                    || "__not_selected__".equals(selectedTeamId)) {
                return List.of(new StringChoice("__not_selected__", "Select a team first"));
            }

            if (shouldSkipApiCall()) {
                return List.of(new StringChoice("__not_selected__",
                        "Network connectivity issue - check your connection and try again"));
            }

            final GraphServiceClient<Request> graphClient = getGraphClient();
            if (graphClient == null) {
                return List.of(new StringChoice("__not_selected__",
                        "Connect Microsoft Authenticator node first"));
            }

            var channels =
                    graphClient.teams().byId(selectedTeamId).channels().buildRequest().get();

            final var choices = new ArrayList<StringChoice>();
            choices.add(new StringChoice("__not_selected__", "Select a channel…"));

            for (var channel : channels.getCurrentPage()) {
                final String channelId = channel.id;
                final String displayName = channel.displayName != null
                        ? channel.displayName
                        : "Unnamed Channel";
                choices.add(new StringChoice(channelId, displayName));
            }

            return choices;

        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("Connect timed out")
                    || e.getMessage().contains("SocketTimeoutException")
                    || e.getMessage().contains("Error executing the request"))) {
                markNetworkError();
                return List.of(new StringChoice("__not_selected__",
                        "Network timeout - check proxy/firewall"));
            }
            return List.of(new StringChoice("__not_selected__",
                    "Error loading channels: " + e.getMessage()));
        }
    }
}