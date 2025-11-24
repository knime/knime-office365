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
package org.knime.ext.sharepoint.parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.settings.SiteMode;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.WidgetGroup;
import org.knime.node.parameters.layout.HorizontalLayout;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.StateProvider.StateProviderInitializer;
import org.knime.node.parameters.updates.TriggerInitialUpdate;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.OptionalWidget;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.ChoicesStateProvider;
import org.knime.node.parameters.widget.choices.IdAndText;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.RadioButtonsWidget;
import org.knime.node.parameters.widget.text.TextInputWidget;

import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.requests.DirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SiteCollectionPage;

import okhttp3.Request;

/**
 * Reusable SharePoint Site parameters component.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class SharepointSiteParameters implements NodeParameters {
    
    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;
    
    /**
     * Site mode selection enum.
     */
    public enum SiteModeOption {
        @Label("Root site")
        ROOT,
        @Label("Web URL")
        WEB_URL,
        @Label("Group site")
        GROUP
    }
    
    @Layout(SiteModeLayout.class)
    interface SiteModeLayout {
    }
    
    @Layout(SiteSelectionLayout.class)
    interface SiteSelectionLayout {
    }
    
    @Layout(SubsiteLayout.class)
    interface SubsiteLayout {
    }
    
    @Widget(title = "SharePoint site", description = """
            Select how to specify the SharePoint site:
            <ul>
                <li><i>Root site:</i> Connect to the root site of the domain of the current user.</li>
                <li><i>Web URL:</i> Specify the web URL of a SharePoint site.</li>
                <li><i>Group site:</i> Connect to the team site of a particular Office 365 group.</li>
            </ul>
            """)
    @RadioButtonsWidget(horizontal = true)
    @ValueReference(SiteModeRef.class)
    @Layout(SiteModeLayout.class)
    @TriggerInitialUpdate
    SiteModeOption m_siteMode = SiteModeOption.ROOT;
    
    static final class SiteModeRef extends ParameterReference<SiteModeOption> {
    }
    
    @Widget(title = "URL", description = "Enter the web URL of the SharePoint site, for example https://mycompany.sharepoint.com")
    @TextInputWidget
    @Layout(SiteSelectionLayout.class)
    @Effect(predicate = SiteModeIsWebUrl.class, type = EffectType.SHOW)
    @ValueReference(WebUrlRef.class)
    String m_webURL = "";
    
    static final class WebUrlRef extends ParameterReference<String> {
    }
    
    static final class SiteModeIsWebUrl implements StateProvider<Boolean> {
        
        private Supplier<SiteModeOption> m_siteModeSupplier;
        
        @Override
        public void init(final StateProviderInitializer initializer) {
            m_siteModeSupplier = initializer.computeFromValueSupplier(SiteModeRef.class);
        }
        
        @Override
        public Boolean computeState(final NodeParametersInput context) {
            return m_siteModeSupplier.get() == SiteModeOption.WEB_URL;
        }
    }
    
    @Widget(title = "Group", description = "Select the Office 365 group whose team site to connect to")
    @ChoicesProvider(GroupChoicesProvider.class)
    @Layout(SiteSelectionLayout.class)
    @Effect(predicate = SiteModeIsGroup.class, type = EffectType.SHOW)
    @ValueReference(GroupRef.class)
    IdAndText m_group = new IdAndText("", "");
    
    static final class GroupRef extends ParameterReference<IdAndText> {
    }
    
    static final class SiteModeIsGroup implements StateProvider<Boolean> {
        
        private Supplier<SiteModeOption> m_siteModeSupplier;
        
        @Override
        public void init(final StateProviderInitializer initializer) {
            m_siteModeSupplier = initializer.computeFromValueSupplier(SiteModeRef.class);
        }
        
        @Override
        public Boolean computeState(final NodeParametersInput context) {
            return m_siteModeSupplier.get() == SiteModeOption.GROUP;
        }
    }
    
    static final class GroupChoicesProvider implements ChoicesStateProvider<IdAndText> {
        
        @Override
        public List<IdAndText> choices(final NodeParametersInput context) {
            try {
                final var client = createClient(context);
                final List<IdAndText> groups = new ArrayList<>();
                
                DirectoryObjectCollectionWithReferencesPage resp = client.me().transitiveMemberOf().buildRequest().get();
                for (DirectoryObject obj : resp.getCurrentPage()) {
                    if (GraphApiUtil.GROUP_DATA_TYPE.equals(obj.oDataType)) {
                        final Group g = (Group) obj;
                        groups.add(new IdAndText(g.id, Optional.ofNullable(g.displayName).orElse(g.id)));
                    }
                }
                
                while (resp.getNextPage() != null) {
                    resp = resp.getNextPage().buildRequest().get();
                    for (DirectoryObject obj : resp.getCurrentPage()) {
                        if (GraphApiUtil.GROUP_DATA_TYPE.equals(obj.oDataType)) {
                            final Group g = (Group) obj;
                            groups.add(new IdAndText(g.id, Optional.ofNullable(g.displayName).orElse(g.id)));
                        }
                    }
                }
                
                return groups;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to fetch groups: " + e.getMessage(), e);
            }
        }
    }
    
    @Widget(title = "Subsite", description = """
            If checked, connect to a (nested) subsite of the SharePoint site specified above. 
            Note that this allows you only to access the list of the subsite, not those of the parent site(s).
            """)
    @OptionalWidget
    @ChoicesProvider(SubsiteChoicesProvider.class)
    @Layout(SubsiteLayout.class)
    @ValueReference(SubsiteRef.class)
    IdAndText m_subsite = null;
    
    static final class SubsiteRef extends ParameterReference<IdAndText> {
    }
    
    static final class SubsiteChoicesProvider implements ChoicesStateProvider<IdAndText> {
        
        private Supplier<SiteModeOption> m_siteModeSupplier;
        private Supplier<String> m_webUrlSupplier;
        private Supplier<IdAndText> m_groupSupplier;
        
        @Override
        public void init(final StateProviderInitializer initializer) {
            m_siteModeSupplier = initializer.computeFromValueSupplier(SiteModeRef.class);
            m_webUrlSupplier = initializer.computeFromValueSupplier(WebUrlRef.class);
            m_groupSupplier = initializer.computeFromValueSupplier(GroupRef.class);
        }
        
        @Override
        public List<IdAndText> choices(final NodeParametersInput context) {
            try {
                final var client = createClient(context);
                final String parentSiteId = getParentSiteId(client, 
                    m_siteModeSupplier.get(), 
                    m_webUrlSupplier.get(), 
                    m_groupSupplier.get());
                return listSubsites(client, parentSiteId, "");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to fetch subsites: " + e.getMessage(), e);
            }
        }
        
        private List<IdAndText> listSubsites(final GraphServiceClient<?> client, final String siteId, final String prefix) {
            final List<IdAndText> result = new ArrayList<>();
            
            SiteCollectionPage resp = client.sites(siteId).sites().buildRequest().get();
            for (Site site : resp.getCurrentPage()) {
                final String name = prefix + site.name;
                result.add(new IdAndText(site.id, name));
                result.addAll(listSubsites(client, site.id, name + " > "));
            }
            
            while (resp.getNextPage() != null) {
                resp = resp.getNextPage().buildRequest().get();
                for (Site site : resp.getCurrentPage()) {
                    final String name = prefix + site.name;
                    result.add(new IdAndText(site.id, name));
                    result.addAll(listSubsites(client, site.id, name + " > "));
                }
            }
            
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
    
    private static String getParentSiteId(final GraphServiceClient<Request> client, 
            final SiteModeOption mode, final String webUrl, final IdAndText group) throws IOException {
        try {
            switch (mode) {
                case ROOT:
                    return client.sites().root().buildRequest().get().id;
                case WEB_URL:
                    if (webUrl.isEmpty()) {
                        throw new IOException("Web URL is not specified");
                    }
                    return GraphApiUtil.resolveSiteId(client, webUrl);
                case GROUP:
                    if (group == null || group.id().isEmpty()) {
                        throw new IOException("Group is not selected");
                    }
                    return client.groups(group.id()).sites("root").buildRequest().get().id;
                default:
                    throw new IOException("Unknown site mode: " + mode);
            }
        } catch (Exception e) {
            throw new IOException("Failed to resolve site ID: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the target site ID based on current settings.
     * 
     * @param client the Graph client
     * @return the site ID
     * @throws IOException if resolution fails
     */
    public String getTargetSiteId(final GraphServiceClient<Request> client) throws IOException {
        final String parentSiteId = getParentSiteId(client, m_siteMode, m_webURL, m_group);
        if (m_subsite != null && m_subsite.id() != null && !m_subsite.id().isEmpty()) {
            return m_subsite.id();
        }
        return parentSiteId;
    }
    
    /**
     * Custom persistor for SharePoint site parameters.
     */
    public static final class SiteParametersPersistor implements NodeParametersPersistor<SharepointSiteParameters> {
        
        @Override
        public SharepointSiteParameters load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var params = new SharepointSiteParameters();
            final NodeSettingsRO siteSettings = settings.getNodeSettings("site");
            
            // Load site mode
            final String modeStr = siteSettings.getString("mode", SiteMode.ROOT.name());
            try {
                final SiteMode oldMode = SiteMode.valueOf(modeStr);
                params.m_siteMode = switch(oldMode) {
                    case ROOT -> SiteModeOption.ROOT;
                    case WEB_URL -> SiteModeOption.WEB_URL;
                    case GROUP -> SiteModeOption.GROUP;
                };
            } catch (IllegalArgumentException e) {
                params.m_siteMode = SiteModeOption.ROOT;
            }
            
            // Load web URL
            params.m_webURL = siteSettings.getString("site", "");
            
            // Load group
            final String groupId = siteSettings.getString("group", "");
            final String groupName = siteSettings.getString("groupName", "");
            params.m_group = new IdAndText(groupId, groupName.isEmpty() ? groupId : groupName);
            
            // Load subsite
            final boolean connectToSubsite = siteSettings.getBoolean("connectToSubsite", false);
            if (connectToSubsite) {
                final String subsiteId = siteSettings.getString("subsite", "");
                final String subsiteName = siteSettings.getString("subsiteName", "");
                params.m_subsite = new IdAndText(subsiteId, subsiteName.isEmpty() ? subsiteId : subsiteName);
            } else {
                params.m_subsite = null;
            }
            
            return params;
        }
        
        @Override
        public void save(final SharepointSiteParameters obj, final NodeSettingsWO settings) {
            final NodeSettingsWO siteSettings = settings.addNodeSettings("site");
            
            // Save site mode
            final SiteMode oldMode = switch(obj.m_siteMode) {
                case ROOT -> SiteMode.ROOT;
                case WEB_URL -> SiteMode.WEB_URL;
                case GROUP -> SiteMode.GROUP;
            };
            siteSettings.addString("mode", oldMode.name());
            
            // Save web URL
            siteSettings.addString("site", obj.m_webURL);
            
            // Save group
            siteSettings.addString("group", obj.m_group != null ? obj.m_group.id() : "");
            siteSettings.addString("groupName", obj.m_group != null ? obj.m_group.text() : "");
            
            // Save subsite
            final boolean connectToSubsite = obj.m_subsite != null;
            siteSettings.addBoolean("connectToSubsite", connectToSubsite);
            siteSettings.addString("subsite", connectToSubsite ? obj.m_subsite.id() : "");
            siteSettings.addString("subsiteName", connectToSubsite ? obj.m_subsite.text() : "");
        }
        
        @Override
        public String[] getConfigPaths() {
            return new String[]{"site"};
        }
    }
}
