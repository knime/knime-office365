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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.port.oauth2.Scope;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;

/**
 * Abstract base class for OAuth2 access token suppliers. This base class will
 * use the refresh token contained in the MSAL4J token cache to produce a fresh
 * access token when needed.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public abstract class BaseAccessTokenSupplier {

    /**
     * Types of suppliers for OAuth2 access tokens.
     *
     * @author bjoern
     */
    public enum SupplierType {
        FILE, MEMORY;
    }

    static final String KEY_TOKEN_SUPPLIER_TYPE = "tokenSupplierType";

    private final SupplierType m_type;

    private final String m_authority;

    public BaseAccessTokenSupplier(final String authority, final SupplierType type) {
        m_type = type;
        m_authority = authority;
    }

    /**
     * @return the authority
     */
    public String getAuthority() {
        return m_authority;
    }

    public void saveSettings(final ConfigWO config) {
        config.addString(KEY_TOKEN_SUPPLIER_TYPE, m_type.toString());
    }

    public abstract void loadSettings(ConfigRO config) throws InvalidSettingsException;

    public final String getAccessToken(final EnumSet<Scope> scopes) throws IOException {
        final PublicClientApplication app = createPublicClientApplication();

        try {
            IAccount account = app.getAccounts().get().iterator().next();

            final Set<String> scopeStrings = new HashSet<>();
            scopes.stream().map((s) -> s.getScope()).forEach(scopeStrings::add);

            IAuthenticationResult result = app
                    .acquireTokenSilently(SilentParameters.builder(scopeStrings, account).build())
                    .get();
            return result.accessToken();
        } catch (InterruptedException e) {
            throw new IOException("Canceled while acquiring access token", e);
        } catch (ExecutionException ex) {
            throw unwrapIOE(ex);
        }
    }

    protected abstract PublicClientApplication createPublicClientApplication() throws IOException;

    private static IOException unwrapIOE(final ExecutionException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IOException) {
                return (IOException) cause;
            }
            cause = cause.getCause();
        }
        return new IOException("Error during refreshing access token", ex.getCause());
    }
}
