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

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.Pair;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.After;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Before;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.field.Persist;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.rule.OneOfEnumCondition;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Signal;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.button.ButtonWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.button.CancelableActionHandler;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.WidgetHandlerException;
import org.knime.credentials.base.GenericTokenHolder;
import org.knime.credentials.base.oauth.api.nodesettings.TokenCacheKeyPersistor;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.CustomOpenBrowserAction;
import org.knime.ext.microsoft.authentication.util.MSALUtil;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.MsalException;
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

    @Section(title = "Authentication type")
    @Before(ScopesSection.class)
    interface AuthenticationTypeSection {
    }

    @Section(title = "Scopes of access")
    @After(AuthenticationTypeSection.class)
    interface ScopesSection {
    }

    @Section(title = "Authorization endpoint", advanced = true)
    @After(ScopesSection.class)
    interface AuthorizationEndpointSection {
    }

    @Section(title = "Client/App", advanced = true)
    @After(AuthorizationEndpointSection.class)
    interface ClientApplicationSection {
    }

    @Section(title = "Authentication")
    @After(ClientApplicationSection.class)
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
                    """, // NOSONAR
            hideTitle = true)
    @Layout(AuthenticationTypeSection.class)
    @Signal(condition = AuthenticationType.IsInteractive.class)
    AuthenticationType m_authenticationType = AuthenticationType.INTERACTIVE;

    enum AuthenticationType {
        @Label("Interactive (OAuth 2)")
        INTERACTIVE;

        static class IsInteractive extends OneOfEnumCondition<AuthenticationType> {
            @Override
            public AuthenticationType[] oneOf() {
                return new AuthenticationType[] { INTERACTIVE };
            }
        }
    }

    @Layout(ScopesSection.class)
    ScopesSettings m_scopesSettings = new ScopesSettings();

    @Widget(title = "Authorization endpoint", hideTitle = true, //
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

    @Widget(title = "Client/App", hideTitle = true, //
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

    @Widget(title = "Redirect URL (should be http://localhost:XXXXX)", //
            description = """
                    Redirect URL to use during interactive login. Technical note: Only
                    URLs such as http://localhost:37489 are allowed (localhost,
                    no https, random non-privileged port number). Any URL entered here
                    must be part of the configuration of your custom app.
                    """)
    @Layout(ClientApplicationSection.class)
    @Effect(signals = ClientSelection.IsCustom.class, type = EffectType.SHOW)
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
    UUID m_tokenCacheKey;

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
            final InteractiveRequestParameters params = InteractiveRequestParameters
                    .builder(URI.create(settings.getRedirectURL()))
                    .scopes(settings.m_scopesSettings.getScopesStringSet())
                    .systemBrowserOptions(
                            SystemBrowserOptions.builder().openBrowserAction(new CustomOpenBrowserAction()).build())
                    .build();

            CompletableFuture<IAuthenticationResult> authFuture = null;
            try {
                authFuture = app.acquireToken(params);
                var result = authFuture.get();

                var pair = new Pair<>(result, app.tokenCache().serialize());
                var holder = GenericTokenHolder.store(pair);
                return holder.getCacheKey();
            } catch (MsalException e) {
                LOG.error(e.getMessage(), e);
                throw new WidgetHandlerException(MSALUtil.formatException(e));
            } catch (InterruptedException | CancellationException ex) { // NOSONAR intentionally ignoring
                // need to cancel the future if current thread is interrupted, otherwise local
                // webserver is not shut down
                authFuture.cancel(true); // NOSONAR it's not null
                throw new WidgetHandlerException("Login cancelled/interrupted");
            } catch (ExecutionException ex) {
                LOG.error(ex.getCause().getMessage(), ex);
                throw new WidgetHandlerException(MSALUtil.formatException(ex.getCause()));
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

    /**
     * Validates the settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        m_scopesSettings.validate();

        if (m_authorizationEndpointSelection == AuthorizationEndpointSelection.CUSTOM
                && StringUtils.isBlank(m_authorizationEndpointUrl)) {
            throw new InvalidSettingsException("Please specify the authorization endpoint URL");
        }

        if (m_clientSelection == ClientSelection.CUSTOM) {
            if (StringUtils.isBlank(m_clientId)) {
                throw new InvalidSettingsException("Please specify the Client/Application ID");
            }
            if (StringUtils.isBlank(m_redirectUrl)) {
                throw new InvalidSettingsException("Please specify the redirect URL");
            }
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
        if(m_clientSelection == ClientSelection.CUSTOM) {
            return m_redirectUrl;
        }else {
            return DEFAULT_REDIRECT_URL;
        }
    }
}
