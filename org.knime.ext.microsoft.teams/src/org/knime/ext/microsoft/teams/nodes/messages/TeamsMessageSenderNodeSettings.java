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

package org.knime.ext.microsoft.teams.nodes.messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.text.RichTextInputWidget;
import org.knime.node.parameters.widget.text.TextAreaWidget;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.ChatType;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Settings for the Microsoft Teams Message Sender node
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */
final class TeamsMessageSenderNodeSettings implements NodeParameters {

    private static final NodeLogger LOG = NodeLogger.getLogger(TeamsMessageSenderNodeSettings.class);

    /**
     * Destination type enum.
     */
    enum Destination {
        @Label("Chat")
        CHAT,

        @Label("Channel")
        CHANNEL;

        interface DestinationRef extends ParameterReference<Destination> {
        }
    }

    /**
     * Reference to the selected Microsoft Teams Chat ID.
     */
    interface ChatRef extends ParameterReference<String> {
    }

    /**
     * Reference to the selected Microsoft Teams Team ID.
     */
    interface TeamRef extends ParameterReference<String> {
    }

    /**
     * Reference to the selected Microsoft Teams Channel ID.
     */
    interface ChannelRef extends ParameterReference<String> {
    }

    /** Show fields when "Chat" is selected. */
    static final class IsChat implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(Destination.DestinationRef.class).isOneOf(Destination.CHAT);
        }
    }

    @Widget(title = "Destination", description = "Choose Chat or Channel")
    @ValueReference(Destination.DestinationRef.class)
    @ValueSwitchWidget
    Destination m_destination = Destination.CHAT;

    @Widget(title = "Chat", description = "Select a chat to send message to")
    @ChoicesProvider(ChatChoicesProvider.class)
    @Effect(predicate = IsChat.class, type = EffectType.SHOW)
    @ValueReference(ChatRef.class)
    String m_selectedChat = "";

    @Widget(title = "Team", description = "Select a team containing the target channel")
    @ChoicesProvider(TeamChoicesProvider.class)
    @Effect(predicate = IsChat.class, type = EffectType.HIDE)
    @ValueReference(TeamRef.class)
    String m_selectedTeam = "";

    @Widget(title = "Channel", description = "Select a channel within the team")
    @ChoicesProvider(ChannelChoicesProvider.class)
    @Effect(predicate = IsChat.class, type = EffectType.HIDE)
    @ValueReference(ChannelRef.class)
    String m_selectedChannel = "";

    /**
     * Content type enum.
     */
    enum ContentType {
        @Label(value = "Text", description = //
        "The plain message content is sent as-is meaning that markdown is not respcted.")
        TEXT,

        @Label(value = "HTML", description = //
        "The message content supports basic formatting and is sent as HTML.")
        HTML;

        interface ContentTypeRef extends ParameterReference<ContentType> {
        }
    }

    /** Predicates to toggle which editor is shown */
    static final class IsText implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(ContentType.ContentTypeRef.class).isOneOf(ContentType.TEXT);
        }
    }

    @Widget(title = "Content type", description = "Message body content type")
    @ValueReference(ContentType.ContentTypeRef.class)
    @ValueSwitchWidget
    ContentType m_contentType = ContentType.TEXT;

    /** Plain multi-line text area for TEXT mode. */
    @Widget(title = "Message", description = "Content of the message as plain text.")
    @Effect(predicate = IsText.class, type = EffectType.SHOW)
    @TextAreaWidget
    String m_messageText = "";

    /** Rich text editor for HTML mode. */
    @Widget(title = "Message", description = "Content of the message with formatting.")
    @Effect(predicate = IsText.class, type = EffectType.HIDE)
    @RichTextInputWidget
    String m_messageHtml = "";

    /**
     * Returns the appropriate message based on the current content type.
     *
     * @return the message string (HTML or Text)
     */
    public String getMessage() {
        return m_contentType == ContentType.HTML ? m_messageHtml : m_messageText;
    }

    /**
     * Validates the current settings.
     *
     * @throws InvalidSettingsException
     *             if the settings are invalid
     */
    @Override
    public void validate() throws InvalidSettingsException {
        // Validate message
        final String message = getMessage();
        if (message.isBlank()) {
            throw new InvalidSettingsException("Please provide a message to be sent.");
        }

        if (m_destination == Destination.CHAT) {
            if (m_selectedChat.isBlank()) {
                throw new InvalidSettingsException("Please select a chat.");
            }
        } else {
            if (m_selectedTeam.isBlank() || m_selectedChannel.isBlank()) {
                throw new InvalidSettingsException("Please select a team and channel.");
            }
        }
    }

    /**
     * ChoicesProvider for available channels.
     *
     * Behaviour:
     * <ul>
     * <li>If Team is selected -> list that team's channels (value =
     * channelId).</li>
     * <li>If Team is NOT selected -> return an empty list.</li>
     * </ul>
     *
     * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
     */
    static final class ChannelChoicesProvider implements StringChoicesProvider {

        private Supplier<String> m_teamSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_teamSupplier = initializer.computeFromValueSupplier(TeamsMessageSenderNodeSettings.TeamRef.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            try {
                final String selectedTeamId = m_teamSupplier.get();
                if (selectedTeamId == null || selectedTeamId.isBlank()) {
                    return List.of();
                }

                final var graphClient = TeamsGraphClientUtils.getGraphClient(context);
                var channels = graphClient.teams().byId(selectedTeamId).channels().buildRequest().get();

                final var choices = new ArrayList<StringChoice>();
                for (var channel : channels.getCurrentPage()) {
                    choices.add(new StringChoice(channel.id, channel.displayName));
                }
                return choices;
            } catch (IOException | GraphServiceException e) {// NOSONAR
                LOG.debug("Could not list channels", e);
                return List.of();
            }
        }
    }

    /**
     * ChoicesProvider for available chats.
     *
     * Uses Microsoft Graph to list chats for the current user. Uses extended scopes
     * so that member names can be displayed where possible.
     *
     * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
     */
    static final class ChatChoicesProvider implements StringChoicesProvider {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        // Default constructor implicitly provided

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            try {
                final var graph = TeamsGraphClientUtils.getGraphClient(context);

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
            } catch (IOException | GraphServiceException e) {// NOSONAR
                LOG.debug("Could not list chats", e);
                return List.of();
            }
        }

        /**
         * Build a readable label with chat members (excluding current user).
         */
        private static String buildChatDisplayNameWithMembers(final GraphServiceClient<Request> graph,
                final com.microsoft.graph.models.Chat chat) {

            if (chat.topic != null && !chat.topic.isBlank()) {
                return ChatType.MEETING == chat.chatType ? ("Meeting: " + chat.topic) : chat.topic;
            }

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

                final var base = memberNames.size() <= 3 ? String.join(", ", memberNames)
                        : (String.join(", ", memberNames.subList(0, 2)) + " + others");
                return prefix + base;
            } else {
                return "(empty)";
            }
        }
    }

    /**
     * ChoicesProvider for available Teams.
     *
     * Loads the user's joined Teams and exposes them as dropdown entries.
     */
    static final class TeamChoicesProvider implements StringChoicesProvider {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            try {
                final GraphServiceClient<Request> graphClient = TeamsGraphClientUtils.getGraphClient(context);
                var page = graphClient.me().joinedTeams().buildRequest().select("id,displayName").get();
                var teamChoices = new ArrayList<StringChoice>();

                while (true) {
                    for (var team : page.getCurrentPage()) {
                        teamChoices.add(new StringChoice(team.id, team.displayName));
                    }
                    if (page.getNextPage() == null) {
                        break;
                    }
                    page = page.getNextPage().buildRequest().get();
                }

                return teamChoices;
            } catch (IOException | GraphServiceException e) { // NOSONAR
                LOG.debug("Could not list teams", e);
                return List.of();
            }
        }
    }
}
