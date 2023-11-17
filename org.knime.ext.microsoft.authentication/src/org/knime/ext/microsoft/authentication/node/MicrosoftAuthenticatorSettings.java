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
 *   2023-08-20 (Alexander Bondaletov, Redfield SE): created
 */
package org.knime.ext.microsoft.authentication.node;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.After;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.field.Persist;
import org.knime.core.webui.node.dialog.defaultdialog.rule.And;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.rule.OneOfEnumCondition;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Or;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Signal;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.Credentials;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.button.ButtonWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.button.CancelableActionHandler;
import org.knime.core.webui.node.dialog.defaultdialog.widget.credentials.CredentialsWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.credentials.PasswordWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.WidgetHandlerException;
import org.knime.credentials.base.CredentialCache;
import org.knime.credentials.base.oauth.api.nodesettings.TokenCacheKeyPersistor;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.CustomOpenBrowserAction;
import org.knime.ext.microsoft.authentication.util.MSALUtil;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.SystemBrowserOptions;

/**
 * The settings for the Microsoft Authenticator node.
 *
 * @author Alexander Bondaletov, Redfield SE
 */
@SuppressWarnings("restriction")
public class MicrosoftAuthenticatorSettings implements DefaultNodeSettings {
    private static final NodeLogger LOG = NodeLogger.getLogger(MicrosoftAuthenticatorSettings.class);

    private static final String DEFAULT_REDIRECT_URL = "http://localhost:51355/";

    @Section(title = "Username and Password")
    @Effect(signals = AuthenticationType.IsUsernamePassword.class, type = EffectType.SHOW)
    interface UsernamePasswordSection {
    }

    @Section(title = "Client/App")
    @After(UsernamePasswordSection.class)
    @Effect(signals = AuthenticationType.IsClientSecret.class, type = EffectType.SHOW)
    interface ClientAppAndSecretSection {
    }

    @Section(title = "Shared Key")
    @After(ClientAppAndSecretSection.class)
    @Effect(signals = AuthenticationType.IsAzureStorageSharedKey.class, type = EffectType.SHOW)
    interface SharedKeySection {
    }

    @Section(title = "Shared access signature (SAS)")
    @After(SharedKeySection.class)
    @Effect(signals = AuthenticationType.IsAzureStorageSasUrl.class, type = EffectType.SHOW)
    interface SasUrlSection {
    }

    @Section(title = "Scopes of access")
    @After(SasUrlSection.class)
    @Effect(signals = { AuthenticationType.IsInteractive.class, //
            AuthenticationType.IsUsernamePassword.class, //
            AuthenticationType.IsClientSecret.class }, type = EffectType.SHOW, operation = Or.class)
    interface ScopesSection {
    }

    @Section(title = "Authorization endpoint", advanced = true)
    @After(ScopesSection.class)
    @Effect(signals = { AuthenticationType.IsInteractive.class,
            AuthenticationType.IsUsernamePassword.class }, type = EffectType.SHOW, operation = Or.class)
    interface AuthorizationEndpointSection {
    }

    @Section(title = "Client/App", advanced = true)
    @After(AuthorizationEndpointSection.class)
    @Effect(signals = { AuthenticationType.IsInteractive.class,
            AuthenticationType.IsUsernamePassword.class }, type = EffectType.SHOW, operation = Or.class)
    interface ClientApplicationSection {
    }

    @Section(title = "Authentication")
    @After(ClientApplicationSection.class)
    @Effect(signals = AuthenticationType.IsInteractive.class, type = EffectType.SHOW)
    interface AuthenticationSection {
    }

