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
 *   2021-10-06 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint;

import java.io.IOException;

import org.knime.ext.sharepoint.settings.SiteMode;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SiteRequestBuilder;

import okhttp3.Request;

/**
 * Class which resolves the settings of nodes i.e. SharePoint Online connector
 * into a SharePoint site ID.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointSiteResolver {

    private static final String ROOT_SITE = "root";

    private final GraphServiceClient<Request> m_client;

    private final SiteMode m_siteMode;

    private final String m_subSite;

    private final String m_webUrl;

    private final String m_group;

    /**
     * Constructor.
     *
     * @param client
     *            the {@link GraphServiceClient}
     * @param siteMode
     *            the selected {@link SiteMode}
     * @param subSite
     *            the selected Subsite
     * @param webUrl
     *            the selected webUrl
     * @param group
     *            the selected group
     */
    public SharepointSiteResolver(final GraphServiceClient<Request> client, final SiteMode siteMode,
            final String subSite, final String webUrl, final String group) {
        m_client = client;
        m_siteMode = siteMode;
        m_subSite = subSite;
        m_webUrl = webUrl;
        m_group = group;
    }

    /**
     * Returns the selected site or subsite id.
     *
     * @return The site id.
     * @throws IOException
     */
    public String getTargetSiteId() throws IOException {
        return (m_subSite == null || m_subSite.isEmpty()) ? getParentSiteId() : m_subSite;
    }

    /**
     * The selected site id.
     *
     * @return The site id.
     * @throws IOException
     */
    @SuppressWarnings("null")
    public String getParentSiteId() throws IOException {
        SiteRequestBuilder req = null;

        switch (m_siteMode) {
        case ROOT:
            req = m_client.sites(ROOT_SITE);
            break;
        case WEB_URL:
            if (m_webUrl.isBlank()) {
                throw new IllegalStateException("Web URL is not specified.");
            }
            req = m_client.sites(GraphApiUtil.getSiteIdFromSharepointSiteWebURL(m_webUrl));
            break;
        case GROUP:
            if (m_group.isEmpty()) {
                throw new IllegalStateException("Please select a group.");
            }
            req = m_client.groups(m_group).sites(ROOT_SITE);
            break;
        }

        try {
            return req.buildRequest().get().id;
        } catch (ClientException e) {
            throw GraphApiUtil.unwrapIOE(e);
        }
    }
}
