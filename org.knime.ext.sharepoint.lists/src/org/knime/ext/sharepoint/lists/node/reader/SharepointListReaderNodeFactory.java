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
 *   Nov 4, 2020 (Tobias): created
 */
package org.knime.ext.sharepoint.lists.node.reader;

import static org.knime.node.impl.description.PortDescription.fixedPort;

import java.util.List;
import java.util.Map;

import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.util.Version;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultKaiNodeInterface;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterface;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterfaceFactory;
import org.knime.node.impl.description.DefaultNodeDescriptionUtil;
import org.knime.node.impl.description.PortDescription;

/**
 * Factory implementation of the "SharePoint List Reader" node.
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings({ "restriction", "removal" })
public class SharepointListReaderNodeFactory extends NodeFactory<SharepointListReaderNodeModel>
        implements NodeDialogFactory, KaiNodeInterfaceFactory {

    private static final String NODE_NAME = "SharePoint List Reader";
    private static final String NODE_ICON = "./sharepoint-list-reader.png";
    private static final String SHORT_DESCRIPTION = """
            Read a SharePoint list.
            """;
    private static final String FULL_DESCRIPTION = """
            <p>
                This node reads a SharePoint list and outputs it as a KNIME table.
            </p>
            <p>
                <b>SharePoint list column type support</b><br/>
                You can select to which KNIME types the
                <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">SharePoint list column types</a>
                should be transformed. The following describes the extend of the support
                for the different column types.
            </p>
            <p>
                <u>Fully supported column types:</u>
                <ul>
                    <li><i>Single line of text</i></li>
                    <li><i>Choice</i></li>
                    <li><i>Number</i></li>
                    <li><i>Yes/No</i></li>
                    <li><i>Date and time</i></li>
                </ul>
            </p>
            <p>
                <u>Partially supported column types:</u>
                <ul>
                    <li><i>Multiple lines of text</i> will contain HTML tags if the <i>Use enhanced rich text</i>
                        option is enabled for the column in SharePoint.</li>
                    <li><i>Calculated</i> columns are always Strings.</li>
                    <li><i>Currency</i> columns are always Doubles. Thus the currency information is not displayed.</li>
                    <li><i>Person</i> columns return a row ID in the "User Information List"
                                      (for more details see below).</li>
                    <li><i>Lookup</i> columns return a row ID in linked list (for more details see below).</li>
                </ul>
            </p>
            <p>
                <u>Unsupported column types:</u><br/>
                For all unsupported column types the node will return the raw JSON value as String.
                The String can be transformed into the JSON type in the <i>Transformation</i>
                tab if the <a href="https://kni.me/e/M6ImzgljvpBKaP13">KNIME JSON Processing extension</a>
                is installed.
                <ul>
                    <li><i>Location</i></li>
                    <li><i>Hyperlink</i></li>
                    <li><i>Image</i> columns are encoded as a JSON string containing JSON so you have
                        to convert twice to JSON.</li>
                    <li><i>Task Outcome</i></li>
                    <li><i>External Data</i></li>
                    <li><i>Managed Metadata</i></li>
                </ul>
            </p>
            <p>
                <b>Working with Person and Lookup columns</b><br/>
                <i>Person</i> and <i>Lookup</i> columns reference data from other SharePoint lists.
                Instead of containing the value in the other list they store the value of the "ID" column
                of the row in that list. To be able to access the content you can use another
                <i>SharePoint List Reader</i> to read the lookup list and then use the
                <a href="https://kni.me/n/WzkQfvBXnYxub9hJ">Joiner node</a> to join both lists
                using the "ID" column of the lookup list.
            </p>
            <p>
                If you are dealing with a <i>Lookup</i> column, you can get the name of the column
                you are trying to join from the settings of the <i>Lookup</i> column (which can for
                example be found in the old web interface). The name of any additional columns that
                are looked up can be found in the table specification as names of those columns.
            </p>
            <p>
                <i>Person</i> columns reference entries in the so called "User Information List"
                (with the internal name "users"). This is a hidden system list in the Root of each
                SharePoint site. To find it, you must check the <i>"Show system lists"</i> option
                in the SharePoint List Reader node. The name may be localized to your language
                so you should search for the internal name.
            </p>
            <p>
                <b>Generated Columns</b><br/>
                Apart from the columns you define in a SharePoint list there are some which are
                present in each list e.g. ID. In addition SharePoint generates columns for some
                specific column types such as <i>Location.</i> The Microsoft API does not provide
                information about these columns or any layout information. To deselect unwanted
                columns and reorder the retained columns go to the "Transformation" tab.
            </p>
            """;
    private static final List<PortDescription> INPUT_PORTS = List.of(fixedPort("Credential (JWT)", """
            A JWT credential as provided by the Microsoft Authenticator node.
            """));
    private static final List<PortDescription> OUTPUT_PORTS = List.of(fixedPort("Table", """
            Read SharePoint list as a table.
            """));

    @Override
    public SharepointListReaderNodeModel createNodeModel() {
        return new SharepointListReaderNodeModel();
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<SharepointListReaderNodeModel> createNodeView(final int viewIndex,
            final SharepointListReaderNodeModel nodeModel) {
        return null;
    }

    @Override
    public NodeDialogPane createNodeDialogPane() {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, SharepointListReaderNodeParameters.class);
    }

    @Override
    public NodeDescription createNodeDescription() {
        return DefaultNodeDescriptionUtil.createNodeDescription(NODE_NAME, //
                NODE_ICON, //
                INPUT_PORTS, //
                OUTPUT_PORTS, //
                SHORT_DESCRIPTION, //
                FULL_DESCRIPTION, //
                List.of(), // external resources
                SharepointListReaderNodeParameters.class, //
                null, // node view descriptions
                NodeType.Source, //
                List.of("sharepoint", "microsoft", "list", "office365", "read"), //
                new Version(4, 5, 0));
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, SharepointListReaderNodeParameters.class));
    }
}
