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
 *   14 Feb 2022 (Lars Schweikardt, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.lists.node.updater;

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

/**
 * SharePoint Online List Updater implementation of a {@link NodeFactory}.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings({ "restriction", "removal" })
public class SharepointListUpdaterNodeFactory extends NodeFactory<SharepointListUpdaterNodeModel>
        implements NodeDialogFactory, KaiNodeInterfaceFactory {

    private static final String FULL_DESCRIPTION = """
            <p>
                Updates rows in a SharePoint Online list based on their ID.
            </p>
            <p>
                <i>Notes:</i>
                <ul>
                    <li>
                        Updating more than a few hundred items can take long time.
                    </li>
                    <li>
                        When updating a list, the name of the input table columns are matched to the display names of
                        the respective columns in the SharePoint list. In this process read-only columns are ignored.
                        If multiple list columns have the same display name, the first will be chosen. The node will
                        fail if there is no list column matching a specific table column or if a required list column
                        is not present in the input table.<br />
                        <u>This node will not perform any type checking when appending values.</u> Unlike the specific
                        error messages when writing a new row, the SharePoint API only returns a generic
                        <i>400 Invalid Request</i> code if the types in the existing list do not match the types this
                        node tries to write. Please check the input types if you receive such an error.
                    </li>
                    <li>
                        Any list columns which are not present in the table will be left as they are. A missing value
                        will clear the respective cell.
                    </li>
                </ul>
            </p>
            <p>
                <b>KNIME column type support</b><br />
            </p>
            <p>
                <u>Fully supported KNIME column types:</u>
                <ul>
                    <li>
                        <i>String and string-compatible columns</i> map to
                        <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Multiple lines of text</i>
                        </a>
                    </li>
                    <li>
                        <i>Integer columns</i> map to
                        <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Number</i>
                        </a>
                    </li>
                    <li>
                        <i>Boolean columns</i> map to
                        <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Yes/no</i>
                        </a>
                    </li>
                </ul>
            </p>
            <p>
                <u>Partially supported KNIME columns types:</u>
                <ul>
                    <li>
                        <i>Long columns</i> map to
                        <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Number</i>
                        </a>.
                        The node will fail for more than 15 significant digits (digits which remain after leading and
                        trailing zeros are removed, i.e. in "401220500000" the digits "4012205" are significant and
                        in "0.0004050114" the digits "4050114" are significant).
                    </li>
                    <li>
                        <i>Double columns</i> map to
                        <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Number</i>
                        </a>.
                        The node will fail for infinity, NaN and values outside of [1.79E308,-1.79E308], as well as
                        for numbers with more than 15 significant digits.
                    </li>
                    <li>
                        <i>Local Date and Local Date Time columns</i> map to
                        <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Date and Time</i>
                        </a>, which stores a UTC timestamp with second-precision. Hence, values from KNIME are mapped
                        to an instant in UTC and truncated to second-precision. For example, 2022-02-02 08:00:00 is
                        stored as 2022-02-02 08:00:00 UTC. The node will fail for values before 1900-01-01 00:00:00
                        UTC and after 8900-12-31 23:59:59 UTC.
                    </li>
                    <li>
                        <i>Zoned Date Time columns</i> map to
                        <a href="https://support.microsoft.com/en-us/office/list-and-library-column-types-and-options\
            -0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Date and Time</i>
                        </a>, which stores a UTC timestamp with second-precision. Hence, Zoned Date Time values are
                        converted to the UTC timezone and truncated to second-precision. For example, 2022-02-02
                        08:00:00 CET is stored as 2022-02-02 07:00:00 UTC. The node will fail for values before
                        1900-01-01 00:00:00 UTC and after 8900-12-31 23:59:59 UTC.
                    </li>
                </ul>
            </p>
            <p>
                <u>Unsupported KNIME column types:</u><br />
                All column types that do not belong to the above categories are unsupported and the node cannot be
                executed, since there is no corresponding type in SharePoint Online.
            </p>
            """;

    @Override
    public SharepointListUpdaterNodeModel createNodeModel() {
        return new SharepointListUpdaterNodeModel();
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
    public NodeDialogPane createNodeDialogPane() {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, SharepointListUpdaterNodeParameters.class);
    }

    @Override
    public NodeDescription createNodeDescription() {
        return DefaultNodeDescriptionUtil.createNodeDescription( //
                "SharePoint Online List Updater", //
                "./sharepoint-list-updater.png", //
                List.of(fixedPort("Credential (JWT)", //
                        "A JWT credential as provided by the Microsoft Authenticator node."), //
                        fixedPort("Table", "The table to be written to SharePoint.")), //
                List.of(), // output ports
                "Updates a SharePoint Online list.", //
                FULL_DESCRIPTION, //
                List.of(), // external resources
                SharepointListUpdaterNodeParameters.class, null, // node view descriptions
                NodeType.Sink, //
                List.of("sharepoint", "microsoft", "list", "office365"), //
                new Version(5, 8, 1));
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, SharepointListUpdaterNodeParameters.class));
    }

    @Override
    public NodeView<SharepointListUpdaterNodeModel> createNodeView(final int viewIndex,
            final SharepointListUpdaterNodeModel nodeModel) {
        return null;
    }

}
