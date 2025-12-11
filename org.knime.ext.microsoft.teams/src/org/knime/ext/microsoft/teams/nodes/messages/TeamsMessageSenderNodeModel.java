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

/**
 *
 * @author halilyerlikaya
 */

/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 * ---------------------------------------------------------------------
 *
 * History
 *   2025-10-24 (halilyerlikaya): created
 */
package org.knime.ext.microsoft.teams.nodes.messages;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.microsoft.teams.nodes.messages.providers.AbstractTeamsChoicesProvider;

import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

public final class TeamsMessageSenderNodeModel extends NodeModel {

    private static final NodeLogger LOG = NodeLogger.getLogger(TeamsMessageSenderNodeModel.class);
    private TeamsMessageSenderNodeSettings m_params = new TeamsMessageSenderNodeSettings();

    public TeamsMessageSenderNodeModel() {
        super(new PortType[] { org.knime.credentials.base.CredentialPortObject.TYPE }, new PortType[0]);
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO s) {
        s.addString("destination", m_params.m_destination.name());
        s.addString("selectedChat", m_params.m_selectedChat);
        s.addString("selectedTeam", m_params.m_selectedTeam);
        s.addString("selectedChannel", m_params.m_selectedChannel);

        s.addString("contentType", m_params.m_contentType.name());
        s.addString("messageText", m_params.m_messageText != null ? m_params.m_messageText : "");
        s.addString("messageHtml", m_params.m_messageHtml != null ? m_params.m_messageHtml : "");

        s.addString("message", getEffectiveMessageForPersist());
    }

    private String getEffectiveMessageForPersist() {
        if (m_params.m_contentType == TeamsMessageSenderNodeSettings.ContentType.HTML) {
            return m_params.m_messageHtml != null ? m_params.m_messageHtml : "";
        }
        return m_params.m_messageText != null ? m_params.m_messageText : "";
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO s) throws InvalidSettingsException {
        final var p = new TeamsMessageSenderNodeSettings();

        try {
            p.m_destination = TeamsMessageSenderNodeSettings.Destination
                    .valueOf(s.getString("destination", p.m_destination.name()));
        } catch (Exception ignore) {
            // keep default
        }

        p.m_selectedChat = s.getString("selectedChat", "");
        p.m_selectedTeam = s.getString("selectedTeam", "");
        p.m_selectedChannel = s.getString("selectedChannel", "");

        try {
            p.m_contentType = TeamsMessageSenderNodeSettings.ContentType
                    .valueOf(s.getString("contentType", p.m_contentType.name()));
        } catch (Exception ignore) {
            // keep default
        }

        p.m_messageText = s.getString("messageText", null);
        p.m_messageHtml = s.getString("messageHtml", null);

        final String legacy = s.getString("message", "");
        if (p.m_messageText == null && p.m_messageHtml == null) {
            p.m_messageText = legacy;
            p.m_messageHtml = legacy;
        } else {
            if (p.m_messageText == null) {
                p.m_messageText = "";
            }
            if (p.m_messageHtml == null) {
                p.m_messageHtml = "";
            }
        }

        m_params = p;
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        // Nothing extra; execute() does runtime validation.
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        if (inSpecs.length > 0 && inSpecs[0] instanceof CredentialPortObjectSpec) {
            AbstractTeamsChoicesProvider.updateCredentialContext((CredentialPortObjectSpec) inSpecs[0]);
        }
        return new PortObjectSpec[0];
    }

    @Override
    protected PortObject[] execute(final PortObject[] inPorts, final ExecutionContext exec) throws Exception {
        final var p = m_params;

        final boolean isHtml = p.m_contentType == TeamsMessageSenderNodeSettings.ContentType.HTML;
        final String message = isHtml ? p.m_messageHtml : p.m_messageText;

        if (message == null || message.isBlank()) {
            throw new InvalidSettingsException("Message must not be empty.");
        }

        final boolean toChat = p.m_destination == TeamsMessageSenderNodeSettings.Destination.CHAT;

        final String chatId = toChat ? safeTrim(p.m_selectedChat) : null;

        if (toChat) {
            if (chatId == null || chatId.isBlank() || "__not_selected__".equals(chatId)) {
                throw new InvalidSettingsException("Chat selection is required. Please select a chat.");
            }
        }

        String teamId = null;
        String channelId = null;

        if (!toChat) {
            final String rawTeam = safeTrim(p.m_selectedTeam);
            final String rawChannel = safeTrim(p.m_selectedChannel);

            teamId = rawTeam;
            channelId = rawChannel;

            if (teamId == null || teamId.isBlank() || "__not_selected__".equals(teamId)
                    || channelId == null || channelId.isBlank() || "__not_selected__".equals(channelId)) {
                throw new InvalidSettingsException("Team and Channel selections are required.");
            }
        }

        LOG.debug("TeamsMessageSender execute(): destination=" + p.m_destination + ", chatId=" + chatId + ", teamId="
                + teamId + ", channelId=" + channelId);

        final GraphServiceClient<Request> graph = TeamsGraphClientFactory.fromCredentialPort(inPorts[0],
                TeamsGraphClientFactory.TEAMS_SCOPES_MIN);

        final var body = new ItemBody();
        body.content = message;
        body.contentType = isHtml ? BodyType.HTML : BodyType.TEXT;

        final var msg = new ChatMessage();
        msg.body = body;

        try {
            if (toChat) {
                graph.chats().byId(chatId).messages().buildRequest().post(msg);
                LOG.info("Message sent to chat " + chatId);
            } else {
                graph.teams().byId(teamId).channels().byId(channelId).messages().buildRequest().post(msg);
                LOG.info("Message sent to channel " + teamId + "/" + channelId);
            }
        } catch (com.microsoft.graph.core.ClientException ge) {
            final var m = String.valueOf(ge.getMessage());
            LOG.warn("Graph error while sending Teams message: " + m, ge);

            if (m.contains("429")) {
                throw new InvalidSettingsException("Graph rate limit hit. Please retry later.", ge);
            }
            if (m.contains("403")) {
                throw new InvalidSettingsException("Insufficient Graph permissions or membership.", ge);
            }
            if (m.contains("404")) {
                throw new InvalidSettingsException(
                        "Target not found. Often caused by stale team/channel IDs.\n"
                                + "Re-open the dialog, re-select Team + Channel, and re-execute.",
                        ge);
            }
            throw new InvalidSettingsException("Graph error: " + m, ge);
        }

        exec.checkCanceled();
        return new PortObject[0];
    }

    private static String safeTrim(final String s) {
        return s == null ? null : s.trim();
    }

    @Override
    protected void reset() {
        // nothing to do
    }

    @Override
    protected void loadInternals(final java.io.File dir, final org.knime.core.node.ExecutionMonitor m) {
        // no internals
    }

    @Override
    protected void saveInternals(final java.io.File dir, final org.knime.core.node.ExecutionMonitor m) {
        // no internals
    }
}
