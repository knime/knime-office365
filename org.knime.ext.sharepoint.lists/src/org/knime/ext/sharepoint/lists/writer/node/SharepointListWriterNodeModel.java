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

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.StringValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeModel;
import org.knime.ext.sharepoint.lists.SharePointListUtils;
import org.knime.ext.sharepoint.lists.writer.KNIMEToSharepointTypeConverter;
import org.knime.ext.sharepoint.lists.writer.SharepointListWriterClient;

/**
 * SharePoint List Writer implementation of a {@link NodeModel}.
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
@SuppressWarnings("restriction")
public class SharepointListWriterNodeModel extends WebUINodeModel<SharepointListWriterNodeSettings> {

    SharepointListWriterNodeModel(final WebUINodeConfiguration configuration) {
        super(configuration, SharepointListWriterNodeSettings.class);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs,
            final SharepointListWriterNodeSettings modelSettings) throws InvalidSettingsException {
        modelSettings.validate();

        final var inputTableSpec = (DataTableSpec) inSpecs[1];

        for (var i = 0; i < inputTableSpec.getNumColumns(); i++) {
            final var colSpec = inputTableSpec.getColumnSpec(i);
            final var colType = colSpec.getType();

            if (!KNIMEToSharepointTypeConverter.TYPE_CONVERTER.containsKey(colType)
                    && !colType.isCompatible(StringValue.class)) {
                throw new InvalidSettingsException(
                        String.format("%s type in column '%s' is not supported", colSpec.getType(), colSpec.getName()));
            }
            final String colName = colSpec.getName();
            if (colName.length() > 255) {
                throw new InvalidSettingsException(
                        "One or more column names do have a length over 255 characters, which is not allowed. Please reduce the length.");
            }
        }
        // TODO: retrieve list id from modelSettings and push it as a flow variable
        pushListId(null);
        return new PortObjectSpec[] {};
    }

    private void pushListId(final String listID) {
        pushFlowVariableString(SharePointListUtils.LIST_ID_VAR_NAME, listID);
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec,
            final SharepointListWriterNodeSettings modelSettings) throws Exception {
        modelSettings.validate();

        final var table = (BufferedDataTable) inObjects[1];
        final var params = modelSettings.getClientParameters(inObjects[0].getSpec());
        try (final var client = new SharepointListWriterClient(params, this::pushListId, table, exec)) {
            client.writeList();
        }
        return new PortObject[] {};
    }
}
