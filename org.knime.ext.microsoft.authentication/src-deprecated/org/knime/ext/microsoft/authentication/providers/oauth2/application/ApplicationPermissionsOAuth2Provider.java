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
 *   2022-10-13 (zkriyarakhimberdiyev): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.application;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelPassword;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.core.node.workflow.ICredentials;
import org.knime.credentials.base.Credential;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProviderEditor;
import org.knime.ext.microsoft.authentication.providers.oauth2.LegacyScope;
import org.knime.ext.microsoft.authentication.providers.oauth2.OAuth2Provider;
import org.knime.ext.microsoft.authentication.util.JWTCredentialFactory;
import org.knime.ext.microsoft.authentication.util.MSALUtil;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.IClientCredential;

/**
 * Auth provider implementing OAuth2 authentication using application
 * permissions.
 *
 * @author Zkriya Rakhimberdiyev
 */
public class ApplicationPermissionsOAuth2Provider extends OAuth2Provider {

    /**
     * Added with KNIME AP 4.7 to support OAuth2 with application permissions.
     */
    private static final String KEY_APP_TYPE_SCOPES = "app_type_scopes";

    /**
     * Added with KNIME AP 4.7 to support OAuth2 with application permissions.
     */
    private static final String KEY_OTHER_SCOPE = "otherScope";

    private static final String KEY_TENANT_ID = "tenant_id";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_USE_CREDENTIALS = "useCredentials";
    private static final String KEY_CREDENTIALS_NAME = "credentialsName";
    private static final String ENCRYPTION_KEY = "Z4mJcnXMrtXKW8wp";

    private final SettingsModelString m_otherScope;

    private final SettingsModelString m_tenantId;
    private final SettingsModelString m_clientId;
    private final SettingsModelPassword m_secret;
    private final SettingsModelBoolean m_useCredentials;
    private final SettingsModelString m_credentialsName;

    /**
     * Constructor for compatibility with BiFunction<PortsConfiguration,String>. The
     * given PortsConfiguration is ignored.
     *
     * @param portsConfig
     *            Ignored argument.
     * @param nodeInstanceId
     */
    public ApplicationPermissionsOAuth2Provider(final PortsConfiguration portsConfig, final String nodeInstanceId) {// NOSONAR
        this();
    }

    /**
     * Constructor.
     *
     */
    public ApplicationPermissionsOAuth2Provider() {
        super(KEY_APP_TYPE_SCOPES, LegacyScope.GRAPH_APP);
        m_otherScope = new SettingsModelString(KEY_OTHER_SCOPE, "");
        m_otherScope.setEnabled(false);

        m_tenantId = new SettingsModelString(KEY_TENANT_ID, "");
        m_clientId = new SettingsModelString(KEY_CLIENT_ID, "");
        m_secret = new SettingsModelPassword(KEY_SECRET, ENCRYPTION_KEY, "");
        m_useCredentials = new SettingsModelBoolean(KEY_USE_CREDENTIALS, false);
        m_credentialsName = new SettingsModelString(KEY_CREDENTIALS_NAME, "");

        m_credentialsName.setEnabled(false);
        m_useCredentials.addChangeListener(e -> {
            var useCreds = m_useCredentials.getBooleanValue();
            m_clientId.setEnabled(!useCreds);
            m_secret.setEnabled(!useCreds);
            m_credentialsName.setEnabled(useCreds);
        });
    }

    /**
     * @return the model for the manually entered scope.
     */
    public SettingsModelString getOtherScopeModel() {
        return m_otherScope;
    }

    /**
     * @return the client Id model
     */
    public SettingsModelString getTenantIdModel() {
        return m_tenantId;
    }

    /**
     * @return the client Id model
     */
    public SettingsModelString getClientIdModel() {
        return m_clientId;
    }

    /**
     * @return the secret model
     */
    public SettingsModelPassword getSecretModel() {
        return m_secret;
    }

