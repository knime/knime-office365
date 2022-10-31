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
 *   2022-10-18 (Zkriya Rakhimberdiyev): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2AccessToken;
import org.knime.ext.microsoft.authentication.providers.MemoryCredentialCache;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IClientCredential;
import com.microsoft.aad.msal4j.SilentParameters;

/**
 * Token supplier for application permissions.
 *
 * @author Zkriya Rakhimberdiyev
 */
public class ApplicationPermissionsTokenSupplier extends MemoryCacheAccessTokenSupplier {

    private static final String KEY_SECRET_CACHE_KEY = "secretCacheKey";

    private String m_secretCacheKey;

    /**
     * Constructor (used for deserialization only).
     *
     * @param endpoint
     *            The endpoint to use during token refresh.
     * @param clientId
     *            The Application (client) ID
     */
    public ApplicationPermissionsTokenSupplier(final String endpoint, final String clientId) {
        super(endpoint, clientId);
    }

    /**
     * Constructor.
     *
     * @param endpoint
     *            The endpoint to use during token refresh.
     * @param cacheKey
     *            The cache key to use against the in-memory token cache.
     * @param clientId
     *            The Application (client) ID
     * @param secretCacheKey
     *            The secrect cache key to use against the in-memory cache.
     */
    public ApplicationPermissionsTokenSupplier(final String endpoint, final String cacheKey, final String clientId,
            final String secretCacheKey) {
        super(endpoint, cacheKey, clientId);
        m_secretCacheKey = secretCacheKey;
    }

    @Override
    public OAuth2AccessToken getAuthenticationResult(final Set<String> scopes) throws IOException {
        final var app = createConfidentialClientApplication();

        try {
            IAuthenticationResult result = app.acquireTokenSilently(SilentParameters.builder(scopes).build())
                    .get();

            return new OAuth2AccessToken(result.accessToken(), result.expiresOnDate().toInstant());
        } catch (InterruptedException e) { // NOSONAR rethrowing as cause of IOE
            throw new IOException("Canceled while acquiring access token", e);
        } catch (ExecutionException ex) {
            throw unwrapIOE(ex);
        }
    }

    private ConfidentialClientApplication createConfidentialClientApplication() throws IOException {
        final var tokenCacheString = MemoryCredentialCache.get(m_cacheKey);
        if (StringUtils.isBlank(tokenCacheString)) {
            throw new IOException("No access token found. Please re-execute the Microsoft Authentication node.");
        }
        final var secret = MemoryCredentialCache.get(m_secretCacheKey);
        if (StringUtils.isBlank(secret)) {
            throw new IOException("No secret found. Please re-execute the Microsoft Authentication node.");
        }

        IClientCredential credential = ClientCredentialFactory.createFromSecret(secret);
        ConfidentialClientApplication app = ConfidentialClientApplication.builder(m_appId, credential)
                .authority(m_endpoint).build();

        app.tokenCache().deserialize(tokenCacheString);
        return app;
    }

    @Override
    public void saveSettings(final ConfigWO config) {
        super.saveSettings(config);
        config.addString(KEY_SECRET_CACHE_KEY, m_secretCacheKey);
    }

    @Override
    public void loadSettings(final ConfigRO config) throws InvalidSettingsException {
        super.loadSettings(config);
        if (config.containsKey(KEY_SECRET_CACHE_KEY)) {
            m_secretCacheKey = config.getString(KEY_SECRET_CACHE_KEY);
        }
    }
}
