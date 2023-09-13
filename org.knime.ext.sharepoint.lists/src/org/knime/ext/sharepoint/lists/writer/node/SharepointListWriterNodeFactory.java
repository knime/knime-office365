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
 *   2023-09-09 (Zkriya Rakhimberdiyev, Redfield SE): created
 */
package org.knime.ext.sharepoint.lists.writer.node;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.NodeFactory;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeFactory;
import org.knime.credentials.base.CredentialPortObject;

/**
 * SharePoint List Writer implementation of a {@link NodeFactory}.
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
@SuppressWarnings("restriction")
public class SharepointListWriterNodeFactory extends WebUINodeFactory<SharepointListWriterNodeModel> {

    private static final String FULL_DESCRIPTION = """
                <p>This node writes a KNIME table to a SharePoint list.</p>
                <p>
                    <i>Notes:</i>
                    <ul>
                        <li>
                            Writing tables with more than a few hundred rows can take long time.
                            Overwriting an existing table
                            can be also very slow in case a lot of rows need to be deleted.
                        </li>
                        <li>
                            This node exports the ID of the written list to a flow
                            variable called <tt>sharepoint_list_id</tt>. The ID can be
                            be used in subsequent nodes to control the <tt>list</tt> setting via flow variable.
                        </li>
                    </ul>
                </p>
                <p>
                    <b>KNIME column type support</b><br/>
                </p>
                <p>
                    <u>Fully supported KNIME column types:</u>
                    <ul>
                        <li>
                            <i>String and string-compatible columns</i> map to
                            <a href="https://support.microsoft.com/en-us/office/
                            list-and-library-column-types-and-options-0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                                <i>Multiple lines of text</i>
                            </a>
                        </li>
                        <li>
                            <i>Integer columns</i> map to
                            <a href="https://support.microsoft.com/en-us/office/
                            list-and-library-column-types-and-options-0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                                <i>Number</i>
                            </a>
                        </li>
                        <li>
                            <i>Boolean columns</i> map to
                            <a href="https://support.microsoft.com/en-us/office/
                            list-and-library-column-types-and-options-0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
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
                            <a href="https://support.microsoft.com/en-us/office/
                            list-and-library-column-types-and-options-0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                                <i>Number</i>
                            </a>.
                            The node will fail for more than 15 significant digits
                            (digits which remain after leading and trailing zeros are
                            removed, i.e. in "401220500000" the digits "4012205" are
                            significant and in "0.0004050114" the digits "4050114" are
                            significant).
                        </li>
                        <li>
                            <i>Double columns</i> map to
                            <a href="https://support.microsoft.com/en-us/office/
                            list-and-library-column-types-and-options-0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                                <i>Number</i>
                            </a>.
                            The node will fail for infinity, NaN and values outside of
                            [1.79E308,-1.79E308], as well as for numbers with more than
                            15 significant digits.
                        </li>
                        <li>
                            <i>Local Date and Local Date Time columns</i> map to
                            <a href="https://support.microsoft.com/en-us/office/
                            list-and-library-column-types-and-options-0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Date and Time</i></a>, which stores a UTC timestamp with second-precision.
                            Hence, values from KNIME are mapped to an instant in UTC
                            and truncated to second-precision.
                            For example, 2022-02-02 08:00:00 is stored as 2022-02-02 08:00:00 UTC.
                            The node will fail for values before 1900-01-01 00:00:00 UTC and
                            after 8900-12-31 23:59:59 UTC.
                        </li>
                        <li>
                            <i>Zoned Date Time columns</i> map to
                            <a href="https://support.microsoft.com/en-us/office/
                            list-and-library-column-types-and-options-0d8ddb7b-7dc7-414d-a283-ee9dca891df7">
                            <i>Date and Time</i></a>, which stores a UTC timestamp with second-precision.
                            Hence, Zoned Date Time values are converted to the UTC timezone
                            and truncated to second-precision.
                            For example, 2022-02-02 08:00:00 CET is stored as 2022-02-02 07:00:00 UTC.
                            The node will fail for values before 1900-01-01 00:00:00 UTC
                            and after 8900-12-31 23:59:59 UTC.
                        </li>
                    </ul>
                </p>
                <p>
                    <u>Unsupported KNIME column types:</u><br/>
                    All column types that do not belong to the above categories are unsupported
                    and the node cannot be executed, since there is no corresponding type in SharePoint Online.
                </p>
            """;

    private static final WebUINodeConfiguration CONFIG = WebUINodeConfiguration.builder()//
            .name("SharePoint Online List Writer")//
            .icon("./sharepoint-list-writer.png")//
            .shortDescription("Writes a SharePoint Online list.")//
            .fullDescription(FULL_DESCRIPTION) //
            .modelSettingsClass(SharepointListWriterNodeSettings.class)//
            .addInputPort("Credential (JWT)", CredentialPortObject.TYPE,
                    "A JWT credential as provided by the Microsoft Authenticator node.")//
            .addInputPort("Table", BufferedDataTable.TYPE, "The table to be written to SharePoint.")//
            .sinceVersion(5, 2, 0).build();

    /**
     * Constructor.
     */
    public SharepointListWriterNodeFactory() {
        super(CONFIG);
    }

    @Override
    public SharepointListWriterNodeModel createNodeModel() {
        return new SharepointListWriterNodeModel(CONFIG);
    }
}
