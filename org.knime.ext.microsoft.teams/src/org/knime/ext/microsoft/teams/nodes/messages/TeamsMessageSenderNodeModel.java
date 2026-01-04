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

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEException;
import org.knime.core.node.NodeLogger;
import org.knime.node.DefaultModel.ConfigureInput;
import org.knime.node.DefaultModel.ConfigureOutput;
import org.knime.node.DefaultModel.ExecuteInput;
import org.knime.node.DefaultModel.ExecuteOutput;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.ChatMessage;
import com.microsoft.graph.models.ItemBody;

/**
 * Node model logic for the Microsoft Teams Message Sender node.
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */
final class TeamsMessageSenderNodeModel {

    private static final NodeLogger LOG = NodeLogger.getLogger(TeamsMessageSenderNodeModel.class);

    private TeamsMessageSenderNodeModel() {
        // Utility class
    }

    /**
     * Configure method for the node.
     *
     * @param input configure input
     * @param output configure output
     * @throws InvalidSettingsException if settings are invalid
     */
    static void configure(final ConfigureInput input, final ConfigureOutput output)//NOSONAR
            throws InvalidSettingsException {
        final TeamsMessageSenderNodeSettings settings = input.getParameters();
        settings.validate();
    }

    /**
     * Execute method for the node.
     *
     * @param input execute input
     * @throws CanceledExecutionException if execution is canceled
     * @throws KNIMEException if execution fails
     */
    static void execute(final ExecuteInput input, final ExecuteOutput output)// NOSONAR
            throws CanceledExecutionException, KNIMEException {
        final var exec = input.getExecutionContext();
        final TeamsMessageSenderNodeSettings settings = input.getParameters();
        final var inPorts = input.getInPortObjects();

        final var message = settings.getMessage();
        final var isHtml = settings.m_contentType == TeamsMessageSenderNodeSettings.ContentType.HTML;
        final var toChat = settings.m_destination == TeamsMessageSenderNodeSettings.Destination.CHAT;


        final var chatId = settings.m_selectedChat.trim();
        final var teamId = settings.m_selectedTeam.trim();
        final var channelId = settings.m_selectedChannel.trim();

        LOG.debug(() -> String.format(
                "TeamsMessageSender execute(): destination=%s, chatId=%s, teamId=%s, channelId=%s",
                settings.m_destination, chatId, teamId, channelId));

        final var spec = (inPorts[0] instanceof org.knime.credentials.base.CredentialPortObject port)
            ? port.getSpec()
            : null;
        if (spec == null) {
            throw new KNIMEException("Input port not connected");
        }
        final var graph = TeamsGraphClientFactory.fromPortObjectSpec(spec,
            TeamsGraphClientFactory.TEAMS_SCOPES_MINIMAL);

        final var body = new ItemBody();
        body.content = message;
        body.contentType = isHtml ? BodyType.HTML : BodyType.TEXT;

        final var msg = new ChatMessage();
        msg.body = body;

        try {
            if (toChat) {
                graph.chats().byId(chatId).messages().buildRequest().post(msg);
                LOG.debug("Message sent to chat " + chatId);
            } else {
                graph.teams().byId(teamId).channels().byId(channelId).messages().buildRequest().post(msg);
                LOG.debug("Message sent to channel " + teamId + "/" + channelId);
            }
        } catch (GraphServiceException ge) {
            final var statusCode = ge.getResponseCode();
            final var errorMessage = ge.getServiceError() != null && ge.getServiceError().message != null
                    ? ge.getServiceError().message
                    : ge.getMessage();

            LOG.debug(() -> String.format("Graph error while sending Teams message (HTTP %d): %s", statusCode,
                    errorMessage), ge);

            if (statusCode == 429) {
                throw new KNIMEException("Graph rate limit hit. Please retry later.", ge);
            } else if (statusCode == 403) {
                throw new KNIMEException("Insufficient Graph permissions or membership.", ge);
            } else if (statusCode == 404) {
                throw new KNIMEException(
                        "Target not found. Often caused by stale team/channel IDs.\n"
                                + "Re-open the dialog, re-select Team + Channel, and re-execute.",
                        ge);
            }
            throw new KNIMEException("Graph error: " + errorMessage, ge);
        } catch (ClientException ce) {
            LOG.debug("Client error while sending Teams message: " + ce.getMessage(), ce);
            throw new KNIMEException("Error sending message: " + ce.getMessage(), ce);
        }

        exec.checkCanceled();
    }
}
