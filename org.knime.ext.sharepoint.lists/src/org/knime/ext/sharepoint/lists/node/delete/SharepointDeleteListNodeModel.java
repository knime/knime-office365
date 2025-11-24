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
package org.knime.ext.sharepoint.lists.node.delete;

import java.io.File;
import java.io.IOException;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.impl.WebUINodeModel;
import org.knime.credentials.base.CredentialPortObject;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.lists.node.SharePointListUtils;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Delete SharePoint Online List node implementation of a WebUI NodeModel.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings({ "restriction", "deprecation" })
final class SharepointDeleteListNodeModel extends WebUINodeModel<SharepointDeleteListNodeParameters> {

    protected SharepointDeleteListNodeModel() {
        super(new PortType[] { CredentialPortObject.TYPE }, new PortType[0], SharepointDeleteListNodeParameters.class);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs,
            final SharepointDeleteListNodeParameters params) throws InvalidSettingsException {
        final var credSpec = (CredentialPortObjectSpec) inSpecs[0];
        if (credSpec != null) {
            GraphCredentialUtil.validateCredentialPortObjectSpecOnConfigure(credSpec);
        }

        if (params.m_list.isLegacyAndWebUIDialogNeverOpened()) {
            // do manual checking because of legacy mode
            CheckUtils.checkSetting(legacyListSettingsNonEmpty(params.m_list),
                    "No list selected. Please select a list.");
        }

        return new PortObjectSpec[] {};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec,
            final SharepointDeleteListNodeParameters modelSettings) throws Exception {

        final var credSpec = ((CredentialPortObject) inObjects[0]).getSpec();
        final var client = GraphApiUtil.createClient(//
                GraphCredentialUtil.createAuthenticationProvider(credSpec), //
                modelSettings.m_timeout.getConnectionTimeoutMillis(), //
                modelSettings.m_timeout.getReadTimeoutMillis());

        deleteList(client, modelSettings);

        return new PortObject[] {};
    }

    private static boolean legacyListSettingsNonEmpty(final SharepointListParameters list) {
        return list.getExistingListId() != null || list.getExistingListInternalName() != null
                || list.getExistingListDisplayName() != null;
    }

    private static void deleteList(final GraphServiceClient<Request> client,
            final SharepointDeleteListNodeParameters modelSettings)
            throws ClientException, IOException, InvalidSettingsException {
        final var siteId = getSiteId(client, modelSettings);
        final var listId = getListId(client, siteId, modelSettings.m_list);

        try {
            client.sites(siteId).lists(listId).buildRequest().delete();
        } catch (GraphServiceException ex) {
            throw new IOException("Error during deletion: " + ex.getServiceError().message, ex);
        }
    }

    private static String getListId(final GraphServiceClient<Request> client, final String siteId,
            final SharepointListParameters listParameters) throws IOException, InvalidSettingsException {

        var listId = listParameters.getExistingListId();
        if (listId == null) {
            final var displayName = listParameters.getExistingListDisplayName();
            final var internalName = listParameters.getExistingListInternalName();
            final String listName;
            if (internalName != null) {
                listName = internalName;
            } else if (listParameters.isLegacyAndWebUIDialogNeverOpened() && displayName != null) {
                // backwards compatibility:
                // if the dialog was never opened and legacy combined settings were loaded
                // the display name is filled with the complete settings string if the legacy
                // settings pattern did not match
                listName = displayName;
            } else {
                throw new InvalidSettingsException("Neither list id nor internal name specified.");
            }

            listId = SharePointListUtils.getListIdByInternalName(client, siteId, listName) //
                    .orElseThrow(() -> new InvalidSettingsException(
                            "Could not find a list with internal name: " + listName));
        }

        return listId;
    }

    private static String getSiteId(final GraphServiceClient<Request> client,
            final SharepointDeleteListNodeParameters modelSettings) throws IOException {
        return modelSettings.m_site.getSiteId(client);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        setWarningMessage("SharePoint connection no longer available. Please re-execute the node.");
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
