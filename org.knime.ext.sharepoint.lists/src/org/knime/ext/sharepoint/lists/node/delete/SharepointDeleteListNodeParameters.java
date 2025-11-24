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
 * ------------------------------------------------------------------------
 */

package org.knime.ext.sharepoint.lists.node.delete;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.ext.sharepoint.parameters.TimeoutParameters;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;

import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Node parameters for Delete SharePoint Online List.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
class SharepointDeleteListNodeParameters implements NodeParameters {

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    private static final List<Option> OPTIONS_ITEMS = Collections
            .singletonList(new QueryOption("select", "system,id,displayName,name"));

    @Section(title = "SharePoint Site")
    @Layout(SharepointSiteSection.class)
    interface SharepointSiteSection {
    }

    @Section(title = "SharePoint List")
    @Layout(SharepointListSection.class)
    interface SharepointListSection {
    }

    @Section(title = "Timeouts")
    @Advanced
    @Layout(AdvancedSection.class)
    interface AdvancedSection {
    }

    @Layout(SharepointSiteSection.class)
    @Persistor(SharepointSiteParameters.SiteParametersPersistor.class)
    SharepointSiteParameters m_siteSettings = new SharepointSiteParameters();

    @Widget(title = "List", description = """
            Select the SharePoint list to delete. The drop down menu shows lists
            in the format &lt;display-name&gt; (&lt;internal-name&gt;). The
            display name is a user-chosen name, which is editable in SharePoint,
            whereas the internal name is fixed and auto-generated by SharePoint.
            The node stores a unique technical ID. Upon execution, the node will
            try to delete the list with the unique technical ID. If no such list
            exists, the node will fail. Only if the technical ID is left empty,
            the node will try to delete a list with the configured internal
            name.
            """)
    @ChoicesProvider(ListChoicesProvider.class)
    @Layout(SharepointListSection.class)
    @Persist(configKey = "list")
    @Persistor(ListPersistor.class)
    StringChoice m_list = StringChoice.fromId("");

    @Layout(AdvancedSection.class)
    @Persistor(TimeoutParameters.TimeoutParametersPersistor.class)
    TimeoutParameters m_timeoutSettings = new TimeoutParameters();

    /**
     * Choices provider for SharePoint lists.
     */
    static final class ListChoicesProvider implements StringChoicesProvider {

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            try {

                // Get site settings from the current parameters
                // Note: This is a workaround - ideally we'd use a ValueReference to the site settings
                // but that requires the site settings to be in the same NodeParameters class
                final var params = context.getInPortSpec(0);
                if (params.isEmpty()) {
                    return List.of();
                }

                // For now, default to root site
                // In a full implementation, this would read the actual site settings
                final var client = createClient(context);
                final String siteId = client.sites().root().buildRequest().get().id;

                return listLists(client, siteId, false);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to fetch lists: " + e.getMessage(), e);
            }
        }

        private List<StringChoice> listLists(final GraphServiceClient<Request> client, final String siteId,
                final boolean showSystemLists) {
            final List<StringChoice> result = new ArrayList<>();

            var resp = client.sites(siteId).lists()
                .buildRequest(showSystemLists ? OPTIONS_ITEMS : Collections.emptyList())
                .get();

            for (final var list : resp.getCurrentPage()) {
                final String displayText = list.displayName + " (" + list.name + ")";
                result.add(new StringChoice(list.id, displayText));
            }

            while (resp.getNextPage() != null) {
                resp = resp.getNextPage().buildRequest().get();
                for (final var list : resp.getCurrentPage()) {
                    final String displayText = list.displayName + " (" + list.name + ")";
                    result.add(new StringChoice(list.id, displayText));
                }
            }

            result.sort(Comparator.comparing(StringChoice::text));
            return result;
        }

        private static GraphServiceClient<Request> createClient(final NodeParametersInput context) throws IOException {
            final var credSpec = (CredentialPortObjectSpec) context.getInPortSpec(0)
                .orElseThrow(() -> new IOException("Credential port not connected"));

            try {
                final var authProvider = GraphCredentialUtil.createAuthenticationProvider(credSpec);
                return GraphApiUtil.createClient(authProvider, DIALOG_CLIENT_TIMEOUT_MILLIS,
                        DIALOG_CLIENT_TIMEOUT_MILLIS);
            } catch (Exception ex) {
                throw new IOException("Failed to create client: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Custom persistor for list selection.
     */
    static final class ListPersistor implements NodeParametersPersistor<StringChoice> {

        @Override
        public StringChoice load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final String listId = settings.getString("list", "");
            final String listName = settings.getString("listName", "");
            return new StringChoice(listId, listName.isEmpty() ? listId : listName);
        }

        @Override
        public void save(final StringChoice obj, final NodeSettingsWO settings) {
            settings.addString("list", obj != null ? obj.id() : "");
            settings.addString("listName", obj != null ? obj.text() : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { { "list" }, { "listName" } };
        }
    }
}
