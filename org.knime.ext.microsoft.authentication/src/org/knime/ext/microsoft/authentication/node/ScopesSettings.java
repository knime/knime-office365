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
 *   2023-08-22 (Alexander Bondaletov, Redfield SE): created
 */
package org.knime.ext.microsoft.authentication.node;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.WidgetGroup;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.api.Migrate;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.api.NodeSettingsPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.api.Persistor;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget.ElementLayout;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.ChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.StringChoice;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.StringChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Predicate;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.PredicateProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Reference;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.ValueReference;
import org.knime.ext.microsoft.authentication.node.MicrosoftAuthenticatorSettings.AuthenticationType;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.CustomScope.CustomScopesPersistor;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.HasAzureStorageScope.IdFieldReference;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.StandardScope.ApplicationScope;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.StandardScope.ApplicationScopePersistor;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.StandardScope.DelegatedScope;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.StandardScope.DelegatedScopePersistor;
import org.knime.ext.microsoft.authentication.scopes.Scope;
import org.knime.ext.microsoft.authentication.scopes.ScopeType;

/**
 * The scopes settings for the Microsoft Authenticator node.
 *
 * @author Alexander Bondaletov, Redfield SE
 */
@SuppressWarnings("restriction")
public class ScopesSettings implements WidgetGroup, DefaultNodeSettings {

    @Widget(title = "Scope type", //
            description = """
                    Scopes are
                    <a href="https://learn.microsoft.com/en-us/entra/identity-platform/permissions-consent-overview#types-of-permissions">
                    permissions</a> that need to be requested during login. They specify what the resulting
                    access token can be used for. This setting defines whether to select scopes from a list of predefined
                    <b>standard</b> scopes or to enter <b>custom</b> scopes manually.
                    """)
    @ValueReference(ScopesSelectionType.Ref.class)
    @ValueSwitchWidget
    ScopesSelectionType m_scopesSelectionType = ScopesSelectionType.STANDARD;

    enum ScopesSelectionType {
        STANDARD, CUSTOM;

        interface Ref extends Reference<ScopesSelectionType> {
        }

        static class IsCustom implements PredicateProvider {
            @Override
            public Predicate init(final PredicateInitializer i) {
                return i.getEnum(Ref.class).isOneOf(CUSTOM);
            }
        }

        static class IsStandard implements PredicateProvider {
            @Override
            public Predicate init(final PredicateInitializer i) {
                return i.getEnum(Ref.class).isOneOf(STANDARD);
            }
        }
    }

    static class HasAzureStorageScope implements PredicateProvider {

        interface ArrayReference extends Reference<DelegatedScope[]> {
        }

        interface IdFieldReference extends Reference<String> {
        }

        @Override
        public Predicate init(final PredicateInitializer i) {
            return i.getArray(ArrayReference.class).containsElementSatisfying(
                    el -> el.getString(IdFieldReference.class).isEqualTo(Scope.AZURE_BLOB_STORAGE.name()));
        }
    }

    static final class HideDelegateScropes implements PredicateProvider {
        @Override
        public Predicate init(final PredicateInitializer i) {
            return i.getPredicate(ScopesSelectionType.IsCustom.class)
                    .or(i.getPredicate(AuthenticationType.IsClientSecret.class));
        }

    }

    @Widget(title = "Standard scopes (delegated permissions)", description = """
            Choose scopes from a predefined list of standard scopes. These scopes are
            <a href="https://learn.microsoft.com/en-us/entra/identity-platform/permissions-consent-overview#types-of-permissions">
            delegated permissions</a> and define what the resulting access token can be used for.
            You can leave the list empty. In this case, the scopes required by downstream nodes need to
            be <a href="https://learn.microsoft.com/en-us/entra/identity-platform/permissions-consent-overview#consent">
            consented</a> to beforehand, otherwise downstream nodes might fail.
            """)
    @ArrayWidget(elementLayout = ElementLayout.HORIZONTAL_SINGLE_LINE, addButtonText = "Add scope")
    @Effect(predicate = HideDelegateScropes.class, type = EffectType.HIDE)
    @ValueReference(HasAzureStorageScope.ArrayReference.class)
    @Persistor(DelegatedScopePersistor.class)
    DelegatedScope[] m_delegatedScopes = new DelegatedScope[0];

    static final class ShowAppScopes implements PredicateProvider {
        @Override
        public Predicate init(final PredicateInitializer i) {
            return i.getPredicate(ScopesSelectionType.IsStandard.class)
                    .and(i.getPredicate(AuthenticationType.IsClientSecret.class));
        }
    }

    @Widget(title = "Standard scopes (application permissions)", description = """
            Choose scopes from a predefined list of standard scopes. These scopes are
            <a href="https://learn.microsoft.com/en-us/entra/identity-platform/permissions-consent-overview#types-of-permissions">
            application permissions</a> and define what the resulting access token can be used for.
            You can leave the list empty. In this case, the scopes required by downstream nodes need to
            be <a href="https://learn.microsoft.com/en-us/entra/identity-platform/permissions-consent-overview#consent">
            consented</a> to beforehand, otherwise downstream nodes might fail.
            """)
    @ArrayWidget(elementLayout = ElementLayout.HORIZONTAL_SINGLE_LINE, addButtonText = "Add scope")
    @Effect(predicate = ShowAppScopes.class, type = EffectType.SHOW)
    @Persistor(ApplicationScopePersistor.class)
    ApplicationScope[] m_appScopes = new ApplicationScope[0];

