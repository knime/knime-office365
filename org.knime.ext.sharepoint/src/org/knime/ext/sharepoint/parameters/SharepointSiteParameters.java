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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.CustomValidation;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.CustomValidationProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.ValidationCallback;
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
import org.knime.node.parameters.migration.ConfigMigration;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.migration.NodeParametersMigration;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
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
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.Message;
import org.knime.node.parameters.widget.message.TextMessage.MessageType;
import org.knime.node.parameters.widget.text.TextInputWidget;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * SharePoint Site parameters component containing the Sharepoint site (if
 * backwards compatibility is a concern, the variable holding this object has to
 * be called "m_site").
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings({ "javadoc", "restriction" })
public final class SharepointSiteParameters implements NodeParameters {

    // The separator used in the id fields to store multiple data points in it
    // to be able to store both of them (required to be backwards compatibility)
    private static final Pattern SEP = Pattern.compile("\0");

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    static final int DEBOUNCE_TIME = 1500;

    @Section(title = "SharePoint Site")
    interface SiteSection {
    }

    @TextMessage(CredentialMessage.class)
    Void m_message;

    @Widget(title = "SharePoint site", description = "Select how to specify the SharePoint site.")
    @ValueSwitchWidget
    @ValueReference(SiteModeRef.class)
    @Layout(SiteSection.class)
    public SiteMode m_mode = SiteMode.ROOT;

    @Widget(title = "URL", //
            description = "The web URL of the SharePoint site, for example https://mycompany.sharepoint.com")
    @TextInputWidget
    @Layout(SiteSection.class)
    @Effect(predicate = SiteModeIsWebUrl.class, type = EffectType.SHOW)
    @ValueReference(WebUrlRef.class)
    @Migration(WebUrlMigration.class)
    @CustomValidation(value = WebUrlValidation.class)
    public String m_webUrl = "https://company.sharepoint.com";

    @Widget(title = "Group", //
            description = "The Office 365 group whose team site to connect to.")
    @ChoicesProvider(GroupChoicesProvider.class)
    @Layout(SiteSection.class)
    @Effect(predicate = SiteModeIsGroup.class, type = EffectType.SHOW)
    @ValueReference(GroupRef.class)
    @Persistor(GroupMigrationPersistor.class)
    @Migration(GroupMigrationPersistor.class)
    String m_group;

    @Persistor(SubsiteMigrationPersistor.class)
    @Migration(SubsiteMigrationPersistor.class)
    Subsite m_subsite = new Subsite();

    @Override
    public void validate() throws InvalidSettingsException {
        switch (m_mode) {
        case ROOT -> {
            // nothing to check
        }
        case WEB_URL -> validateWebUrl(m_webUrl);
        case GROUP -> {
            if (m_group == null || m_group.isEmpty()) {
                throw new InvalidSettingsException("Please select a group");
            }
        }
        }
        m_subsite.validate();
    }

    private static void validateWebUrl(final String url) throws InvalidSettingsException {
        try {
            CheckUtils.checkSetting(!url.isBlank(), "Please enter a protocol (e.g. \"https://\")");
            GraphApiUtil.getSiteIdFromSharepointSiteWebURL(url);
        } catch (MalformedURLException e) {
            throw new InvalidSettingsException("Please enter a valid URL: " + e.getMessage(), e);
        }
    }

    static final class Subsite implements NodeParameters {
        @Widget(title = "Connect to subsite", description = """
                A (nested) subsite of the SharePoint site specified above. If selected, the node will connect to
                the subsite instead of the parent site. Note that access is limited to content
                of the subsite, not those of the parent site(s).
                """)
        @ChoicesProvider(SubsiteChoicesProvider.class)
        @Layout(SiteSection.class)
        @ValueReference(SubsiteRef.class)
        @OptionalWidget(defaultProvider = SubsiteDefault.class)
        Optional<String> m_displayedSubsite = Optional.empty();

        @ValueProvider(SubsiteProvider.class)
        @ValueReference(LastSubsiteRef.class)
        String m_subsite = "";

        @Override
        public void validate() throws InvalidSettingsException {
            if (m_displayedSubsite.isPresent()) {
                CheckUtils.checkSetting(!m_displayedSubsite.get().isEmpty(), "Please specify a subsite");
            }
        }
    }

    public String getSiteId(final GraphServiceClient<Request> client) throws IOException {
        return new SharepointSiteResolver(client, m_mode, getSubSite(), m_webUrl, getGroupSite()).getTargetSiteId();
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

    static final class CredentialMessage implements StateProvider<Optional<TextMessage.Message>> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
        }

