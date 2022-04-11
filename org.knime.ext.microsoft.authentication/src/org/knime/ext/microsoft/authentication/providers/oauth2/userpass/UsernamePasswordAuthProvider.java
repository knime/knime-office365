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
 *   2020-06-04 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.userpass;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelPassword;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.core.node.workflow.ICredentials;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.microsoft.authentication.providers.MemoryCredentialCache;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProviderEditor;
import org.knime.ext.microsoft.authentication.providers.oauth2.MSALUtil;
import org.knime.ext.microsoft.authentication.providers.oauth2.OAuth2Provider;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.MemoryCacheAccessTokenSupplier;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;

/**
 * {@link OAuth2Provider} implementation that performs authentication
 * using username and password provided by user.
 *
 * @author Alexander Bondaletov
 */
public class UsernamePasswordAuthProvider extends OAuth2Provider {

    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USE_CREDENTIALS = "useCredentials";
    private static final String KEY_CREDENTIALS_NAME = "credentialsName";
    private static final String ENCRYPTION_KEY = "Z4mJcnXMrtXKW8wp";

    private final String m_cacheKey;

    private final SettingsModelString m_username;
    private final SettingsModelPassword m_password;
    private final SettingsModelBoolean m_useCredentials;
    private final SettingsModelString m_credentialsName;

    /**
     * Constructor for compatibility with BiFunction<PortsConfiguration,String>. The given
     * PortsConfiguration is ignored.
     *
     * @param portsConfig Ignored argument.
     * @param nodeInstanceId
     */
    public UsernamePasswordAuthProvider(final PortsConfiguration portsConfig, final String nodeInstanceId) {
        this(nodeInstanceId);
    }

    /**
     * Creates new instance.
     *
     * @param nodeInstanceId
     */
    public UsernamePasswordAuthProvider(final String nodeInstanceId) {
        super();
        m_username = new SettingsModelString(KEY_USERNAME, "");
        m_password = new SettingsModelPassword(KEY_PASSWORD, ENCRYPTION_KEY, "");
        m_useCredentials = new SettingsModelBoolean(KEY_USE_CREDENTIALS, false);
        m_credentialsName = new SettingsModelString(KEY_CREDENTIALS_NAME, "");

        m_credentialsName.setEnabled(false);
        m_useCredentials.addChangeListener(e -> {
            boolean useCreds = m_useCredentials.getBooleanValue();
            m_username.setEnabled(!useCreds);
            m_password.setEnabled(!useCreds);
            m_credentialsName.setEnabled(useCreds);
        });

        m_cacheKey = "userpass-" + nodeInstanceId;
    }

    /**
     * @return the username model
     */
    public SettingsModelString getUsernameModel() {
        return m_username;
    }

    /**
     * @return the password model
     */
    public SettingsModelPassword getPasswordModel() {
        return m_password;
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
    public OAuth2Credential getCredential(
            final CredentialsProvider credentialsProvider)
            throws IOException {
        String username;
        String password;

        if (m_useCredentials.getBooleanValue()) {
            ICredentials creds = credentialsProvider.get(m_credentialsName.getStringValue());
            username = creds.getLogin();
            password = creds.getPassword();

            if (password == null || password.isEmpty()) {
                throw new IOException("The selected credentials flow variable does not provide a password");
            }
        } else {
            username = m_username.getStringValue();
            password = m_password.getStringValue();
        }

        PublicClientApplication app = MSALUtil.createClientApp(getAppId(), getEndpoint());

        try {
            final IAuthenticationResult result = app.acquireToken(
                    UserNamePasswordParameters.builder(getScopesStringSet(), username, password.toCharArray()).build())
                    .get();

            MemoryCredentialCache.put(m_cacheKey, app.tokenCache().serialize());

            final var tokenSupplier = new MemoryCacheAccessTokenSupplier(getEndpoint(),
                    m_cacheKey, getAppId());

            return new OAuth2Credential(tokenSupplier, //
                    result.account().username(), //
                    getScopesStringSet(), //
                    getEndpoint(), getAppId());
        } catch (InterruptedException ex) { // NOSONAR we are rethrowing by attaching as cause to an IOE
            throw new IOException(ex);
        } catch (ExecutionException ex) { // NOSONAR this exception is being handled
            final Throwable cause = ex.getCause();
            throw new IOException(ExceptionUtil.getDeepestErrorMessage(cause, false), cause);
        }
    }

    @Override
    public MicrosoftAuthProviderEditor createEditor(final MicrosoftAuthenticationNodeDialog parent) {
        return new UsernamePasswordProviderEditor(this, parent);
    }

    @Override
    protected String getDefaultEndpoint() {
        return MSALUtil.ORGANIZATIONS_ENDPOINT;
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        m_username.saveSettingsTo(settings);
        m_password.saveSettingsTo(settings);
        m_useCredentials.saveSettingsTo(settings);
        m_credentialsName.saveSettingsTo(settings);
    }

    @Override
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_username.validateSettings(settings);
        m_password.validateSettings(settings);
        m_useCredentials.validateSettings(settings);
        m_credentialsName.validateSettings(settings);

        UsernamePasswordAuthProvider temp = new UsernamePasswordAuthProvider("");
        temp.loadSettingsFrom(settings);
        temp.validate();
    }

    @Override
    public void validate() throws InvalidSettingsException {
        super.validate();
        if (!m_useCredentials.getBooleanValue()) {
            if (m_username.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Username cannot be empty");
            }
            if (m_password.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Password cannot be empty");
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
        m_username.loadSettingsFrom(settings);
        m_password.loadSettingsFrom(settings);
        m_credentialsName.loadSettingsFrom(settings);
        m_useCredentials.loadSettingsFrom(settings);
    }

    @Override
    public void clearMemoryTokenCache() {
        MemoryCredentialCache.remove(m_cacheKey);
    }
}