    /**
     * @return the useCredentials model
     */
    public SettingsModelBoolean getUseCredentialsModel() {
        return m_useCredentials;
    }

    /**
     * @return the credentialsName model
     */
    public SettingsModelString getCredentialsNameModel() {
        return m_credentialsName;
    }

    @Override
    public Credential getCredential(final CredentialsProvider credentialsProvider) throws IOException {
        String clientId;
        String secret;

        if (m_useCredentials.getBooleanValue()) {
            ICredentials creds = credentialsProvider.get(m_credentialsName.getStringValue());
            clientId = creds.getLogin();
            secret = creds.getPassword();

            if (StringUtils.isBlank(secret)) {
                throw new IOException("The selected credentials flow variable does not provide a secret");
            }
        } else {
            clientId = m_clientId.getStringValue();
            secret = m_secret.getStringValue();
        }

        final var endpoint = "https://login.microsoftonline.com/" + m_tenantId.getStringValue();

        final IClientCredential credential = ClientCredentialFactory.createFromSecret(secret);
        final var app = MSALUtil.createConfidentialApp(clientId, endpoint, credential, null);

        try {
            final var scopes = getScopesStringSet();
            final var authResult = app.acquireToken(ClientCredentialParameters.builder(scopes).build())
                    .get();
            return JWTCredentialFactory.create(authResult, app, scopes);
        } catch (InterruptedException ex) { // NOSONAR
            throw new IOException("Authentication cancelled/interrupted", ex);
        } catch (ExecutionException ex) {// NOSONAR
            throw new IOException(ex.getCause());
        }
    }

    @Override
    public MicrosoftAuthProviderEditor createEditor(final MicrosoftAuthenticationNodeDialog parent,
            final Supplier<CredentialsProvider> credentialsSupplier) {
        return new ApplicationPermissionsOAuth2ProviderEditor(this, credentialsSupplier);
    }

    @Override
    public Set<String> getScopesStringSet() {
        final Set<String> scopeStrings = new HashSet<>();
        final Set<LegacyScope> scopes = getScopesEnumSet();
        for (LegacyScope scope : scopes) {
            if (scope == LegacyScope.OTHER) {
                scopeStrings.add(m_otherScope.getStringValue().trim());
            } else {
                scopeStrings.add(scope.getScope());
            }
        }
        return scopeStrings;
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        m_otherScope.saveSettingsTo(settings);
        m_tenantId.saveSettingsTo(settings);
        m_clientId.saveSettingsTo(settings);
        m_secret.saveSettingsTo(settings);
        m_useCredentials.saveSettingsTo(settings);
        m_credentialsName.saveSettingsTo(settings);
    }

    @Override
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_tenantId.validateSettings(settings);
        m_clientId.validateSettings(settings);
        m_secret.validateSettings(settings);
        m_useCredentials.validateSettings(settings);
        m_credentialsName.validateSettings(settings);

        var temp = new ApplicationPermissionsOAuth2Provider();
        temp.loadSettingsFrom(settings);
        temp.validate();
    }

    @Override
    public void validate() throws InvalidSettingsException {
        super.validate();

        if (getScopesEnumSet().contains(LegacyScope.OTHER) && m_otherScope.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Other scope cannot be empty");
        }

        if (m_tenantId.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Tenant ID/Domain cannot be empty");
        }

        if (!m_useCredentials.getBooleanValue()) {
            if (m_clientId.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Client/Application ID cannot be empty");
            }
            if (m_secret.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Secret cannot be empty");
            }
        } else {
            if (m_credentialsName.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Credentials are not selected");
            }
        }
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.loadSettingsFrom(settings);
        m_otherScope.loadSettingsFrom(settings);
        m_tenantId.loadSettingsFrom(settings);
        m_clientId.loadSettingsFrom(settings);
        m_secret.loadSettingsFrom(settings);
        m_credentialsName.loadSettingsFrom(settings);
        m_useCredentials.loadSettingsFrom(settings);
    }

}