    @Widget(title = "Authentication type", //
            description = """
                    Authentication type to use. The following types are supported:
                        <ul>
                        <li>
                            <b>Interactive (OAuth 2)</b>: Interactive <i>user login</i> in your web browser, when you
                            click on <i>Login</i>. In the browser window that pops up, you may be asked to consent to
                            the scopes of access. The acquired access token typically will only be valid for a short
                            amount of time, then you need to login again. Technically, this uses the
                            <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-auth-code-flow">
                            OAuth 2.0 authorization code flow</a>.
                        </li>
                        <li>
                            <b>Username/Password (OAuth 2</b>: Non-interactive <i>user login</i>. This is well-suited for
                            workflows on KNIME
                            Hub, however you cannot to consent to the scopes of access, hence consent must be given
                            beforehand, e.g. during a previous interactive login, or by an Azure AD directory admin.
                            Second, accounts that require multi-factor authentication (MFA) will not work.
                            Technically, this uses the
                            <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth-ropc">
                            OAuth 2.0 Resource Owner Password Credentials flow</a>.
                        </li>
                        <li>
                            <b>Client/Application secret (OAuth 2)</b>: Non-interactive login as the
                            service principal of the configured client/app. This is well-suited for workflows on KNIME
                            Hub.
                            Technically, this uses the
                            <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-client-creds-grant-flow">
                            OAuth 2.0 client credentials flow</a>. MicrosoftAzure cloud services are accessed on
                            behalf of the application's service principal, not on behalf of a user
                            <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/scenario-daemon-overview">
                            (see here)</a>.
                        </li>
                        <li>
                            <b>Azure storage shared key</b>: Authenticates using an Azure storage account and
                            its secret key. Can only be used to access Azure Blob Storage and Azure Data Lake Storage
                            Gen2.
                        </li>
                        <li>
                            <b>Azure Storage shared access signature (SAS)</b>: Authenticates using a shared access
                            signature (SAS). Can only be used to access Azure Blob Storage and Azure Data Lake Storage
                            Gen2. For more details on shared access signatures see
                            <a href="https://docs.microsoft.com/en-us/azure/storage/common/storage-sas-overview">
                            here.</a>
                        </li>
                    </ul>
                    """)
    @Signal(condition = AuthenticationType.IsInteractive.class)
    @Signal(condition = AuthenticationType.IsUsernamePassword.class)
    @Signal(condition = AuthenticationType.IsClientSecret.class)
    @Signal(condition = AuthenticationType.IsAzureStorageSharedKey.class)
    @Signal(condition = AuthenticationType.IsAzureStorageSasUrl.class)
    AuthenticationType m_authenticationType = AuthenticationType.INTERACTIVE;

    enum AuthenticationType {
        @Label("Interactive (OAuth 2)")
        INTERACTIVE,

        @Label("Username/Password (OAuth 2)")
        USERNAME_PASSWORD,

        @Label("Client/Application secret (OAuth 2)")
        CLIENT_SECRET,

        @Label("Azure Storage shared key")
        AZURE_STORAGE_SHARED_KEY,

        @Label("Azure Storage shared access signature (SAS)")
        AZURE_STORAGE_SAS_URL;

        static class IsInteractive extends OneOfEnumCondition<AuthenticationType> {
            @Override
            public AuthenticationType[] oneOf() {
                return new AuthenticationType[] { INTERACTIVE };
            }
        }

        static class IsUsernamePassword extends OneOfEnumCondition<AuthenticationType> {
            @Override
            public AuthenticationType[] oneOf() {
                return new AuthenticationType[] { USERNAME_PASSWORD };
            }
        }

        static class IsClientSecret extends OneOfEnumCondition<AuthenticationType> {
            @Override
            public AuthenticationType[] oneOf() {
                return new AuthenticationType[] { CLIENT_SECRET };
            }
        }

        static class IsAzureStorageSharedKey extends OneOfEnumCondition<AuthenticationType> {
            @Override
            public AuthenticationType[] oneOf() {
                return new AuthenticationType[] { AZURE_STORAGE_SHARED_KEY };
            }
        }

        static class IsAzureStorageSasUrl extends OneOfEnumCondition<AuthenticationType> {
            @Override
            public AuthenticationType[] oneOf() {
                return new AuthenticationType[] { AZURE_STORAGE_SAS_URL };
            }
        }
    }

    @Widget(title = "Azure tenant ID (or domain)", description = """
            The directory tenant the application plans to operate against, in ID or domain-name format,
            for example <i>cf47ff49-7da6-4603-b339-f4475176432b,</i> or <i>mycompany.onmicrosoft.com.</i>
            """)
    @Effect(signals = AuthenticationType.IsClientSecret.class, type = EffectType.SHOW)
    String m_tenantId;

