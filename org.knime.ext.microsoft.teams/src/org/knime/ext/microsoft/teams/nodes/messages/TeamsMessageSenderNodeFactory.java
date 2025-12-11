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

package org.knime.ext.microsoft.teams.nodes.messages;

import static org.knime.node.impl.description.PortDescription.fixedPort;

import java.util.List;
import java.util.Map;

import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.webui.node.dialog.NodeDialog; // webui NodeDialog
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultKaiNodeInterface;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterface;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterfaceFactory;
import org.knime.node.impl.description.DefaultNodeDescriptionUtil;
import org.knime.node.impl.description.PortDescription;

@SuppressWarnings("restriction") // suppress non-API access warnings from webui helpers
public class TeamsMessageSenderNodeFactory extends NodeFactory<TeamsMessageSenderNodeModel>
        implements NodeDialogFactory, KaiNodeInterfaceFactory {

    private static final String NODE_NAME = "Microsoft Teams Message Sender";
    private static final String NODE_ICON = "./teams16x16.png";

    private static final String SHORT_DESCRIPTION = """
            Send a message to a Microsoft Teams chat (default) or a channel.
        """;

    private static final String FULL_DESCRIPTION = """
        Sends a message to an existing Microsoft Teams chat or to a channel in a Team.
        First version: no attachments and no table input. Supports Text and limited HTML content.
            Note: Microsoft Graph enforces rate limits (≈10 messages / 10 seconds).
        """;

    private static final List<PortDescription> INPUT_PORTS = List.of(
            fixedPort("Microsoft Graph Credentials", "Credential port with OAuth context for Microsoft Graph.")
    );
    private static final List<PortDescription> OUTPUT_PORTS = List.of();

    // mirror StringToURI: provide keywords and use NodeType.Manipulator (safe in
    // your target)
    private static final List<String> KEYWORDS = List.of("Teams", "Microsoft", "Graph", "Chat", "Channel", "Message");

    @Override
    public TeamsMessageSenderNodeModel createNodeModel() {
        return new TeamsMessageSenderNodeModel();
    }

    @Override protected int getNrNodeViews() { return 0; }

    @Override
    public NodeView<TeamsMessageSenderNodeModel> createNodeView(final int index,
            final TeamsMessageSenderNodeModel model) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, TeamsMessageSenderNodeSettings.class);
    }

    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDescription createNodeDescription() {
        // Use the long overload (same as your StringToURI node)
        return DefaultNodeDescriptionUtil.createNodeDescription(
                NODE_NAME, NODE_ICON, INPUT_PORTS, OUTPUT_PORTS, SHORT_DESCRIPTION, FULL_DESCRIPTION, List.of(), // external
                                                                                                                 // resources
                TeamsMessageSenderNodeSettings.class, // settings class
                null, // view descriptions
                NodeType.Manipulator, // type (Connector isn't in your target)
                KEYWORDS, // keywords
                null // version
        );
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, TeamsMessageSenderNodeSettings.class));
    }
}


