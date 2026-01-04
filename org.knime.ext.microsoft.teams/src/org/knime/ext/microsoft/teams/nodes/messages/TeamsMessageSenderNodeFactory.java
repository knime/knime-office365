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

import org.knime.credentials.base.CredentialPortObject;
import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;

/**
 * The node factory for the Microsoft Teams Message Sender Node.
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public final class TeamsMessageSenderNodeFactory extends DefaultNodeFactory {

    /**
     * Creates the factory with the DefaultNode definition.
     */
    public TeamsMessageSenderNodeFactory() {
        super(DefaultNode.create().name("Microsoft Teams Message Sender").icon("./teams16x16.png")
                .shortDescription("Send a message to a Microsoft Teams chat (default) or a channel.")
                .fullDescription("""
                        Sends a message to an existing Microsoft Teams chat or to a channel in a Team.
                        First version: no attachments and no table input. Supports Text and limited HTML content.
                        Note: Microsoft Graph enforces rate limits (≈10 messages / 10 seconds).
                        """).sinceVersion(5, 4, 0)
                .ports(ports -> ports.addInputPort("Credential port with OAuth context for Microsoft Graph.",
                        "Microsoft Graph Credentials", CredentialPortObject.TYPE))
                .model(model -> model.parametersClass(TeamsMessageSenderNodeSettings.class)
                        .configure(TeamsMessageSenderNodeModel::configure)
                        .execute(TeamsMessageSenderNodeModel::execute))
                .keywords("Teams", "Microsoft", "Graph", "Chat", "Channel", "Message").nodeType(NodeType.Manipulator));
    }
}
