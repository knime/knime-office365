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

import java.io.File;
import java.io.IOException;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.StringValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.credentials.base.CredentialPortObject;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.lists.node.KNIMEToSharepointTypeConverter;
import org.knime.ext.sharepoint.lists.node.SharepointListChangingClient;

/**
 * SharePoint Online List Updater node implementation of a {@link NodeModel}.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
final class SharepointListUpdaterNodeModel extends NodeModel {

    private final SharepointListUpdaterConfig m_config;

    protected SharepointListUpdaterNodeModel() {
        super(new PortType[] { CredentialPortObject.TYPE, BufferedDataTable.TYPE }, new PortType[] {});
        m_config = new SharepointListUpdaterConfig();
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final var credSpec = (CredentialPortObjectSpec) inSpecs[0];
        if (credSpec != null) {
            GraphCredentialUtil.validateCredentialPortObjectSpecOnConfigure(credSpec);
        }

        final var inputTableSpec = (DataTableSpec) inSpecs[1];

        CheckUtils.checkSetting(listSettingsNonEmpty(), "No list selected. Please select a list.");

        final var colId = m_config.getIdColNameModel();

        if (!colId.useRowID()) {
            CheckUtils.checkSetting(inputTableSpec.containsName(colId.getColumnName()),
                    "Please select a column containing the item ID");
            CheckUtils.checkSetting(
                    inputTableSpec.getColumnSpec(colId.getColumnName()).getType().isCompatible(StringValue.class),
                    "Column containing the item ID is not string compatible");
        }

        final var idColIdx = inputTableSpec.findColumnIndex(colId.getColumnName());

        for (var i = 0; i < inputTableSpec.getNumColumns(); i++) {
            if (i == idColIdx) {
                continue;
            }

            final var colSpec = inputTableSpec.getColumnSpec(i);
            final var colType = colSpec.getType();

            if (!KNIMEToSharepointTypeConverter.supportsType(colType) && !colType.isCompatible(StringValue.class)) {
                throw new InvalidSettingsException(
                        String.format("%s type in column '%s' is not supported", colSpec.getType(), colSpec.getName()));
            }

            final String colName = colSpec.getName();
            // _In theory_, display names can be longer than that as long as the
            // internal name is shorter (we make sure it is)
            // However, the online UI for editing columns does not seem to be
            // able to handle it so we restrict it here
            if (colName.length() > 255) {
                throw new InvalidSettingsException("One or more column names do have a length over 255 characters, "
                        + "which is not allowed. Please reduce the length.");
            }
        }

        return new PortObjectSpec[] {};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        final var credSpec = ((CredentialPortObject) inObjects[0]).getSpec();
        final var table = (BufferedDataTable) inObjects[1];

        try (final var client = new SharepointListChangingClient(m_config.getSharepointListSettings(), false, null,
                table, credSpec, exec)) {
            client.updateList(m_config.getIdColName());
        }

        return new PortObject[] {};
    }

    private boolean listSettingsNonEmpty() {
        return !m_config.getSharepointListSettings().getListSettings().getListNameModel().getStringValue().isEmpty()
                || !m_config.getSharepointListSettings().getListSettings().getListModel().getStringValue().isEmpty();
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_config.saveSettings(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.validateSettings(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config.loadSettings(settings);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        setWarningMessage("Sharepoint connection no longer available. Please re-execute the node.");
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // nothing to do
    }

    @Override
    protected void reset() {
        // nothing to do
    }
}
