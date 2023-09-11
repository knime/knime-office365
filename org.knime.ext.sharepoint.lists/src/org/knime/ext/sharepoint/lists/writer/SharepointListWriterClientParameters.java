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
 *   2023-09-10 (Zkriya Rakhimberdiyev, Redfield SE): created
 */
package org.knime.ext.sharepoint.lists.writer;

import org.knime.ext.sharepoint.SharepointSiteResolver;

import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * SharePoint List Writer client parameters.
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
public class SharepointListWriterClientParameters {

    private final ListOverwritePolicy m_overwritePolicy;
    private final SharepointSiteResolver m_siteResolver;
    private final String m_listId;
    private final String m_listName;
    private final GraphServiceClient<Request> m_client;

    /**
     * Constructor.
     *
     * @param overwritePolicy
     *            {@link ListOverwritePolicy}
     * @param siteResolver
     *            {@link SharepointSiteResolver}
     * @param listId
     *            id of list
     * @param listName
     *            name of list
     * @param client
     *            {@link GraphServiceClient}
     */
    public SharepointListWriterClientParameters(final ListOverwritePolicy overwritePolicy,
            final SharepointSiteResolver siteResolver, final String listId, final String listName,
            final GraphServiceClient<Request> client) {
        m_overwritePolicy = overwritePolicy;
        m_siteResolver = siteResolver;
        m_listId = listId;
        m_listName = listName;
        m_client = client;
    }

    /**
     * @return the overwritePolicy
     */
    public ListOverwritePolicy getOverwritePolicy() {
        return m_overwritePolicy;
    }

    /**
     * @return the siteResolver
     */
    public SharepointSiteResolver getSiteResolver() {
        return m_siteResolver;
    }

    /**
     * @return the listId
     */
    public String getListId() {
        return m_listId;
    }

    /**
     * @return the listName
     */
    public String getListName() {
        return m_listName;
    }

    /**
     * @return the client
     */
    public GraphServiceClient<Request> getClient() {
        return m_client;
    }
}