        @Override
        public Optional<Message> computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {
            try {
                createClient(parametersInput);
                return Optional.empty();
            } catch (IOException e) { // NOSONAR only interested in message
                return Optional.of(new Message("Credential unavailable", e.getMessage(), MessageType.WARNING));

            }
        }
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
                        .map(Group.class::cast).map(g -> new StringChoice( //
                                String.join("\0", g.id, Optional.ofNullable(g.displayName).orElse("")), //
                                Optional.ofNullable(g.displayName).orElse(g.id)))
                        .toList();
            } catch (IOException | GraphServiceException ignored) { // NOSONAR avoid log spam in dialog
                // NOSONAR TODO if implemented, show error
                return List.of();
            }
        }
    }

    static final class SubsiteChoicesProvider extends DebouncedChoicesProvider<List<StringChoice>>
            implements StringChoicesProvider {

        private static final AtomicReference<Thread> DEBOUNCE_LOCK = new AtomicReference<>();
        private static final AtomicLong LAST_TIME_CALLED = new AtomicLong();

        private Supplier<SiteMode> m_siteModeSupplier;
        private Supplier<String> m_webUrlSupplier;
        private Supplier<String> m_groupSupplier;

        SubsiteChoicesProvider() {
            super(DEBOUNCE_TIME, DEBOUNCE_LOCK, LAST_TIME_CALLED);
        }

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_siteModeSupplier = initializer.computeFromValueSupplier(SiteModeRef.class);
            m_webUrlSupplier = initializer.computeFromValueSupplier(WebUrlRef.class);
            m_groupSupplier = initializer.computeFromValueSupplier(GroupRef.class);
        }

        @Override
        protected List<StringChoice> computeStateDebounced(final NodeParametersInput context) {
            try {
                final var client = createClient(context);
                final var resolver = new SharepointSiteResolver(client, m_siteModeSupplier.get(), null,
                        m_webUrlSupplier.get(), getId(m_groupSupplier.get()));
                return listSubsites(client, resolver.getParentSiteId(), "");
            } catch (IOException | IllegalStateException | GraphServiceException ignored) { // NOSONAR
                // avoid log spam in dialog
                // NOSONAR TODO if implemented, show error
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

        @Override
        protected List<StringChoice> bounced() {
            return List.of();
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

    static final class WebUrlMigration implements NodeParametersMigration<String> {
        @Override
        public List<ConfigMigration<String>> getConfigMigrations() {
            return List.of(ConfigMigration.builder(s -> s.getString("site")) //
                    .withDeprecatedConfigPath("site").build());
        }
    }

    static final class GroupMigrationPersistor
            implements NodeParametersPersistor<String>, NodeParametersMigration<String> {

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return load(settings, "groupId");
        }

        private static String load(final NodeSettingsRO settings, final String idKey) throws InvalidSettingsException {
            final var id = settings.getString(idKey);
            final var name = settings.getString("groupName");
            if (id.isEmpty() && name.isEmpty()) {
                return "";
            } else {
                return String.join("\0", id, name);
            }
        }

        @Override
        public void save(final String params, final NodeSettingsWO settings) {
            final var fields = SEP.split(params == null ? "" : params, 2);

            settings.addString("groupId", fields[0]);
            settings.addString("groupName", fields.length > 1 ? fields[1] : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { "groupId" }, //
                    { "groupName" }, //
            };
        }

        @Override
        public List<ConfigMigration<String>> getConfigMigrations() {
            return List.of(ConfigMigration.builder(s -> load(s, "group")) //
                    .withDeprecatedConfigPath("group").build());
        }
    }

    static final class SubsiteMigrationPersistor
            implements NodeParametersPersistor<Subsite>, NodeParametersMigration<Subsite> {

        @Override
        public Subsite load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return load(settings, "subsiteId");
        }

        private static Subsite load(final NodeSettingsRO settings, final String idKey) throws InvalidSettingsException {

            final var result = new Subsite();

            final var id = settings.getString(idKey);
            final var name = settings.getString("subsiteName", "");
            if (id.isEmpty() && name.isEmpty()) {
                result.m_subsite = "";
            } else {
                result.m_subsite = String.join("\0", id, name);
            }

            result.m_displayedSubsite = Optional.of(result.m_subsite)
                    .filter(b -> settings.getBoolean("connectToSubsite", false));

            return result;
        }

        @Override
        public void save(final Subsite params, final NodeSettingsWO settings) {
            final var fields = SEP.split(params.m_subsite, 2);
            settings.addBoolean("connectToSubsite", params.m_displayedSubsite.isPresent());

            settings.addString("subsiteId", fields[0]);
            settings.addString("subsiteName", fields.length > 1 ? fields[1] : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { "subsiteId" }, //
                    { "subsiteName" }, //
                    { "connectToSubsite" }, //
            };
        }

        @Override
        public List<ConfigMigration<Subsite>> getConfigMigrations() {
            return List.of(ConfigMigration.builder(s -> load(s, "subsite")) //
                    .withDeprecatedConfigPath("subsite").build());
        }
    }

    static final class SiteModeRef implements ParameterReference<SiteMode> {
    }

    static final class WebUrlRef implements ParameterReference<String> {
    }

    static final class WebUrlValidation implements CustomValidationProvider<String> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public ValidationCallback<String> computeValidationCallback(final NodeParametersInput parametersInput) {
            return SharepointSiteParameters::validateWebUrl;
        }
    }

    static final class SiteModeIsWebUrl implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(SiteModeRef.class).isOneOf(SiteMode.WEB_URL);
        }
    }

    static final class GroupRef implements ParameterReference<String> {
    }

    // Use this provider to keep the actual subsite value up to date with the
    // displayed value
    // We have to use this split for two reasons:
    // * The value isn't lost if the user deselects the checkbox and selects it
    // again
    // * The value can be persisted even if the checkbox is not selected (backwards
    // compatible)
    static final class SubsiteProvider implements StateProvider<String> {

        private Supplier<Optional<String>> m_subsite;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_subsite = initializer.computeFromValueSupplier(SubsiteRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            return m_subsite.get().orElseThrow(StateComputationFailureException::new);
        }
    }

    // Set the default so that the value is restored
    static final class SubsiteDefault implements OptionalWidget.DefaultValueProvider<String> {

        private Supplier<String> m_lastSubsite;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
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