    abstract static class StandardScope implements DefaultNodeSettings {
        protected abstract String getId();

        abstract static class StandardScopesPersistor<S extends StandardScope> implements NodeSettingsPersistor<S[]> {

            final String m_configKey;

            protected StandardScopesPersistor(final String configKey) {
                m_configKey = configKey;
            }

            @Override
            public void save(final S[] obj, final NodeSettingsWO settings) {
                var strings = Stream.of(obj).map(S::getId).toArray(String[]::new);
                settings.addStringArray(m_configKey, strings);
            }

            @Override
            public String[][] getConfigPaths() {
                return new String[][] { { m_configKey } };
            }
        }

        abstract static class StandardScopesChoicesProvider implements StringChoicesProvider {
            @Override
            public List<StringChoice> computeState(final DefaultNodeSettingsContext context) {
                return Scope.listByScopeType(getScopeType()).stream() //
                        .map(s -> new StringChoice(s.name(), stripHtml(s.getTitle()))) //
                        .toList();
            }

            private static String stripHtml(final String str) {
                return str.replace("<html>", "") //
                        .replace("</html>", "") //
                        .replace("<i>", "") //
                        .replace("</i>", "");
            }

            protected abstract ScopeType getScopeType();
        }

        static class DelegatedScope extends StandardScope {
            @Widget(title = "Scope/permission", description = "")
            @ChoicesProvider(DelegatedScopeChoicesProvider.class)
            @ValueReference(IdFieldReference.class)
            String m_id = "";

            DelegatedScope(final String id) {
                m_id = id;
            }

            DelegatedScope() {
            }

            @Override
            protected String getId() {
                return m_id;
            }
        }

        static class DelegatedScopeChoicesProvider extends StandardScopesChoicesProvider {
            @Override
            protected ScopeType getScopeType() {
                return ScopeType.DELEGATED;
            }

        }

        static class DelegatedScopePersistor extends StandardScopesPersistor<DelegatedScope> {
            DelegatedScopePersistor() {
                super("delegatedScopes");
            }

            @Override
            public DelegatedScope[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
                return Stream.of(settings.getStringArray(m_configKey)) //
                        .map(DelegatedScope::new) //
                        .toArray(DelegatedScope[]::new);
            }
        }

        static class ApplicationScope extends StandardScope {
            @Widget(title = "Scope/permission", description = "")
            @ChoicesProvider(ApplicationScopeChoicesProvider.class)
            String m_id = "";

            ApplicationScope(final String id) {
                m_id = id;
            }

            ApplicationScope() {
            }

            @Override
            protected String getId() {
                return m_id;
            }
        }

        static class ApplicationScopeChoicesProvider extends StandardScopesChoicesProvider {
            @Override
            protected ScopeType getScopeType() {
                return ScopeType.APPLICATION;
            }

        }

        static class ApplicationScopePersistor extends StandardScopesPersistor<ApplicationScope> {
            ApplicationScopePersistor() {
                super("appScopes");
            }

            @Override
            public ApplicationScope[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
                return Stream.of(settings.getStringArray(m_configKey))//
                        .map(ApplicationScope::new) //
                        .toArray(ApplicationScope[]::new);
            }
        }
    }

    @Widget(title = "Custom scopes", description = """
            Enter a list of custom scopes to request during login.
            These scopes are
            <a href="https://learn.microsoft.com/en-us/entra/identity-platform/permissions-consent-overview#types-of-permissions">
            permissions</a> and define what the resulting access token can be used for.
            You can leave the list empty. In this case, the scopes required by downstream nodes need to
            be <a href="https://learn.microsoft.com/en-us/entra/identity-platform/permissions-consent-overview#consent">
            consented</a> to beforehand, otherwise downstream nodes might fail.
            """)
    @ArrayWidget(elementLayout = ElementLayout.HORIZONTAL_SINGLE_LINE, addButtonText = "Add scope")
    @Effect(predicate = ScopesSelectionType.IsCustom.class, type = EffectType.SHOW)
    @Persistor(CustomScopesPersistor.class)
    CustomScope[] m_customScopes = new CustomScope[0];

    static class CustomScope implements DefaultNodeSettings {
        @Widget(title = "Custom scope/permission", description = "")
        String m_scope;

        CustomScope(final String scope) {
            m_scope = scope;
        }

        CustomScope() {
        }

        static class CustomScopesPersistor implements NodeSettingsPersistor<CustomScope[]> {

            static final String CONFIG_KEY = "customScopes";

            @Override
            public CustomScope[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
                return Stream.of(settings.getStringArray(CONFIG_KEY)) //
                        .map(CustomScope::new) //
                        .toArray(CustomScope[]::new);
            }