    @Widget(title = "Username and password", description = "The username and password to use.")
    @CredentialsWidget
    @Layout(UsernamePasswordSection.class)
    Credentials m_usernamePassword = new Credentials();

    @Widget(title = "Client/App ID and secret", //
            description = "The client/app ID and secret to use.")
    @CredentialsWidget(usernameLabel = "Client/App ID", passwordLabel = "Client/App Secret")
    @Layout(ClientAppAndSecretSection.class)
    Credentials m_confidentialApp = new Credentials();

    @Layout(ScopesSection.class)
    ScopesSettings m_scopesSettings = new ScopesSettings();

    @Widget(title = "Which authorization endpoint to use", //
            description = "Whether to use the Microsoft default authorization endpoint, or a custom one.")
    @ValueSwitchWidget
    @Layout(AuthorizationEndpointSection.class)
    @Signal(condition = AuthorizationEndpointSelection.IsCustom.class)
    AuthorizationEndpointSelection m_authorizationEndpointSelection = AuthorizationEndpointSelection.DEFAULT;

    enum AuthorizationEndpointSelection {
        DEFAULT, CUSTOM;

        static class IsCustom extends OneOfEnumCondition<AuthorizationEndpointSelection> {
            @Override
            public AuthorizationEndpointSelection[] oneOf() {
                return new AuthorizationEndpointSelection[] { CUSTOM };
            }
        }
    }

    @Widget(title = "Endpoint URL", description = "Custom authorization endpoint URL to use.")
    @Layout(AuthorizationEndpointSection.class)
    @Effect(signals = AuthorizationEndpointSelection.IsCustom.class, type = EffectType.SHOW)
    String m_authorizationEndpointUrl = "";

    @Widget(title = "Which client/app to use", //
            description = """
                    Whether to use the KNIME default app, or enter a custom one. The
                    KNIME default app is called "KNIME Analytics Platform" and its
                    ID is cf47ff49-7da6-4603-b339-f4475176432b.
                    """)
    @ValueSwitchWidget
    @Layout(ClientApplicationSection.class)
    @Signal(condition = ClientSelection.IsCustom.class)
    ClientSelection m_clientSelection = ClientSelection.DEFAULT;

    enum ClientSelection {
        DEFAULT, CUSTOM;

        static class IsCustom extends OneOfEnumCondition<ClientSelection> {
            @Override
            public ClientSelection[] oneOf() {
                return new ClientSelection[] { CUSTOM };
            }
        }
    }

    @Widget(title = "Custom client/app ID", description = "The custom client/app ID to use.")
    @Layout(ClientApplicationSection.class)
    @Effect(signals = ClientSelection.IsCustom.class, type = EffectType.SHOW)
    String m_clientId;

    @Widget(title = "Storage account name and shared/access key", //
            description = "The storage account name and shared key (also called access key) to use.")
    @CredentialsWidget(usernameLabel = "Storage account name", passwordLabel = "Shared/access key")
    @Layout(SharedKeySection.class)
    Credentials m_sharedKey = new Credentials();

    @Widget(title = "Service SAS URL", //
            description = """
                    The Azure Service SAS URL. Note that only
                    <a href="https://learn.microsoft.com/en-us/azure/storage/common/storage-sas-overview#service-sas">
                    Service SAS</a> is supported. The SAS URL must delegate access to the Blob storage
                    service, or an object within.
                    """)
    @PasswordWidget(passwordLabel = "Service SAS URL")
    @Layout(SasUrlSection.class)
    Credentials m_sasUrl = new Credentials();

    @Widget(title = "Redirect URL (should be http://localhost:XXXXX)", //
            description = """
                    Redirect URL to use during interactive login. Technical note: Only
                    URLs such as http://localhost:37489 are allowed (localhost,
                    no https, random non-privileged port number). Any URL entered here
                    must be part of the configuration of your custom app.
                    """)
    @Layout(ClientApplicationSection.class)
    @Effect(signals = { AuthenticationType.IsInteractive.class,
            ClientSelection.IsCustom.class }, type = EffectType.SHOW, operation = And.class)
    String m_redirectUrl;

