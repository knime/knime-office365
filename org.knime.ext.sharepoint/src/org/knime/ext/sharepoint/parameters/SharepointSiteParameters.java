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
import java.util.regex.Pattern;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.util.string.KnimeStringUtils;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.settings.SiteMode;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.OptionalWidget;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.text.TextInputWidget;

import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * SharePoint Site parameters component containing the Sharepoint site.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings({"javadoc", "restriction"})
public final class SharepointSiteParameters implements NodeParameters {

    // The separator used in the id fields to store multiple data points in it
    // to be able to store both of them (required to be backwards compatibility)
    private static final Pattern SEP = Pattern.compile("\0");

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    @Section(title = "SharePoint Site")
    interface SiteSection {
    }

    @Widget(title = "SharePoint site", description = "Select how to specify the SharePoint site.")
    @ValueSwitchWidget
    @ValueReference(SiteModeRef.class)
    @Layout(SiteSection.class)
    @PersistWithin({"site"})
    public SiteMode m_mode = SiteMode.ROOT;

    @Widget(title = "URL", //
            description = "Enter the web URL of the SharePoint site, for example https://mycompany.sharepoint.com")
    @TextInputWidget
    @Layout(SiteSection.class)
    @Effect(predicate = SiteModeIsWebUrl.class, type = EffectType.SHOW)
    @ValueReference(WebUrlRef.class)
    @PersistWithin({"site"})
    @Persist(configKey = "site")
    public String m_webURL = "";

    @Widget(title = "Group", //
            description = "Select the Office 365 group whose team site to connect to")
    @ChoicesProvider(GroupChoicesProvider.class)
    @Layout(SiteSection.class)
    @Effect(predicate = SiteModeIsGroup.class, type = EffectType.SHOW)
    @ValueReference(GroupRef.class)
    @Persistor(GroupPersistor.class)
    String m_group;

    @Persistor(SubsitePersistor.class)
    Subsite m_subsite = new Subsite();

    static class Subsite implements NodeParameters {
        @Widget(title = "Subsite", description = """
                Select a (nested) subsite of the SharePoint site specified above to connect to.
                """)
        @ChoicesProvider(SubsiteChoicesProvider.class)
        @Layout(SiteSection.class)
        @ValueReference(SubsiteRef.class)
        @OptionalWidget(defaultProvider = SubsiteDefault.class)
        Optional<String> m_displayedSubsite = Optional.empty();

        @ValueProvider(SubsiteProvider.class)
        @ValueReference(LastSubsiteRef.class)
        String m_subsite = "";
    }

    public String getSiteId(final GraphServiceClient<Request> client) throws IOException {
        return new SharepointSiteResolver(client, m_mode, getSubSite(), m_webURL, getGroupSite()).getTargetSiteId();
    }

    /**
     * @return the group id
     */
    public String getGroupSite() {
        return getId(m_group);
    }

    /**
     * @return the subsite id
     */
    public String getSubSite() {
        return m_subsite.m_displayedSubsite.map(SharepointSiteParameters::getId).orElse("");
    }

    private static String getId(final String internalValue) {
        return internalValue == null ? "" : internalValue.substring(0, Math.max(0, internalValue.indexOf('\0')));
    }

