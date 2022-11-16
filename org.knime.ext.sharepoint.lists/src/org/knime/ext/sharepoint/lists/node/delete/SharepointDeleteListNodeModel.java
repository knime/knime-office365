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

import org.apache.commons.lang3.StringUtils;
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
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.lists.node.SharePointListUtils;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.IGraphServiceClient;

/**
 * Delete SharePoint Online List node implementation of a {@link NodeModel}.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class SharepointDeleteListNodeModel extends NodeModel {

    private final SharepointDeleteListConfig m_config;

    protected SharepointDeleteListNodeModel() {
        super(new PortType[] { MicrosoftCredentialPortObject.TYPE }, new PortType[] {});
        m_config = new SharepointDeleteListConfig();
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        final var authPortSpec = (MicrosoftCredentialPortObjectSpec) inSpecs[0];
        m_config.getSharepointListSettings().validateCredential(authPortSpec.getMicrosoftCredential());

        return new PortObjectSpec[] {};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final var authPortSpec = (MicrosoftCredentialPortObjectSpec) inObjects[0].getSpec();

        final var credential = authPortSpec.getMicrosoftCredential();
        if (credential == null) {
            throw new InvalidSettingsException("Not authenticated!");
        }

        CheckUtils.checkSetting(listSettingsEmpty(), "No list selected. Please select a list.");

        final var timeouts = m_config.getSharepointListSettings().getTimeoutSettings();
        final IGraphServiceClient client = GraphApiUtil.createClient(credential, timeouts.getConnectionTimeout(),
                timeouts.getReadTimeout());

        deleteList(client);

        return new PortObject[] {};
    }

    private boolean listSettingsEmpty() {
        return !m_config.getSharepointListSettings().getListSettings().getListNameModel().getStringValue().isEmpty()
                || !m_config.getSharepointListSettings().getListSettings().getListModel().getStringValue().isEmpty();
    }

    /**
     * Deletes a list from SharePoint.
     *
     * @param client
     * @throws ClientException
     * @throws IOException
     * @throws InvalidSettingsException
     */
    private void deleteList(final IGraphServiceClient client)
            throws ClientException, IOException, InvalidSettingsException {
        final var siteId = getSiteId(client);
        final var listId = getListId(client, siteId);

        try {
            client.sites(siteId).lists(listId).buildRequest().delete();
        } catch (GraphServiceException ex) {
            throw new IOException("Error during deletion: " + ex.getServiceError().message, ex);
        }
    }

    private String getListId(final IGraphServiceClient client, final String siteId)
            throws IOException, InvalidSettingsException {

        var listId = m_config.getSharepointListSettings().getListSettings().getListModel().getStringValue();
        if (StringUtils.isBlank(listId)) {
            final var listName = m_config.getSharepointListSettings().getListSettings().getListNameModel()
                    .getStringValue();

            listId = SharePointListUtils.getListIdByName(client, siteId, listName) //
                    .orElseThrow(() -> new InvalidSettingsException("Could not find a list with name: " + listName));
        }

        return listId;
    }

    /**
     * Returns the site id.
     *
     * @param client
     * @return returns the site id
     * @throws IOException
     */
    private String getSiteId(final IGraphServiceClient client) throws IOException {
        final var settings = m_config.getSharepointListSettings().getSiteSettings();
        final var siteResolver = new SharepointSiteResolver(client, settings.getMode(),
                settings.getSubsiteModel().getStringValue(), settings.getWebURLModel().getStringValue(),
                settings.getGroupModel().getStringValue());

        return siteResolver.getTargetSiteId();
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