    @ButtonWidget(actionHandler = LoginActionHandler.class, //
            updateHandler = LoginUpdateHandler.class, //
            showTitleAndDescription = false)
    @Widget(title = "Login", //
            description = "Clicking on login opens a new browser window/tab which "
                    + "allows to interactively log into the service.")
    @Layout(AuthenticationSection.class)
    @Persist(optional = true, hidden = true, customPersistor = TokenCacheKeyPersistor.class)
    @Effect(signals = AuthenticationType.IsInteractive.class, type = EffectType.SHOW)
    UUID m_loginCredentialRef;

    static class LoginActionHandler extends CancelableActionHandler<UUID, MicrosoftAuthenticatorSettings> {

        @Override
        protected UUID invoke(final MicrosoftAuthenticatorSettings settings, final DefaultNodeSettingsContext context)
                throws WidgetHandlerException {
            try {
                settings.validate();
            } catch (InvalidSettingsException e) { // NOSONAR
                throw new WidgetHandlerException(e.getMessage());
            }

            var app = MSALUtil.createClientApp(settings.getClientId(), settings.getAuthorizationEndpointURL());

            // Use the InternalOpenBrowserAction, to avoid crashes on ubuntu with gtk3.
            final var params = InteractiveRequestParameters.builder(URI.create(settings.getRedirectURL()))//
                    .scopes(settings.getScopes())//
                    .systemBrowserOptions(
                            SystemBrowserOptions.builder().openBrowserAction(new CustomOpenBrowserAction()).build())
                    .build();

            try {
                var authResult = MSALUtil.doLogin(() -> app.acquireToken(params));
                return CredentialCache.store(MSALUtil.createCredential(authResult, app));
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                throw new WidgetHandlerException(e.getMessage());
            }
        }

        @Override
        protected String getButtonText(final States state) {
            return switch (state) {
                case READY -> "Login";
                case CANCEL -> "Cancel login";
                case DONE -> "Login again";
                default -> null;
            };
        }
    }

    static class LoginUpdateHandler
            extends CancelableActionHandler.UpdateHandler<UUID, MicrosoftAuthenticatorSettings> {
    }


    private void validateScopesSettings() throws InvalidSettingsException {
        var requireApplicationScopes = m_authenticationType == AuthenticationType.CLIENT_SECRET;
        m_scopesSettings.validate(requireApplicationScopes);
    }

    private void validateAuthorizationEndpointSettings() throws InvalidSettingsException {
        if (m_authorizationEndpointSelection == AuthorizationEndpointSelection.CUSTOM
                && StringUtils.isBlank(m_authorizationEndpointUrl)) {
            throw new InvalidSettingsException("Please specify the authorization endpoint URL");
        }
    }

    private void validateClientSettings() throws InvalidSettingsException {
        if (m_clientSelection == ClientSelection.CUSTOM) {

            if (StringUtils.isBlank(m_clientId)) {
                throw new InvalidSettingsException("Please specify the Client/Application ID");
            }

            if (m_authenticationType == AuthenticationType.INTERACTIVE) {
                validateRedirectURL();
            }
        }
    }

    private void validateRedirectURL() throws InvalidSettingsException {
        if (StringUtils.isBlank(m_redirectUrl)) {
            throw new InvalidSettingsException("Please specify the redirect URL");
        }

        try {
            var uri = new URI(m_redirectUrl);
            if (!Objects.equals(uri.getScheme(), "http") && !Objects.equals(uri.getScheme(), "https")) {
                throw new InvalidSettingsException("Redirect URL must start with http:// or https://.");
            }

            if (StringUtils.isBlank(uri.getHost())) {
                throw new InvalidSettingsException("Redirect URL must specify a host.");
            }
        } catch (URISyntaxException ex) {// NOSONAR
            throw new InvalidSettingsException("Please specify a valid redirect URL: " + ex.getMessage());
        }
    }

    private void validateTenantId() throws InvalidSettingsException {
        if (StringUtils.isBlank(m_tenantId)) {
            throw new InvalidSettingsException(
                    "Please specify the tenant id or domain, e.g. mycompany.onmicrosoft.com");
        }
    }

