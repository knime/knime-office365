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
import java.util.Optional;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.CredentialRef;
import org.knime.credentials.base.node.AuthenticatorNodeModel;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.ext.microsoft.authentication.credential.AzureStorageSasUrlCredential;
import org.knime.ext.microsoft.authentication.credential.AzureStorageSharedKeyCredential;
import org.knime.ext.microsoft.authentication.node.MicrosoftAuthenticatorSettings.AuthenticationType;
import org.knime.ext.microsoft.authentication.node.MicrosoftAuthenticatorSettings.UserAgentSelection;
import org.knime.ext.microsoft.authentication.util.AccessTokenWithScopesCredentialFactory;
import org.knime.ext.microsoft.authentication.util.JWTCredentialFactory;
import org.knime.ext.microsoft.authentication.util.MSALUtil;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;

/**
 * The Microsoft Authenticator node. Performs authentication and produces
 * {@link Credential} object.
 *
 * @author Alexander Bondaletov, Redfield SE
 */
@SuppressWarnings("restriction")
public class MicrosoftAuthenticatorNodeModel extends AuthenticatorNodeModel<MicrosoftAuthenticatorSettings> {
    private static final String LOGIN_FIRST_ERROR = "Please use the configuration dialog to log in first.";

    /**
     * This references a {@link JWTCredential} that was acquired interactively in
     * the node dialog. It is disposed when the workflow is closed, or when the
     * authentication scheme is switched to non-interactive. However, it is NOT
     * disposed during reset().
     */
    private CredentialRef m_interactiveCredentialRef;

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

        settings.validateOnConfigure();

        if (settings.m_authenticationType == AuthenticationType.INTERACTIVE) {
            m_interactiveCredentialRef = Optional.ofNullable(settings.m_loginCredentialRef)//
                    .map(CredentialRef::new)//
                    .orElseThrow(() -> new InvalidSettingsException(LOGIN_FIRST_ERROR));

            if (!m_interactiveCredentialRef.isPresent()) {
                throw new InvalidSettingsException(LOGIN_FIRST_ERROR);
            }
        } else {
            disposeInteractiveCredential();
        }
    }

    @Override
    protected void validateOnExecute(final PortObject[] inObjects, final MicrosoftAuthenticatorSettings settings)
            throws InvalidSettingsException {
        settings.validateOnExecute();
    }

    @Override
    protected final CredentialPortObjectSpec createSpecInConfigure(final PortObjectSpec[] inSpecs,
            final MicrosoftAuthenticatorSettings modelSettings) {

        return switch (modelSettings.m_authenticationType) {
            case AZURE_STORAGE_SHARED_KEY -> new CredentialPortObjectSpec(AzureStorageSharedKeyCredential.TYPE, null);
            default -> new CredentialPortObjectSpec(JWTCredential.TYPE, null);
        };
    }

    @Override
    protected Credential createCredential(final PortObject[] inObjects, final ExecutionContext exec,
            final MicrosoftAuthenticatorSettings settings) throws Exception {

        return switch (settings.m_authenticationType) {
            case INTERACTIVE -> m_interactiveCredentialRef.getCredential(JWTCredential.class)//
                    .orElseThrow(() -> new InvalidSettingsException(LOGIN_FIRST_ERROR));
            case CLIENT_SECRET -> fetchCredentialFromClientSecret(settings);
            case USERNAME_PASSWORD -> fetchCredentialFromUsernamePassword(settings);
            case AZURE_STORAGE_SHARED_KEY -> createAzureSharedKeyCredential(settings);
            case AZURE_STORAGE_SAS_URL -> fetchCredentialFromSasUrl(settings);
            default -> throw new InvalidSettingsException(
                    "Unknown authentication mode: " + settings.m_authenticationType);
        };
    }

    private static Credential fetchCredentialFromUsernamePassword(final MicrosoftAuthenticatorSettings settings)
            throws IOException {

        final var usernamePassword = settings.m_usernamePassword;
        final var httpUserAgent = settings.m_userAgentSelection == UserAgentSelection.CUSTOM
                ? settings.m_customUserAgent
                : null;

        final var clientId = settings.getClientId();
        final var authEndpointURL = settings.getAuthorizationEndpointURL();
        final var app = MSALUtil.createClientApp(clientId, authEndpointURL, httpUserAgent);
        final var scopeList = settings.getScopes();
        final var params = UserNamePasswordParameters
                .builder(scopeList.scopes(), //
                        usernamePassword.getUsername(), //
                        usernamePassword.getPassword().toCharArray())
                .build();

        final var authResult = MSALUtil.doLogin(() -> app.acquireToken(params));

        if (scopeList.isMultiResource()) {
            return AccessTokenWithScopesCredentialFactory.create(app);
        } else {
            return JWTCredentialFactory.create(authResult, app);
        }
    }

    private static Credential fetchCredentialFromClientSecret(final MicrosoftAuthenticatorSettings settings)
            throws IOException {

        final var clientId = settings.m_confidentialApp.getUsername();
        final var clientSecret = ClientCredentialFactory.createFromSecret(settings.m_confidentialApp.getPassword());
        final var authEndpointURL = settings.getAuthorizationEndpointURL();
        final var httpUserAgent = settings.m_userAgentSelection == UserAgentSelection.CUSTOM
                ? settings.m_customUserAgent
                : null;
        final var scopeList = settings.getScopes();

        var app = MSALUtil.createConfidentialApp(clientId, authEndpointURL, clientSecret, httpUserAgent);
        var params = ClientCredentialParameters.builder(scopeList.scopes()).build();

        var authResult = MSALUtil.doLogin(() -> app.acquireToken(params));

        if (scopeList.isMultiResource()) {
            return AccessTokenWithScopesCredentialFactory.create(app);
        } else {
            return JWTCredentialFactory.create(authResult, app);
        }
    }

    private static Credential createAzureSharedKeyCredential(final MicrosoftAuthenticatorSettings settings) {

        final var sharedKey = settings.m_sharedKey;
        return new AzureStorageSharedKeyCredential(sharedKey.getUsername(), sharedKey.getPassword());
    }

    private static Credential fetchCredentialFromSasUrl(final MicrosoftAuthenticatorSettings settings)
            throws InvalidSettingsException {

        final var sasUrl = settings.m_sasUrl;
        try {
            var sasUri = URI.create(sasUrl.getPassword());
            return new AzureStorageSasUrlCredential(sasUri);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingsException("Invalid SAS URL. " + e.getMessage(), e); // NOSONAR
        }
    }

    private void disposeInteractiveCredential() {
        if (m_interactiveCredentialRef != null) {
            m_interactiveCredentialRef.dispose();
            m_interactiveCredentialRef = null;
        }
    }

    @Override
    protected void onDisposeInternal() {
        disposeInteractiveCredential();
    }
}
