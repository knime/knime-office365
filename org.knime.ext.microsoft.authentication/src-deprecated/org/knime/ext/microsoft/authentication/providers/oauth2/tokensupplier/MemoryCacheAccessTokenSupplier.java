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
 *   2020-07-03 (bjoern): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2AccessToken;
import org.knime.ext.microsoft.authentication.providers.MemoryCredentialCache;

/**
 * Access token supplier that reads the MSAL4J token cache from the JVM global
 * {@link MemoryCredentialCache} and uses the contained refresh token to produce a
 * fresh access token when needed.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public abstract class MemoryCacheAccessTokenSupplier {

    private static final String KEY_CACHE_KEY = "cacheKey";

    /** The endpoint to use during token refresh. **/
    protected final String m_endpoint;

    /** The Application (client) ID **/
    protected final String m_appId;

    /** The cache key to use against the in-memory token cache. **/
    protected String m_cacheKey;

    /**
     * Constructor (used for deserialization only).
     *
     * @param endpoint
     *            The endpoint to use during token refresh.
     * @param appId
     *            The Application (client) ID
     */
    protected MemoryCacheAccessTokenSupplier(final String endpoint, final String appId) {
        this(endpoint, null, appId);
    }

    /**
     * Constructor.
     *
     * @param endpoint
     *            The endpoint to use during token refresh.
     * @param cacheKey
     *            The cache key to use against the in-memory token cache.
     * @param appId
     *            The Application (client) ID
     */
    protected MemoryCacheAccessTokenSupplier(final String endpoint, final String cacheKey, final String appId) {
        m_endpoint = endpoint;
        m_cacheKey = cacheKey;
        m_appId = appId;
    }

    /**
     * @return the OAuth2 authorization endpoint
     */
    public String getEndpoint() {
        return m_endpoint;
    }

    /**
     * @param scopes
     *            set of scopes
     * @return {@link OAuth2AccessToken} access token
     * @throws IOException
     */
    public abstract OAuth2AccessToken getAuthenticationResult(final Set<String> scopes) throws IOException;

    /**
     * @param ex
     *            instance of exception
     * @return unwrapped IOException
     */
    protected static IOException unwrapIOE(final ExecutionException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IOException) {
                return (IOException) cause;
            }
            cause = cause.getCause();
        }
        return new IOException("Error during refreshing access token", ex.getCause());
    }

    /**
     * Saves settings to the given {@link ConfigWO}.
     *
     * @param config
     */
    public void saveSettings(final ConfigWO config) {
        config.addString(KEY_CACHE_KEY, m_cacheKey);
    }

    /**
     * Loads settings from the given {@link ConfigRO}.
     *
     * @param config
     * @throws InvalidSettingsException
     */
    public void loadSettings(final ConfigRO config) throws InvalidSettingsException {
        m_cacheKey = config.getString(KEY_CACHE_KEY);
    }
}
