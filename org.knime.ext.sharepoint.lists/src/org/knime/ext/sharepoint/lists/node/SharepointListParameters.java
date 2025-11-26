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
 *   2025-01-21 (Jannik Löscher): created
 */
package org.knime.ext.sharepoint.lists.node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.lists.node.writer.ListOverwritePolicy;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.ChoicesStateProvider;
import org.knime.node.parameters.widget.choices.EnumChoicesProvider;
import org.knime.node.parameters.widget.choices.RadioButtonsWidget;

import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * Reusable SharePoint List parameters component.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class SharepointListParameters implements NodeParameters {
    
    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;
    
    private static final List<Option> OPTIONS_ITEMS = Collections
            .singletonList(new QueryOption("select", "system,id,displayName,name"));
    
    private final boolean m_showSystemListsOption;
    private final boolean m_hasOverwriteOptions;
    private final Supplier<SharepointSiteParameters> m_siteParametersSupplier;
    
    /**
     * Creates new instance.
     */
    public SharepointListParameters() {
        this(true, false, null);
    }
    
    /**
     * Creates new instance with specific settings.
     * 
     * @param showSystemListsOption whether to show the system lists checkbox
     * @param hasOverwriteOptions whether to show overwrite options
     */
    public SharepointListParameters(final boolean showSystemListsOption, final boolean hasOverwriteOptions) {
        this(showSystemListsOption, hasOverwriteOptions, null);
    }
    
    /**
     * Creates new instance with specific settings and site parameters supplier.
     * 
     * @param showSystemListsOption whether to show the system lists checkbox
     * @param hasOverwriteOptions whether to show overwrite options
     * @param siteParametersSupplier supplier for the site parameters
     */
    public SharepointListParameters(final boolean showSystemListsOption, final boolean hasOverwriteOptions,
            final Supplier<SharepointSiteParameters> siteParametersSupplier) {
        m_showSystemListsOption = showSystemListsOption;
        m_hasOverwriteOptions = hasOverwriteOptions;
        m_siteParametersSupplier = siteParametersSupplier;
    }
    
    /*
    @Layout(ListSelectionLayout.class)
    interface ListSelectionLayout {
    }
    
    @Layout(ShowSystemListsLayout.class)
    @After(ListSelectionLayout.class)
    interface ShowSystemListsLayout {
    }
    
    @Layout(OverwriteOptionsLayout.class)
    @After(ShowSystemListsLayout.class)
    interface OverwriteOptionsLayout {
    }
    
    @Widget(title = "List", description = """
            Select the SharePoint list. The drop down menu shows lists in the format &lt;display-name&gt; (&lt;internal-name&gt;). 
            The display name is a user-chosen name, which is editable in SharePoint, whereas the internal name is fixed and 
            auto-generated by SharePoint. In addition to that the node also stores a unique technical ID (not visible). 
            Upon execution, the node will try to use the unique technical ID. If no such list exists, the node will 
            fall back to using the internal name.
            """)
    @ChoicesProvider(ListChoicesProvider.class)
    @Layout(ListSelectionLayout.class)
    IdAndText m_list = new IdAndText("", "");
    
    @Widget(title = "Show system lists", description = "If checked, system lists (e.g., internal SharePoint lists) will be shown in the list selection")
    @CheckboxWidget
    @Layout(ShowSystemListsLayout.class)
    @Effect(predicate = ShowSystemListsPredicate.class, type = EffectType.SHOW)
    boolean m_showSystemLists = false;
    
    static final class ShowSystemListsPredicate implements StateProvider<Boolean> {
        @Override
        public Boolean computeState(final NodeParametersInput context) {
            // This will be set by the constructor parameter
            return true; // Visibility will be controlled by Effect
        }
    }
    
    @Widget(title = "If list exists", description = "Specify how to handle the case when the list already exists")
    @RadioButtonsWidget
    @ChoicesProvider(EnumChoicesProvider.class)
    @Layout(OverwriteOptionsLayout.class)
    @Effect(predicate = HasOverwriteOptionsPredicate.class, type = EffectType.SHOW)
    ListOverwritePolicy m_overwritePolicy = ListOverwritePolicy.FAIL;
    
    static final class HasOverwriteOptionsPredicate implements StateProvider<Boolean> {
        @Override
        public Boolean computeState(final NodeParametersInput context) {
            // This will be set by the constructor parameter
            return true; // Visibility will be controlled by Effect
        }
    }
    
    // Note: This is a marker class - the actual provider will be in the parent NodeParameters
    // where it has access to the site settings
    static final class ListChoicesProvider implements ChoicesStateProvider<IdAndText> {
        
        @Override
        public List<IdAndText> choices(final NodeParametersInput context) {
            // This will be overridden by the parent NodeParameters implementation
            return List.of();
        }
        
        private List<IdAndText> listLists(final GraphServiceClient<Request> client, final String siteId, 
                final boolean showSystemLists) {
            final List<IdAndText> result = new ArrayList<>();
            
            var resp = client.sites(siteId).lists()
                .buildRequest(showSystemLists ? OPTIONS_ITEMS : Collections.emptyList())
                .get();
            
            for (final var list : resp.getCurrentPage()) {
                final String displayText = list.displayName + " (" + list.name + ")";
                result.add(new IdAndText(list.id, displayText));
            }
            
            while (resp.getNextPage() != null) {
                resp = resp.getNextPage().buildRequest().get();
                for (final var list : resp.getCurrentPage()) {
                    final String displayText = list.displayName + " (" + list.name + ")";
                    result.add(new IdAndText(list.id, displayText));
                }
            }
            
            result.sort(Comparator.comparing(IdAndText::text));
            return result;
        }
    }
    
    private static GraphServiceClient<Request> createClient(final NodeParametersInput context) throws IOException {
        final var credSpec = (CredentialPortObjectSpec) context.getInPortSpec(0)
            .orElseThrow(() -> new IOException("Credential port not connected"));
        
        try {
            final var authProvider = GraphCredentialUtil.createAuthenticationProvider(credSpec);
            return GraphApiUtil.createClient(authProvider, DIALOG_CLIENT_TIMEOUT_MILLIS, DIALOG_CLIENT_TIMEOUT_MILLIS);
        } catch (Exception ex) {
            throw new IOException("Failed to create client: " + ex.getMessage(), ex);
        }
    }
    
    /**
     * Custom persistor for SharePoint list parameters.
     * /
    public static final class ListParametersPersistor implements NodeParametersPersistor<SharepointListParameters> {
        
        @Override
        public SharepointListParameters load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var params = new SharepointListParameters();
            
            // Load list ID and name
            final String listId = settings.getString("list", "");
            final String listName = settings.getString("listName", "");
            params.m_list = new IdAndText(listId, listName.isEmpty() ? listId : listName);
            
            // Load show system lists if available
            if (settings.containsKey("showSystemLists")) {
                params.m_showSystemLists = settings.getBoolean("showSystemLists");
            }
            
            // Load overwrite policy if available
            if (settings.containsKey("if_list_exists")) {
                final String policyStr = settings.getString("if_list_exists");
                try {
                    params.m_overwritePolicy = ListOverwritePolicy.valueOf(policyStr);
                } catch (IllegalArgumentException e) {
                    params.m_overwritePolicy = ListOverwritePolicy.FAIL;
                }
            }
            
            return params;
        }
        
        @Override
        public void save(final SharepointListParameters obj, final NodeSettingsWO settings) {
            // Save list ID and name
            settings.addString("list", obj.m_list != null ? obj.m_list.id() : "");
            settings.addString("listName", obj.m_list != null ? obj.m_list.text() : "");
            
            // Save show system lists
            if (obj.m_showSystemListsOption) {
                settings.addBoolean("showSystemLists", obj.m_showSystemLists);
            }
            
            // Save overwrite policy
            if (obj.m_hasOverwriteOptions) {
                settings.addString("if_list_exists", obj.m_overwritePolicy.name());
            }
        }
        
        @Override
        public String[] getConfigPaths() {
            return new String[]{"list", "listName", "showSystemLists", "if_list_exists"};
        }
    }
    */
}