    private void validate() throws InvalidSettingsException {
        switch (m_authenticationType) {
            case INTERACTIVE, USERNAME_PASSWORD:
                validateScopesSettings();
                validateAuthorizationEndpointSettings();
                validateClientSettings();
                break;
            case CLIENT_SECRET:
                validateTenantId();
                validateScopesSettings();
                validateAuthorizationEndpointSettings();
                break;
            default:
                break;
        }
    }

    /**
     * * Validates the settings. The method is intended to be called in the
     * configure stage.
     *
     * @throws InvalidSettingsException
     */
    public void validateOnConfigure() throws InvalidSettingsException {
        validate();
    }

    /**
     * Validates the settings. The method is intended to be called in the execute
     * stage.
     *
     * @throws InvalidSettingsException
     */
    public void validateOnExecute() throws InvalidSettingsException {
        validate();

        switch (m_authenticationType) {
            case USERNAME_PASSWORD:
                CheckUtils.checkSetting(StringUtils.isNotBlank(m_usernamePassword.getUsername()),
                        "Username is required");
                CheckUtils.checkSetting(StringUtils.isNotBlank(m_usernamePassword.getPassword()),
                        "Password is required");
                break;
            case CLIENT_SECRET:
                CheckUtils.checkSetting(StringUtils.isNotBlank(m_confidentialApp.getUsername()),
                        "Client/App ID is required");
                CheckUtils.checkSetting(StringUtils.isNotBlank(m_confidentialApp.getPassword()),
                        "Client/App secret is required");
                break;
            case AZURE_STORAGE_SAS_URL:
                CheckUtils.checkSetting(StringUtils.isNotBlank(m_sasUrl.getPassword()), "Service SAS URL is required");
                validateSasUrl(m_sasUrl.getPassword());
                break;
            case AZURE_STORAGE_SHARED_KEY:
                CheckUtils.checkSetting(StringUtils.isNotBlank(m_sharedKey.getUsername()),
                        "Storage account name is required");
                CheckUtils.checkSetting(StringUtils.isNotBlank(m_sharedKey.getPassword()),
                        "Access/shared key is required");
                break;
            default:
                break;
        }
    }


    private static void validateSasUrl(final String sasUrl) throws InvalidSettingsException {
        try {
            final var url = new URI(sasUrl);
            CheckUtils.checkSetting(Objects.equals(url.getScheme(), "https"), "Invalid protocol: %s (expected https)",
                    url.getScheme());
            CheckUtils.checkSetting(StringUtils.isNotBlank(url.getQuery()), "Invalid SAS URL (query is missing)");
        } catch (URISyntaxException ex) {
            throw new InvalidSettingsException(ex.getMessage(), ex);
        }
    }

    /**
     * @return The client ID.
     */
    @JsonIgnore
    public String getClientId() {
        if (m_clientSelection == ClientSelection.CUSTOM) {
            return m_clientId;
        } else {
            return MSALUtil.DEFAULT_APP_ID;
        }
    }

    /**
     * @return The authorization endpoint URL.
     */
    @JsonIgnore
    public String getAuthorizationEndpointURL() {
        if (m_authenticationType == AuthenticationType.CLIENT_SECRET) {
            if (m_tenantId.startsWith("http")) {
                return m_tenantId;
            } else {
                return "https://login.microsoftonline.com/" + m_tenantId;
            }
        }

        if (m_authorizationEndpointSelection == AuthorizationEndpointSelection.CUSTOM) {
            return m_authorizationEndpointUrl;
        }

        if (m_authenticationType == AuthenticationType.INTERACTIVE) {
            return MSALUtil.COMMON_ENDPOINT;
        } else {
            return MSALUtil.ORGANIZATIONS_ENDPOINT;
        }
    }

    /**
     * @return The redirect URL.
     */
    @JsonIgnore
    public String getRedirectURL() {
        if (m_clientSelection == ClientSelection.CUSTOM) {
            return m_redirectUrl;
        } else {
            return DEFAULT_REDIRECT_URL;
        }
    }

    /**
     * @return The scopes strings.
     */
    @JsonIgnore
    public Set<String> getScopes() {
        return m_scopesSettings.getScopesStringSet(m_authenticationType == AuthenticationType.CLIENT_SECRET);
    }

}
