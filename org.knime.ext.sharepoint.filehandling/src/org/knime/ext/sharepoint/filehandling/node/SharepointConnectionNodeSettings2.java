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
 *   2025-08-11 (david): created
 */
package org.knime.ext.sharepoint.filehandling.node;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.internal.button.SimpleButtonWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.WidgetHandlerException;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.settings.SiteMode;
import org.knime.filehandling.core.connections.base.ui.LoadedItemsSelector.IdComboboxItem;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.ButtonReference;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation;

import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.requests.DirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SiteCollectionPage;

import okhttp3.Request;

/**
 * Settings for the WebUI Sharepoint Connection Node.
 *
 * @author David Hickey, TNG Technology Consulting GmbH
 */
final class SharepointConnectionNodeSettings2 implements NodeParameters {

    @Widget(title = "", description = "")
    @Persistor(SiteSettingsLegacyCompatiblePersistor.class)
    SiteSettings m_siteSettings = new SiteSettings();

    @Widget(title = "Connection timeout", description = "")
    @NumberInputWidget(minValidation = MinValidation.IsPositiveIntegerValidation.class)
    @Advanced
    @Persist(configKey = "connectionTimeout")
    int m_connectionTimeout = 20;

    @Widget(title = "Read timeout", description = "")
    @NumberInputWidget(minValidation = MinValidation.IsPositiveIntegerValidation.class)
    @Advanced
    @Persist(configKey = "readTimeout")
    int m_readTimeout = 20;

    static final class SiteSettings implements NodeParameters {
        @Widget(title = "Site type", description = "abc")
        @ValueReference(SharePointSite.Ref.class)
        @ValueSwitchWidget
        SharePointSite m_siteMode;

        @Widget(title = "Site URL", description = "The URL of the SharePoint site to connect to.")
        @Effect(predicate = SharePointSite.IsWebUrl.class, type = EffectType.SHOW)
        @ValueReference(WebUrlRef.class)
        String m_siteUrl;

        @Widget(title = "Group", description = "")
        @Effect(predicate = SharePointSite.IsGroupSite.class, type = EffectType.SHOW)
        @ChoicesProvider(GroupChoicesProvider.class)
        @ValueReference(GroupRef.class)
        String m_group;

        @Widget(title = "Refresh groups", description = "Refresh the list of groups from SharePoint.")
        @Effect(predicate = SharePointSite.IsGroupSite.class, type = EffectType.SHOW)
        @SimpleButtonWidget(ref = RefreshGroupsButton.class)
        Void m_refreshGroupsButton;

        @Widget(title = "Connect to subsite", description = "If enabled, the node will connect to the selected subsite.")
        @ValueReference(ConnectToSubsiteRef.class)
        boolean m_connectToSubsite = false;

        @Widget(title = "Subsite", description = "")
        @ChoicesProvider(SubsitesChoicesProvider.class)
        @ValueReference(SubsiteRef.class)
        @Effect(predicate = ConnectToSubsiteIsTrue.class, type = EffectType.SHOW)
        String m_subsite;

        @Widget(title = "Refresh subsites", description = "Refresh the list of subsites from SharePoint.")
        @SimpleButtonWidget(ref = RefreshSubsitesButton.class)
        @Effect(predicate = ConnectToSubsiteIsTrue.class, type = EffectType.SHOW)
        Void m_refreshSubsitesButton;
    }

    static final class SiteSettingsLegacyCompatiblePersistor implements NodeParametersPersistor<SiteSettings> {

        private static final String KEY_SECTION = "site";

        private static final String KEY_MODE = "mode";

        private static final String KEY_SITE_URL = "site";

        private static final String KEY_GROUP = "groupName";

        private static final String KEY_SUBSITE = "subsite";

        private static final String KEY_CONNECT_TO_SUBSITE = "connectToSubsite";