    static final class GroupChoicesProvider implements StringChoicesProvider {

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            try {
                final var client = createClient(context);
                final var dirObjects = new ArrayList<DirectoryObject>();

                var nextRequest = client.me().transitiveMemberOf();
                while (nextRequest != null) {
                    final var resp = nextRequest.buildRequest().get();
                    dirObjects.addAll(resp.getCurrentPage());
                    nextRequest = resp.getNextPage();
                }
                return dirObjects.stream().filter(o -> GraphApiUtil.GROUP_DATA_TYPE.equals(o.oDataType))
                        .map(Group.class::cast)
                        .map(g -> new StringChoice( //
                                String.join("\0", g.id, Optional.ofNullable(g.displayName).orElse("")), //
                                Optional.ofNullable(g.displayName).orElse(g.id)))
                        .toList();
            } catch (IOException ignored) { // NOSONAR avoid log spam in dialog
                return List.of();
            }
        }
    }

    static final class SubsiteChoicesProvider implements StringChoicesProvider {

        private Supplier<SiteMode> m_siteModeSupplier;
        private Supplier<String> m_webUrlSupplier;
        private Supplier<String> m_groupSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_siteModeSupplier = initializer.computeFromValueSupplier(SiteModeRef.class);
            m_webUrlSupplier = initializer.computeFromValueSupplier(WebUrlRef.class);
            m_groupSupplier = initializer.computeFromValueSupplier(GroupRef.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            try {
                final var client = createClient(context);
                final var resolver = new SharepointSiteResolver(client, m_siteModeSupplier.get(), null,
                        m_webUrlSupplier.get(), getId(m_groupSupplier.get()));
                return listSubsites(client, resolver.getParentSiteId(), "");
            } catch (IOException | IllegalStateException ignored) { // NOSONAR avoid log spam in dialog
                return List.of();
            }
        }

        private static List<StringChoice> listSubsites(final GraphServiceClient<?> client, final String siteId,
                final String prefix) {
            final List<StringChoice> result = new ArrayList<>();

            var nextRequest = client.sites(siteId).sites();

            while (nextRequest != null) {
                final var resp = nextRequest.buildRequest().get();
                for (final var site : resp.getCurrentPage()) {
                    final var name = prefix + site.name;
                    result.add(new StringChoice(String.join("\0", site.id, name), name));
                    result.addAll(listSubsites(client, site.id, name + " > "));
                }
                nextRequest = resp.getNextPage();
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
        } catch (NoSuchCredentialException | IOException ex) {
            throw ExceptionUtil.wrapAsIOException(ex);
        }
    }

    static final class GroupPersistor implements NodeParametersPersistor<String> {

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var site = settings.getNodeSettings("site");

            final var fields = KnimeStringUtils.toEmpty( //
                    site.getString("group", null), //
                    site.getString("groupName", null));
            return String.join("\0", fields);
        }

        @Override
        public void save(final String params, final NodeSettingsWO settings) {
            final var fields = SEP.split(params == null ? "" : params, 2);

            final var site = settings.addNodeSettings("site");
            site.addString("group", fields[0]);
            site.addString("groupName", fields.length > 1 ? fields[1] : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { "site", "group" }, //
                    { "site", "groupName" }, //
            };
        }
    }

    static final class SubsitePersistor implements NodeParametersPersistor<Subsite> {

        @Override
        public Subsite load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var site = settings.getNodeSettings("site");

            final var result = new Subsite();

            final var fields = KnimeStringUtils.toEmpty( //
                    site.getString("subsite", null), //
                    site.getString("subsiteName", null));

            if (fields[0].isEmpty()) {
                result.m_subsite = "";
            } else {
                result.m_subsite = String.join("\0", fields);
            }

            result.m_displayedSubsite = Optional.of(result.m_subsite)
                    .filter(b -> site.getBoolean("connectToSubsite", false));

            return result;
        }

        @Override
        public void save(final Subsite params, final NodeSettingsWO settings) {
            final var site = settings.addNodeSettings("site");
            final var fields = SEP.split(params.m_subsite, 2);
            site.addBoolean("connectToSubsite", params.m_displayedSubsite.isPresent());

            site.addString("subsite", fields[0]);
            site.addString("subsiteName", fields.length > 1 ? fields[1] : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { "site", "subsite" }, //
                    { "site", "subsiteName" }, //
                    { "site", "connectToSubsite" }, //
            };
        }
    }

    static final class SiteModeRef implements ParameterReference<SiteMode> {
    }

    static final class WebUrlRef implements ParameterReference<String> {
    }

    static final class SiteModeIsWebUrl implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(SiteModeRef.class).isOneOf(SiteMode.WEB_URL);
        }
    }

    static final class GroupRef implements ParameterReference<String> {
    }

    // Use this provider to keep the actual subsite value of to date with the
    // displayed value
    // We have to use this split for two reasons:
    // * The value isn't lost if the user deselects the checkbox and selects it
    // again
    // * The value can be persisted even if the checkbox is not selected (backwards
    // compatible)
    static final class SubsiteProvider implements StateProvider<String> {

        private Supplier<Optional<String>> m_subsite;
        private Supplier<String> m_lastSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_subsite = initializer.computeFromValueSupplier(SubsiteRef.class);
            m_lastSupplier = initializer.getValueSupplier(LastSubsiteRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            return m_subsite.get().orElseGet(m_lastSupplier);
        }

    }

    // Set the default so that the value is restored
    static final class SubsiteDefault implements OptionalWidget.DefaultValueProvider<String> {

        private Supplier<String> m_lastSubsite;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_lastSubsite = initializer.computeFromValueSupplier(LastSubsiteRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            return m_lastSubsite.get();
        }

    }

    static final class SiteModeIsGroup implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(SiteModeRef.class).isOneOf(SiteMode.GROUP);
        }
    }

    static final class SubsiteRef implements ParameterReference<Optional<String>> {
    }

    static final class LastSubsiteRef implements ParameterReference<String> {
    }

    public static final class Ref implements ParameterReference<SharepointSiteParameters> {
    }

}