            @Override
            public void save(final CustomScope[] obj, final NodeSettingsWO settings) {
                var strings = Stream.of(obj).map(s -> s.m_scope).toArray(String[]::new);
                settings.addStringArray(CONFIG_KEY, strings);
            }

            @Override
            public String[][] getConfigPaths() {
                return new String[][] { { CONFIG_KEY } };
            }
        }
    }

    static final class RequireAzureStorageAccount implements PredicateProvider {
        @Override
        public Predicate init(final PredicateInitializer i) {
            return i.getPredicate(AuthenticationType.RequiresDelegatedPermissions.class)
                    .and(i.getPredicate(HasAzureStorageScope.class));
        }

    }

    @Widget(title = "Azure Storage account", //
            description = """
                    If the Azure Blob Storage/Azure Data Lake Storage Gen2 scope is chosen, then this field
                    specifies the specific Azure storage account to request access to.
                    """)
    @Effect(predicate = RequireAzureStorageAccount.class, type = EffectType.SHOW)
    /**
     * added shortly before AP 5.2 code freeze, there might already be example
     * workflows
     */
    @Migrate(loadDefaultIfAbsent = true)
    String m_azureStorageAccount = "";

    /**
     * Validates the settings.
     *
     * @param applicationScopes
     *            Whether the application standard scopes are used or the delegated
     *            ones.
     *
     * @throws InvalidSettingsException
     */
    public void validate(final boolean applicationScopes) throws InvalidSettingsException {// NOSONAR

        if (m_scopesSelectionType == ScopesSelectionType.STANDARD) {
            final var standardScopes = applicationScopes ? m_appScopes : m_delegatedScopes;

            validateScopesAreNotBlank(standardScopes, StandardScope::getId);
            validateStandardScopesGrouping(standardScopes);

            if (!applicationScopes) {
                validateAzureStorageAccountIfNecessary(standardScopes);
            }
        } else {
            validateScopesAreNotBlank(m_customScopes, s -> s.m_scope);
        }
    }

    private static void validateStandardScopesGrouping(final StandardScope[] standardScopes)
            throws InvalidSettingsException {

        Set<Scope> scopes = EnumSet.noneOf(Scope.class);
        for (StandardScope s : standardScopes) {
            var scopeToCheck = Scope.valueOf(s.getId());

            for (Scope scopeToCompare : scopes) {
                if (!scopeToCheck.canBeGroupedWith(scopeToCompare)) {
                    throw new InvalidSettingsException(
                            String.format("The '%s' scope cannot be grouped with '%s' scope.", scopeToCheck.getTitle(),
                                    scopeToCompare.getTitle()));
                }
            }

            scopes.add(scopeToCheck);
        }
    }

    private static <T> void validateScopesAreNotBlank(final T[] scopes, final Function<T, String> toString)
            throws InvalidSettingsException {
        var pos = 1;
        for (final var scope : scopes) {
            if (StringUtils.isBlank(toString.apply(scope))) {
                throw new InvalidSettingsException("Please remove blank scope at position " + pos);
            }

            pos++;
        }
    }

    private void validateAzureStorageAccountIfNecessary(final StandardScope[] standardScopes)
            throws InvalidSettingsException {

        final var azureScope = Scope.AZURE_BLOB_STORAGE.name();
        final var hasAzureStorageScope = Arrays.stream(standardScopes).map(StandardScope::getId)
                .anyMatch(azureScope::equals);
        if (hasAzureStorageScope) {
            CheckUtils.checkSetting(StringUtils.isNotBlank(m_azureStorageAccount),
                    "Please specify an Azure Storage account name when requesting the %s scope.",
                    Scope.AZURE_BLOB_STORAGE.getTitle());
        }
    }

    private String adjustAzureStorageScopeIfNecessary(final Scope scope) {
        if (scope == Scope.AZURE_BLOB_STORAGE) {
            return String.format(scope.getScope(), m_azureStorageAccount);
        } else {
            return scope.getScope();
        }
    }

    /**
     * @param applicationScopes
     *            Whether the application standard scopes are used or the delegated
     *            ones.
     * @return The selected scopes as a set of strings.
     */
    public Set<String> getScopesStringSet(final boolean applicationScopes) {
        if (m_scopesSelectionType == ScopesSelectionType.STANDARD) {
            var standardScopes = applicationScopes ? m_appScopes : m_delegatedScopes;

            // convert the list of chosen standard scopes into strings
            // as part of this we need to modify the string for the AZURE_BLOB_STORAGE scope
            // because it needs to contain the user-provided azure storage account name
            return Stream.of(standardScopes) //
                    .map(StandardScope::getId)//
                    .map(Scope::valueOf)//
                    .map(s -> (!applicationScopes) ? adjustAzureStorageScopeIfNecessary(s) : s.getScope())//
                    .collect(Collectors.toSet());
        } else {
            return Stream.of(m_customScopes) //
                    .map(s -> s.m_scope) //
                    .collect(Collectors.toSet());
        }
    }
}