        @Override
        public SiteSettings load(final NodeSettingsRO settings) throws InvalidSettingsException {
            var output = new SiteSettings();

            var section = settings.getConfig(KEY_SECTION);
            output.m_siteMode = SharePointSite.fromOldConfigValue(section.getString(KEY_MODE));
            output.m_siteUrl = section.getString(KEY_SITE_URL);
            output.m_group = section.getString(KEY_GROUP);
            output.m_connectToSubsite = section.getBoolean(KEY_CONNECT_TO_SUBSITE);
            output.m_subsite = section.getString(KEY_SUBSITE, "");

            return output;
        }

        @Override
        public void save(final SiteSettings param, final NodeSettingsWO settings) {
            var section = settings.addConfig("site");

            section.addString(KEY_MODE, param.m_siteMode.m_oldConfigValue);
            section.addString(KEY_SITE_URL, param.m_siteUrl);
            section.addString(KEY_GROUP, param.m_group);
            section.addBoolean(KEY_CONNECT_TO_SUBSITE, param.m_connectToSubsite);
            section.addString(KEY_SUBSITE, param.m_subsite);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { KEY_SECTION, KEY_MODE }, //
                    { KEY_SECTION, KEY_SITE_URL }, //
                    { KEY_SECTION, KEY_GROUP }, //
                    { KEY_SECTION, KEY_SUBSITE }, //
                    { KEY_SECTION, KEY_CONNECT_TO_SUBSITE } //
            };
        }
    }

    static final class SubsitesChoicesProvider implements StringChoicesProvider {

        private Supplier<SharePointSite> m_modeSupplier;
        private Supplier<String> m_subsiteSupplier;
        private Supplier<String> m_webUrlSupplier;
        private Supplier<String> m_groupSupplier;

        private List<StringChoice> fetchSubsites(final CredentialPortObjectSpec credentialPortSpec,
                final SharePointSite mode, final String subsite, final String webUrl, final String group)
                throws IOException {
            final GraphServiceClient<Request> client = createClient(credentialPortSpec);
            final var siteResolver = new SharepointSiteResolver(client, mode.toLegacySiteMode(), subsite, webUrl,
                    group);
            return listSubsites(siteResolver.getParentSiteId(), client, "");
        }

        private List<StringChoice> listSubsites(final String siteId, final GraphServiceClient<?> client,
                final String prefix) {
            final List<StringChoice> result = new ArrayList<>();

            SiteCollectionPage resp = client.sites(siteId).sites().buildRequest().get();
            for (Site site : resp.getCurrentPage()) {
                result.addAll(processSite(site, client, prefix));
            }

            while (resp.getNextPage() != null) {
                resp = resp.getNextPage().buildRequest().get();
                for (Site site : resp.getCurrentPage()) {
                    result.addAll(processSite(site, client, prefix));
                }
            }

            return result;
        }

        private List<StringChoice> processSite(final Site site, final GraphServiceClient<?> client,
                final String prefix) {
            final List<StringChoice> result = new ArrayList<>();
            final String name = prefix + site.name;

            result.add(new StringChoice(site.id, name));
            result.addAll(listSubsites(site.id, client, prefix + site.name + " > "));
            return result;
        }

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeOnButtonClick(RefreshSubsitesButton.class);

            m_modeSupplier = initializer.getValueSupplier(SharePointSite.Ref.class);
            m_subsiteSupplier = initializer.getValueSupplier(SubsiteRef.class);
            m_webUrlSupplier = initializer.getValueSupplier(WebUrlRef.class);
            m_groupSupplier = initializer.getValueSupplier(GroupRef.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput parametersInput) {
            var credentialPortSpec = parametersInput.getInPortSpec(0) //
                    .map(CredentialPortObjectSpec.class::cast) //
                    .orElseThrow(() -> new WidgetHandlerException("Credential port object is not available."));
            try {
                return fetchSubsites(credentialPortSpec, m_modeSupplier.get(), m_subsiteSupplier.get(),
                        m_webUrlSupplier.get(), m_groupSupplier.get());
            } catch (IOException ex) { // NOSONAR
                throw new WidgetHandlerException("Failed to fetch subsites from SharePoint.");
            }
        }
    }

    static GraphServiceClient<Request> createClient(final CredentialPortObjectSpec credentialPortSpec)
            throws IOException {
        try {
            final var authProvider = GraphCredentialUtil.createAuthenticationProvider(credentialPortSpec);
            return GraphApiUtil.createClient(authProvider, DIALOG_CLIENT_TIMEOUT_MILLIS, DIALOG_CLIENT_TIMEOUT_MILLIS);
        } catch (Exception ex) {
            throw ExceptionUtil.wrapAsIOException(ex);
        }
    }

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    static final class GroupChoicesProvider implements StringChoicesProvider {

        private static List<IdComboboxItem> fetchGroups(final CredentialPortObjectSpec credentialPortSpec)
                throws IOException {
            var client = createClient(credentialPortSpec);

            List<DirectoryObject> objects = new ArrayList<>();
            DirectoryObjectCollectionWithReferencesPage resp = client.me().transitiveMemberOf().buildRequest().get();
            objects.addAll(resp.getCurrentPage());
            while (resp.getNextPage() != null) {
                resp = resp.getNextPage().buildRequest().get();
                objects.addAll(resp.getCurrentPage());
            }

            return objects.stream().filter(o -> GraphApiUtil.GROUP_DATA_TYPE.equals(o.oDataType)).map(Group.class::cast)
                    .map(g -> new IdComboboxItem(g.id, Optional.ofNullable(g.displayName).orElse(g.id)))
                    .collect(toList());
        }

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeOnButtonClick(RefreshGroupsButton.class);
        }

        @Override
        public List<String> choices(final NodeParametersInput parametersInput) {
            var credentialsInput = parametersInput.getInPortSpec(0).map(CredentialPortObjectSpec.class::cast);

            if (credentialsInput.isEmpty()) {
                throw new WidgetHandlerException("Credential port object is not available.");
            }

            try {
                return fetchGroups(credentialsInput.get()).stream() //
                        .map(IdComboboxItem::getTitle) //
                        .collect(toList());
            } catch (IOException ex) { // NOSONAR
                throw new WidgetHandlerException("Failed to fetch groups from SharePoint.");
            }
        }
    }

    static final class RefreshGroupsButton implements ButtonReference {
    }

    static final class RefreshSubsitesButton implements ButtonReference {
    }

    static final class SubsiteRef implements ParameterReference<String> {
    }

    static final class WebUrlRef implements ParameterReference<String> {
    }

    static final class GroupRef implements ParameterReference<String> {
    }

    static final class ConnectToSubsiteRef implements BooleanReference {
    }

    static final class ConnectToSubsiteIsTrue implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getBoolean(ConnectToSubsiteRef.class).isTrue();
        }
    }

    enum SharePointSite {
        ROOT_SITE("ROOT"), //
        WEB_URL("WEB_URL"), //
        GROUP_SITE("GROUP");

        private final String m_oldConfigValue;

        SharePointSite(final String oldConfigValue) {
            m_oldConfigValue = oldConfigValue;
        }

        static class IsWebUrl implements EffectPredicateProvider {
            @Override
            public EffectPredicate init(final PredicateInitializer i) {
                return i.getEnum(Ref.class).isOneOf(WEB_URL);
            }
        }

        static class IsGroupSite implements EffectPredicateProvider {
            @Override
            public EffectPredicate init(final PredicateInitializer i) {
                return i.getEnum(Ref.class).isOneOf(GROUP_SITE);
            }
        }

        static class Ref implements ParameterReference<SharePointSite> {
        }

        static SharePointSite fromOldConfigValue(final String oldConfigValue) {
            return Arrays.stream(values()) //
                    .filter(site -> site.m_oldConfigValue.equals(oldConfigValue)) //
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown SharePoint site mode: " + oldConfigValue));
        }

        SiteMode toLegacySiteMode() {
            return SiteMode.valueOf(m_oldConfigValue);
        }
    }
}
