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
 * ChoicesProvider for available chats.
 *
 * Uses Microsoft Graph to list chats for the current user. Uses extended scopes
 * so that member names can be displayed where possible.
 */
public final class ChatChoicesProvider extends AbstractTeamsChoicesProvider
        implements StringChoicesProvider {

    /** Public no-arg constructor required by KNIME reflection. */
    public ChatChoicesProvider() {
        // no-op
    }

    @Override
    public List<StringChoice> computeState(final NodeParametersInput context) {
        if (shouldSkipApiCall()) {
            return List.of(new StringChoice("__not_selected__",
                    "⚠️ Network timeout - wait 30 seconds and reopen dialog to retry"));
        }

        try {
            // Use PLUS scopes to get chat members
            final GraphServiceClient<Request> graph = getGraphClient(true);
            if (graph == null) {
                return List.of(new StringChoice("__not_selected__",
                        "Connect Microsoft Authenticator node first"));
            }

            // Fetch chats without expanding members (not supported by API)
            var chats = graph.me().chats().buildRequest().select("id,topic,chatType").get();

            var out = new ArrayList<StringChoice>();
            out.add(new StringChoice("__not_selected__", "Select a chat..."));

            for (var chat : chats.getCurrentPage()) {
                out.add(new StringChoice(chat.id, buildChatDisplayNameWithMembers(graph, chat)));
            }

            if (out.size() == 1) { // Only placeholder, no real chats
                return List.of(new StringChoice("__not_selected__", "No chats found for this user"));
            }

            return out;

        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("Connect timed out")
                    || e.getMessage().contains("SocketTimeoutException")
                    || e.getMessage().contains("Error executing the request"))) {
                markNetworkError();
                return List.of(new StringChoice("__not_selected__",
                        "Network timeout - check connection, proxy or firewall"));
            }
            return List.of(new StringChoice("__not_selected__", "Error loading chats: " + e.getMessage()));
        }
    }

    /** Build a readable label without members (for Chat.ReadBasic fallback). */
    private static String buildLabelNoMembers(final com.microsoft.graph.models.Chat chat) {
        if (chat.topic != null && !chat.topic.isBlank()) {
            String ct = String.valueOf(chat.chatType);
            return "meeting".equalsIgnoreCase(ct) ? ("Meeting: " + chat.topic) : chat.topic;
        }
        final String ct = String.valueOf(chat.chatType);
        final String prefix = "meeting".equalsIgnoreCase(ct) ? "Meeting"
                : ("oneOnOne".equalsIgnoreCase(ct) ? "1:1" : ("group".equalsIgnoreCase(ct) ? "Group" : "Chat"));
        final String shortId = (chat.id != null) ? chat.id.substring(0, Math.min(8, chat.id.length())) : "—";
        return prefix + " #" + shortId;
    }

    /**
     * Build a readable label with chat members (excluding current user).
     */
    private static String buildChatDisplayNameWithMembers(final GraphServiceClient<Request> graph,
            final com.microsoft.graph.models.Chat chat) {

        if (chat.topic != null && !chat.topic.isBlank()) {
            String ct = String.valueOf(chat.chatType);
            return "meeting".equalsIgnoreCase(ct) ? ("Meeting: " + chat.topic) : chat.topic;
        }

        try {
            var membersRequest = graph.chats().byId(chat.id).members().buildRequest();
            var membersPage = membersRequest.get();

            var memberNames = new java.util.ArrayList<String>();
            String currentUserId = null;

            try {
                var currentUser = graph.me().buildRequest().get();
                currentUserId = currentUser.id;
            } catch (Exception e) {
                // Ignore error getting current user ID
            }

            for (var member : membersPage.getCurrentPage()) {
                if (member.id != null && !member.id.equals(currentUserId)) {
                    if (member.displayName != null && !member.displayName.isBlank()) {
                        memberNames.add(member.displayName);
                    }
                }
            }

            if (!memberNames.isEmpty()) {
                String ct = String.valueOf(chat.chatType);
                String prefix = "oneOnOne".equalsIgnoreCase(ct) ? ""
                        : ("group".equalsIgnoreCase(ct) ? "Group: " : "Chat: ");

                if (memberNames.size() == 1) {
                    return prefix + memberNames.get(0);
                } else if (memberNames.size() <= 3) {
                    return prefix + String.join(", ", memberNames);
                } else {
                    return prefix + String.join(", ", memberNames.subList(0, 2)) + " +" + (memberNames.size() - 2)
                            + " others";
                }
            }
        } catch (Exception e) {
            // Could not fetch chat members, will fallback to basic display name
        }

        return buildLabelNoMembers(chat);
    }
}