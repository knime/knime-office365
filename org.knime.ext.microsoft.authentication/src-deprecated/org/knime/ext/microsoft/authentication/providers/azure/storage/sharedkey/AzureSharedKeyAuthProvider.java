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
 *   2020-08-09 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers.azure.storage.sharedkey;

import java.io.IOException;
import java.util.function.Supplier;

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
import org.knime.ext.microsoft.authentication.credential.AzureStorageSharedKeyCredential;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.providers.AuthProviderType;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProvider;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProviderEditor;

/**
 * {@link MicrosoftAuthProvider} implementations that performs authentication
 * using Azure Storage account and secret key provided by user.
 *
 * @author Alexander Bondaletov
 */
public class AzureSharedKeyAuthProvider implements MicrosoftAuthProvider {

    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_SECRET_KEY = "secretKey";
    private static final String KEY_USE_CREDENTIALS = "useCredentials";
    private static final String KEY_CREDENTIALS_NAME = "credentialsName";
    private static final String ENCRYPTION_KEY = "Jy3yBcWjcyqku2E";

    private final SettingsModelString m_account;
    private final SettingsModelPassword m_secretKey;
    private final SettingsModelBoolean m_useCredentials;
    private final SettingsModelString m_credentialsName;

    /**
     * Constructor for compatibility with BiFunction<PortsConfiguration,String> in
     * the {@link AuthProviderType}. The given PortsConfiguration is ignored.
     *
     * @param portsConfig
     *            Ignored argument.
     * @param nodeInstanceId
     */
    public AzureSharedKeyAuthProvider(final PortsConfiguration portsConfig, final String nodeInstanceId) {// NOSONAR
        this();
    }

    /**
     * Creates new instance
     */
    public AzureSharedKeyAuthProvider() {
        m_account = new SettingsModelString(KEY_ACCOUNT, "");
        m_secretKey = new SettingsModelPassword(KEY_SECRET_KEY, ENCRYPTION_KEY, "");
        m_useCredentials = new SettingsModelBoolean(KEY_USE_CREDENTIALS, false);
        m_credentialsName = new SettingsModelString(KEY_CREDENTIALS_NAME, "");

        m_credentialsName.setEnabled(false);
        m_useCredentials.addChangeListener(e -> {
            var useCreds = m_useCredentials.getBooleanValue();
            m_account.setEnabled(!useCreds);
            m_secretKey.setEnabled(!useCreds);
            m_credentialsName.setEnabled(useCreds);
        });
    }

    /**
     * @return the account model.
     */
    public SettingsModelString getAccountModel() {
        return m_account;
    }

    /**
     * @return the secretKey model.
     */
    public SettingsModelPassword getSecretKeyModel() {
        return m_secretKey;
    }

    /**
     * @return the useCredentials model.
     */
    public SettingsModelBoolean getUseCredentialsModel() {
        return m_useCredentials;
    }

    /**
     * @return the credentialsName model.
     */
    public SettingsModelString getCredentialsNameModel() {
        return m_credentialsName;
    }

    @Override
    public Credential getCredential(final CredentialsProvider credentialsProvider) throws IOException {
        String account;
        String secretKey;
        if (m_useCredentials.getBooleanValue()) {
            ICredentials creds = credentialsProvider.get(m_credentialsName.getStringValue());
            account = creds.getLogin();
            secretKey = creds.getPassword();

            if (secretKey == null || secretKey.isEmpty()) {
                throw new IOException("The selected credentials flow variable does not provide a password");
            }
        } else {
            account = m_account.getStringValue();
            secretKey = m_secretKey.getStringValue();
        }

        return new AzureStorageSharedKeyCredential(account, secretKey);
    }

    @Override
    public MicrosoftAuthProviderEditor createEditor(final MicrosoftAuthenticationNodeDialog parent,
            final Supplier<CredentialsProvider> credentialsSupplier) {
        return new AzureSharedKeyAuthProviderEditor(this, credentialsSupplier);
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_account.saveSettingsTo(settings);
        m_secretKey.saveSettingsTo(settings);
        m_useCredentials.saveSettingsTo(settings);
        m_credentialsName.saveSettingsTo(settings);
    }

    @Override
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_account.validateSettings(settings);
        m_secretKey.validateSettings(settings);
        m_useCredentials.validateSettings(settings);
        m_credentialsName.validateSettings(settings);

        var temp = new AzureSharedKeyAuthProvider();
        temp.loadSettingsFrom(settings);
        temp.validate();
    }

    /**
     * Validates consistency of the current settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        if (!m_useCredentials.getBooleanValue()) {
            if (m_account.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Account cannot be empty");
            }
            if (m_secretKey.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Secret key cannot be empty");
            }
        } else {
            if (m_credentialsName.getStringValue().isEmpty()) {
                throw new InvalidSettingsException("Credentials are not selected");
            }
        }
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_account.loadSettingsFrom(settings);
        m_secretKey.loadSettingsFrom(settings);
        m_credentialsName.loadSettingsFrom(settings);
        m_useCredentials.loadSettingsFrom(settings);
    }

}
