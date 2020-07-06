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
package org.knime.ext.microsoft.authentication.providers;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelPassword;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.workflow.ICredentials;
import org.knime.ext.microsoft.authentication.nodes.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.nodes.auth.MicrosoftAuthenticationNodeModel;
import org.knime.ext.microsoft.authentication.port.MicrosoftConnection;
import org.knime.ext.microsoft.authentication.providers.ui.MicrosoftAuthProviderEditor;
import org.knime.ext.microsoft.authentication.providers.ui.UsernamePasswordProviderEditor;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;

/**
 * {@link MSALAuthProvider} implementation that performs authentication
 * using username and password provided by user.
 *
 * @author Alexander Bondaletov
 */
public class UsernamePasswordAuthProvider extends MSALAuthProvider {

    private static final String AUTHORITY = "https://login.microsoftonline.com/organizations";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USE_CREDENTIALS = "useCredentials";
    private static final String KEY_CREDENTIALS_NAME = "credentialsName";
    private static final String ENCRYPTION_KEY = "Z4mJcnXMrtXKW8wp";

    private final SettingsModelString m_username;
    private final SettingsModelPassword m_password;
    private final SettingsModelBoolean m_useCredentials;
    private final SettingsModelString m_credentialsName;

    /**
     * Creates new instance.
     */
    public UsernamePasswordAuthProvider() {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public MicrosoftConnection authenticate(final MicrosoftAuthenticationNodeModel model)
            throws InterruptedException, ExecutionException, MalformedURLException {
        String username;
        String password;

        if (m_useCredentials.getBooleanValue()) {
            ICredentials creds = model.getCredentials(m_credentialsName.getStringValue());
            username = creds.getLogin();
            password = creds.getPassword();
        } else {
            username = m_username.getStringValue();
            password = m_password.getStringValue();
        }

        PublicClientApplication app = createClientApp();

        final IAuthenticationResult result = app.acquireToken(
                UserNamePasswordParameters.builder(getScopes(), username, password.toCharArray()).build())
                .get();

        return new MicrosoftConnection(AuthProviderType.USERNAME_PASSWORD, app.tokenCache().serialize(),
                result.account().username(), result.expiresOnDate(), getScopes(), getAuthority());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MicrosoftAuthProviderEditor createEditor(final MicrosoftAuthenticationNodeDialog parent) {
        return new UsernamePasswordProviderEditor(this, parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        m_username.saveSettingsTo(settings);
        m_password.saveSettingsTo(settings);
        m_useCredentials.saveSettingsTo(settings);
        m_credentialsName.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_username.validateSettings(settings);
        m_password.validateSettings(settings);
        m_useCredentials.validateSettings(settings);
        m_credentialsName.validateSettings(settings);

        UsernamePasswordAuthProvider temp = new UsernamePasswordAuthProvider();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.loadSettingsFrom(settings);
        m_username.loadSettingsFrom(settings);
        m_password.loadSettingsFrom(settings);
        m_credentialsName.loadSettingsFrom(settings);
        m_useCredentials.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getAuthority() {
        return AUTHORITY;
    }

}
