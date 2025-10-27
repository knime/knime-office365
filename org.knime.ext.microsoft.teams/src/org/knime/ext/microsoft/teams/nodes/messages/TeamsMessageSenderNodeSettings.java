package org.knime.ext.microsoft.teams.nodes.messages;

import java.util.Optional;
import java.util.function.Supplier;

import org.knime.ext.microsoft.teams.nodes.messages.providers.ChannelChoicesProvider;
import org.knime.ext.microsoft.teams.nodes.messages.providers.ChatChoicesProvider;
import org.knime.ext.microsoft.teams.nodes.messages.providers.TeamChoicesProvider;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.text.RichTextInputWidget;
import org.knime.node.parameters.widget.text.TextAreaWidget;

/**
 * Settings for the Microsoft Teams Message Sender node
 *
 * @author halilyerlikaya
 */
public final class TeamsMessageSenderNodeSettings implements NodeParameters {

    /**
     * Destination type enum.
     */
    enum Destination {
        @Label("Chat")
        CHAT, @Label("Channel")
        CHANNEL;

        interface DestinationRef extends ParameterReference<Destination> {
        }
    }

    interface ChatRef extends ParameterReference<String> {
    }

    public interface TeamRef extends ParameterReference<String> {
    }

    interface ChannelRef extends ParameterReference<String> {
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
    String m_selectedChat = "__not_selected__";

    @TextMessage(ChatValidationMessage.class)
    @Effect(predicate = IsChat.class, type = EffectType.SHOW)
    Void m_chatValidationMessage;

    @Widget(title = "Team", description = "Select a team containing the target channel")
    @ChoicesProvider(TeamChoicesProvider.class)
    @Effect(predicate = IsChannel.class, type = EffectType.SHOW)
    @ValueReference(TeamRef.class)
    String m_selectedTeam = "__not_selected__";

    @TextMessage(TeamValidationMessage.class)
    @Effect(predicate = IsChannel.class, type = EffectType.SHOW)
    Void m_teamValidationMessage;

    @Widget(title = "Channel", description = "Select a channel within the team")
    @ChoicesProvider(ChannelChoicesProvider.class)
    @Effect(predicate = IsChannel.class, type = EffectType.SHOW)
    @ValueReference(ChannelRef.class)
    String m_selectedChannel = "__not_selected__";

    @TextMessage(ChannelValidationMessage.class)
    @Effect(predicate = IsChannel.class, type = EffectType.SHOW)
    Void m_channelValidationMessage;

    /**
     * Content type enum.
     */
    enum ContentType {
        @Label("Text")
        TEXT, @Label("HTML")
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

    static final class IsHtml implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(ContentType.ContentTypeRef.class).isOneOf(ContentType.HTML);
        }
    }

    @Widget(title = "Content type", description = "Message body content type")
    @ValueReference(ContentType.ContentTypeRef.class)
    @ValueSwitchWidget
    ContentType m_contentType = ContentType.TEXT;

    // ── New: separate fields for Text vs HTML ───────────────────────────────────

    /** Plain multi-line text area for TEXT mode. */
    @Widget(title = "Message (Text)", description = "Plain text message body")
    @Effect(predicate = IsText.class, type = EffectType.SHOW)
    @TextAreaWidget // renders a normal multi-line text editor
    String m_messageText = "";

    /** Rich text editor for HTML mode. */
    @Widget(title = "Message (HTML)", description = "HTML message body (rich text editor)")
    @Effect(predicate = IsHtml.class, type = EffectType.SHOW)
    @RichTextInputWidget // renders a rich text / HTML editor
    String m_messageHtml = "";

    /**
     * Backward-compat: keep a hidden single field to read legacy workflows
     * (pre-split). Not shown in UI; only used to import old settings if present.
     */
    @SuppressWarnings("unused")
    String m_message = "";

    /**
     * Validation message for Chat selection
     */
    static final class ChatValidationMessage implements StateProvider<Optional<TextMessage.Message>> {
        private Supplier<String> m_chatSupplier;
        private Supplier<Destination> m_destinationSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_chatSupplier = initializer.computeFromValueSupplier(ChatRef.class);
            m_destinationSupplier = initializer.computeFromValueSupplier(Destination.DestinationRef.class);
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            final var destination = m_destinationSupplier.get();
            final var selectedChat = m_chatSupplier.get();

            // Only show validation message when Chat destination is selected
            if (destination == Destination.CHAT) {
                if (selectedChat == null || selectedChat.isBlank() || "__not_selected__".equals(selectedChat)) {
                    return Optional.of(new TextMessage.Message("Please select a chat",
                            "Connect the Microsoft Authenticator node first, then select a chat from the dropdown",
                            TextMessage.MessageType.INFO));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Validation message for Team selection
     */
    static final class TeamValidationMessage implements StateProvider<Optional<TextMessage.Message>> {
        private Supplier<String> m_teamSupplier;
        private Supplier<Destination> m_destinationSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_teamSupplier = initializer.computeFromValueSupplier(TeamRef.class);
            m_destinationSupplier = initializer.computeFromValueSupplier(Destination.DestinationRef.class);
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            final var destination = m_destinationSupplier.get();
            final var selectedTeam = m_teamSupplier.get();

            // Only show validation message when Channel destination is selected
            if (destination == Destination.CHANNEL) {
                if (selectedTeam == null || selectedTeam.isBlank() || "__not_selected__".equals(selectedTeam)) {
                    return Optional.of(new TextMessage.Message("Please select a team",
                            "Connect the Microsoft Authenticator node first, then select a team from the dropdown",
                            TextMessage.MessageType.INFO));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Validation message for Channel selection
     */
    static final class ChannelValidationMessage implements StateProvider<Optional<TextMessage.Message>> {
        private Supplier<String> m_channelSupplier;
        private Supplier<String> m_teamSupplier;
        private Supplier<Destination> m_destinationSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_channelSupplier = initializer.computeFromValueSupplier(ChannelRef.class);
            m_teamSupplier = initializer.computeFromValueSupplier(TeamRef.class);
            m_destinationSupplier = initializer.computeFromValueSupplier(Destination.DestinationRef.class);
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            final var destination = m_destinationSupplier.get();
            final var selectedTeam = m_teamSupplier.get();
            final var selectedChannel = m_channelSupplier.get();

            // Only show validation message when Channel destination is selected
            if (destination == Destination.CHANNEL) {
                if (selectedTeam == null || selectedTeam.isBlank() || "__not_selected__".equals(selectedTeam)) {
                    return Optional.of(new TextMessage.Message("Select a team first",
                            "Please select a team from the dropdown above before choosing a channel",
                            TextMessage.MessageType.INFO));
                } else if (selectedChannel == null || selectedChannel.isBlank()
                        || "__not_selected__".equals(selectedChannel)) {
                    return Optional.of(new TextMessage.Message("Please select a channel",
                            "Select a channel within the chosen team from the dropdown", TextMessage.MessageType.INFO));
                }
            }
            return Optional.empty();
        }
    }

    /** Default ctor. */
    public TeamsMessageSenderNodeSettings() {
    }

    /** Ctor for NodeParameters API (no-op for now). */
    public TeamsMessageSenderNodeSettings(final NodeParametersInput ctx) {
    }
}