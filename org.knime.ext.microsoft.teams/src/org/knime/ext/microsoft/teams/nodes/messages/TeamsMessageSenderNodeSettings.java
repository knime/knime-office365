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

import org.knime.core.node.InvalidSettingsException;
import org.knime.ext.microsoft.teams.nodes.messages.providers.ChannelChoicesProvider;
import org.knime.ext.microsoft.teams.nodes.messages.providers.ChatChoicesProvider;
import org.knime.ext.microsoft.teams.nodes.messages.providers.TeamChoicesProvider;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.text.RichTextInputWidget;
import org.knime.node.parameters.widget.text.TextAreaWidget;

/**
 * Settings for the Microsoft Teams Message Sender node
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */
public final class TeamsMessageSenderNodeSettings implements NodeParameters {

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
    public interface ChatRef extends ParameterReference<String> {
    }

    /**
     * Reference to the selected Microsoft Teams Team ID.
     */
    public interface TeamRef extends ParameterReference<String> {
    }

    /**
     * Reference to the selected Microsoft Teams Channel ID.
     */
    public interface ChannelRef extends ParameterReference<String> {
    }

    /** Show fields when "Chat" is selected. */
    static final class IsChat implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(Destination.DestinationRef.class).isOneOf(Destination.CHAT);
        }
    }

    /** Show fields when "Channel" is selected. */
    static final class IsChannel implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(Destination.DestinationRef.class).isOneOf(Destination.CHANNEL);
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
    @Effect(predicate = IsChannel.class, type = EffectType.SHOW)
    @ValueReference(TeamRef.class)
    String m_selectedTeam = "";

    @Widget(title = "Channel", description = "Select a channel within the team")
    @ChoicesProvider(ChannelChoicesProvider.class)
    @Effect(predicate = IsChannel.class, type = EffectType.SHOW)
    @ValueReference(ChannelRef.class)
    String m_selectedChannel = "";

    /**
     * Content type enum.
     */
    enum ContentType {
        @Label("Text")
        TEXT,

        @Label("HTML")
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
    @Widget(title = "Message (Text)", description = "The message is sent as is  and markdown "
            + "formatting is not respected.")
    @Effect(predicate = IsText.class, type = EffectType.SHOW)
    @TextAreaWidget
    String m_messageText = "";

    /** Rich text editor for HTML mode. */
    @Widget(title = "Message (HTML)", description = "This message will rendered as HTML to the receiving user.")
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
     * @throws InvalidSettingsException if the settings are invalid
     */
    @Override
    public void validate() throws InvalidSettingsException {
        // Validate message
        final String message = getMessage();
        if (message.isBlank()) {
            throw new InvalidSettingsException("Message must not be empty.");
        }

        if (m_destination == Destination.CHAT) {
            if (m_selectedChat.isBlank()) {
                throw new InvalidSettingsException("Chat selection is required. Please select a chat.");
            }
        } else {
            if (m_selectedTeam.isBlank() || m_selectedChannel.isBlank()) {
                throw new InvalidSettingsException("Team and Channel selections are required.");
            }
        }
    }
}
