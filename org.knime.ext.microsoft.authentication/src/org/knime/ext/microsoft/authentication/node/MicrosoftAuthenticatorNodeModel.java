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
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.util.Date;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.Pair;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialCache;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.GenericTokenHolder;
import org.knime.credentials.base.node.AuthenticatorNodeModel;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.ext.microsoft.authentication.node.MicrosoftAuthenticatorSettings.AuthenticationType;
import org.knime.ext.microsoft.authentication.util.MSALUtil;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.SilentParameters;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;

/**
 * The Microsoft Authenticator node. Performs authentication and produces
 * {@link Credential} object.
 *
 * @author Alexander Bondaletov, Redfield SE
 */
@SuppressWarnings("restriction")
public class MicrosoftAuthenticatorNodeModel extends AuthenticatorNodeModel<MicrosoftAuthenticatorSettings> {

    private static final NodeLogger LOG = NodeLogger.getLogger(MicrosoftAuthenticatorNodeModel.class);

    private static final String LOGIN_FIRST_ERROR = "Please use the configuration dialog to log in first.";

    private GenericTokenHolder<Pair<IAuthenticationResult, String>> m_tokenHolder;

    /**
     * @param configuration
     *            The node configuration.
     */
    protected MicrosoftAuthenticatorNodeModel(final WebUINodeConfiguration configuration) {
        super(configuration, MicrosoftAuthenticatorSettings.class);
    }

    @Override
    protected void validateOnConfigure(final PortObjectSpec[] inSpecs, final MicrosoftAuthenticatorSettings settings)
            throws InvalidSettingsException {
        settings.validateOnConfigure(getCredentialsProvider());

        if (settings.m_authenticationType == AuthenticationType.INTERACTIVE) {
            // in this case we must have already fetched the token in the node dialog
            if (settings.m_tokenCacheKey == null) {
                throw new InvalidSettingsException(LOGIN_FIRST_ERROR);
            } else {
                m_tokenHolder = CredentialCache
                        .<GenericTokenHolder<Pair<IAuthenticationResult, String>>>get(settings.m_tokenCacheKey)//
                        .orElseThrow(() -> new InvalidSettingsException(LOGIN_FIRST_ERROR));
            }
        } else {
            // we have an access token from a previous interactive login -> remove it
            if (m_tokenHolder != null) {
                CredentialCache.delete(m_tokenHolder.getCacheKey());
                m_tokenHolder = null;
            }
        }
    }

    @Override
    protected void validateOnExecute(final PortObject[] inObjects, final MicrosoftAuthenticatorSettings settings)
            throws InvalidSettingsException {
        settings.validateOnExecute(getCredentialsProvider());
    }

    @Override
    protected final CredentialPortObjectSpec createSpecInConfigure(final PortObjectSpec[] inSpecs,
            final MicrosoftAuthenticatorSettings modelSettings) {
        return new CredentialPortObjectSpec(JWTCredential.TYPE, null);
    }

    @Override
    protected Credential createCredential(final PortObject[] inObjects, final ExecutionContext exec,
            final MicrosoftAuthenticatorSettings settings) throws Exception {
        return switch (settings.m_authenticationType) {
            case INTERACTIVE -> fromAuthResult(settings, m_tokenHolder.getToken().getFirst(),
                    m_tokenHolder.getToken().getSecond());
            case USERNAME_PASSWORD -> fetchCredentialFromUsernamePassword(settings);
            default -> throw new InvalidSettingsException(
                    "Unknown authentication mode: " + settings.m_authenticationType);
        };
    }

    private Credential fetchCredentialFromUsernamePassword(final MicrosoftAuthenticatorSettings settings)
            throws IOException {

        var app = MSALUtil.createClientApp(settings.getClientId(), settings.getAuthorizationEndpointURL());
        var params = UserNamePasswordParameters.builder(settings.m_scopesSettings.getScopesStringSet(),
                settings.m_usernamePassword.login(getCredentialsProvider()),
                settings.m_usernamePassword.secret(getCredentialsProvider()).toCharArray()).build();

        var authResult = MSALUtil.doLogin(() -> app.acquireToken(params));
        return fromAuthResult(settings, authResult, app.tokenCache().serialize());
    }

    private JWTCredential fromAuthResult(final MicrosoftAuthenticatorSettings settings,
            final IAuthenticationResult authResult, final String tokenCache) {

        var accessToken = authResult.accessToken();
        var idToken = authResult.idToken();
        var expiresAfter = Optional.ofNullable(authResult.expiresOnDate())//
                .map(Date::toInstant)//
                .orElse(null);
        var tokenType = "Bearer";

        try {
            return new JWTCredential(accessToken, tokenType, expiresAfter, idToken,
                    createTokenRefresher(settings, authResult.account(), tokenCache));
        } catch (ParseException ex) {
            // should not happen
            throw new UncheckedIOException(new IOException("Failed to parse token: " + ex.getMessage(), ex));
        }
    }

    private Supplier<JWTCredential> createTokenRefresher(final MicrosoftAuthenticatorSettings settings,
            final IAccount account, final String tokenCache) {

        return () -> {// NOSONAR
            try {
                var app = MSALUtil.createClientAppWithToken(//
                        settings.getClientId(),
                        settings.getAuthorizationEndpointURL(), //
                        tokenCache);
                var params = SilentParameters.builder(//
                        settings.m_scopesSettings.getScopesStringSet(), //
                        account).build();

                var authResult = MSALUtil.doLogin(() -> app.acquireTokenSilently(params));
                return fromAuthResult(settings, authResult, app.tokenCache().serialize());
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new UncheckedIOException(ex);
            }
        };
    }

    @Override
    protected void onDisposeInternal() {
        // dispose of the token that was retrieved interactively in the node
        // dialog
        if (m_tokenHolder != null) {
            CredentialCache.delete(m_tokenHolder.getCacheKey());
            m_tokenHolder = null;
        }
    }
}
