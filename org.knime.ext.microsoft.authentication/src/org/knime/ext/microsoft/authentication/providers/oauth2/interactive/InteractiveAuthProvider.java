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
package org.knime.ext.microsoft.authentication.providers.oauth2.interactive;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProviderEditor;
import org.knime.ext.microsoft.authentication.providers.oauth2.MSALUtil;
import org.knime.ext.microsoft.authentication.providers.oauth2.OAuth2Provider;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage.StorageSettings;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;

/**
 * {@link OAuth2Provider} implementation that performs interactive
 * authentication by opening a browser window.
 *
 * @author Alexander Bondaletov
 */
public class InteractiveAuthProvider extends OAuth2Provider {

    private static final String REDIRECT_URL = "http://localhost:51355/";

    private final PortsConfiguration m_portsConfig;

    private final StorageSettings m_storageSettings;

    /**
     * Creates new instance.
     */
    public InteractiveAuthProvider(final PortsConfiguration portsConfig, final String nodeInstanceId) {
        m_portsConfig = portsConfig;
        m_storageSettings = new StorageSettings(portsConfig, nodeInstanceId, getAuthority());
    }

    /**
     * Performs login by opening browser window and then stores authentication
     * result.
     *
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws IOException
     */
    public LoginStatus performLogin() throws InterruptedException, ExecutionException, IOException {
        final PublicClientApplication app = MSALUtil.createClientApp(getAuthority());

        final InteractiveRequestParameters params = InteractiveRequestParameters
                .builder(URI.create(REDIRECT_URL))
                .scopes(getScopesStringSet()).build();

        final IAuthenticationResult result = app.acquireToken(params).get();

        m_storageSettings.writeTokenCache(app.tokenCache().serialize());
        return LoginStatus.fromAuthenticationResult(result);
    }

    public LoginStatus getLoginStatus() throws IOException {
        return m_storageSettings.getLoginStatus();
    }

    @Override
    public MicrosoftCredential getCredential(final CredentialsProvider credentialsProvider) throws IOException {
        final LoginStatus loginStatus = m_storageSettings.getLoginStatus();

        if (loginStatus == LoginStatus.NOT_LOGGED_IN) {
            throw new IOException("Access token not available anymore. Please login in the node dialog again.");
        }

        return new OAuth2Credential(
                m_storageSettings.createAccessTokenSupplier(), //
                loginStatus.getUsername(), //
                loginStatus.getAccessTokenExpiry(), //
                getScopesEnumSet(), //
                getAuthority());
    }

    @Override
    protected String getAuthority() {
        return MSALUtil.COMMON_AUTHORITY;
    }

    @Override
    public MicrosoftAuthProviderEditor createEditor(final MicrosoftAuthenticationNodeDialog parent) {
        return new InteractiveAuthProviderEditor(this, parent);
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        m_storageSettings.saveSettingsTo(settings);
    }

    @Override
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        InteractiveAuthProvider temp = new InteractiveAuthProvider(m_portsConfig, "");
        temp.loadSettingsFrom(settings);
        temp.validate();
    }

    @Override
    public void validate() throws InvalidSettingsException {
        super.validate();
        m_storageSettings.validate();
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.loadSettingsFrom(settings);
        m_storageSettings.loadSettingsFrom(settings);
    }

    public StorageSettings getStorageSettings() {
        return m_storageSettings;
    }

    @Override
    public void clearMemoryTokenCache() {
        m_storageSettings.clearMemoryTokenCache();
    }
}
