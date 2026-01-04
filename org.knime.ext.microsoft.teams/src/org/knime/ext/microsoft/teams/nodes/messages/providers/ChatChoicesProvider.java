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
import java.util.function.Supplier;

import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.widget.choices.StringChoice;

import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * ChoicesProvider for available chats.
 *
 * Uses Microsoft Graph to list chats for the current user. Uses extended scopes
 * so that member names can be displayed where possible.
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */
public final class ChatChoicesProvider extends AbstractTeamsChoicesProvider {

    private Supplier<String> m_chatSupplier;

    @Override
    public void init(final StateProviderInitializer initializer) {
        initializer.computeAfterOpenDialog();
        m_chatSupplier = initializer.computeFromValueSupplier(
                org.knime.ext.microsoft.teams.nodes.messages.TeamsMessageSenderNodeSettings.ChatRef.class);
    }

    // Default constructor implicitly provided

    @Override
    public List<StringChoice> computeState(final NodeParametersInput context) {
        try {
            final GraphServiceClient<Request> graph = getGraphClientWithMembersScope(context);

            var page = graph.me().chats().buildRequest().select("id,topic,chatType").get();
            var out = new ArrayList<StringChoice>();

            while (true) {
                for (var chat : page.getCurrentPage()) {
                    out.add(new StringChoice(chat.id, buildChatDisplayNameWithMembers(graph, chat)));
                }
                if (page.getNextPage() == null) {
                    break;
                }
                page = page.getNextPage().buildRequest().select("id,topic,chatType").get();
            }

            return out;
        } catch (Exception e) {
            final var current = m_chatSupplier != null ? m_chatSupplier.get() : null;
            return (current == null || current.isBlank())
                    ? List.of()
                    : List.of(new StringChoice(current, current));
        }
    }

    /** Build a readable label without members (for Chat.ReadBasic fallback). */
    private static String buildLabelNoMembers(final com.microsoft.graph.models.Chat chat) {
        if (chat.topic != null && !chat.topic.isBlank()) {
            return com.microsoft.graph.models.ChatType.MEETING == chat.chatType
                    ? ("Meeting: " + chat.topic)
                    : chat.topic;
        }
        return switch (chat.chatType) {
            case ONE_ON_ONE -> "1:1 chat";
            case GROUP -> "Group chat";
            case MEETING -> "Meeting";
            default -> "Chat";
        };
    }

    /**
     * Build a readable label with chat members (excluding current user).
     */
    private static String buildChatDisplayNameWithMembers(final GraphServiceClient<Request> graph,
            final com.microsoft.graph.models.Chat chat) {

        if (chat.topic != null && !chat.topic.isBlank()) {
            return com.microsoft.graph.models.ChatType.MEETING == chat.chatType
                    ? ("Meeting: " + chat.topic)
                    : chat.topic;
        }

        try {
            final var currentUserId = graph.me().buildRequest().get().id;

            var membersPage = graph.chats().byId(chat.id).members().buildRequest().get();
            var memberNames = new ArrayList<String>();
            for (var member : membersPage.getCurrentPage()) {
                if (!member.id.equals(currentUserId)) {
                    memberNames.add(member.displayName);
                }
            }

            if (!memberNames.isEmpty()) {
                final var prefix = switch (chat.chatType) {
                    case ONE_ON_ONE -> "";
                    case GROUP -> "Group: ";
                    default -> "Chat: ";
                };

                final var base = memberNames.size() <= 3
                        ? String.join(", ", memberNames)
                        : (String.join(", ", memberNames.subList(0, 2)) + " + others");
                return prefix + base;
            }
        } catch (Exception ignore) {

        }

        return buildLabelNoMembers(chat);
    }
}
