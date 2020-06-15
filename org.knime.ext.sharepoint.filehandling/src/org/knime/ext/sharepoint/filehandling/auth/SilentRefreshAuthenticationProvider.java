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
 *   2020-06-06 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.auth;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.requests.extensions.GraphServiceClient;

/**
 * {@link IAuthenticationProvider} implementation used to authenticate
 * {@link GraphServiceClient}.<br>
 *
 * Should be initialized with {@link PublicClientApplication} instance with
 * token cache restored. Signs every {@link IHttpRequest} with a short-lived
 * access token and automatically refreshes this token using information stored
 * in the client's token cache.
 *
 * @author Alexander Bondaletov
 */
public class SilentRefreshAuthenticationProvider implements IAuthenticationProvider {
    private final Set<String> m_scopes;
    private final PublicClientApplication m_client;

    private String m_accessToken;
    private Date m_expiresOn;

    /**
     * Creates new instance.
     *
     * @param scopes
     *            Requested scopes.
     * @param client
     *            The client instance. Should have it's token cache
     *            initialized/restored.
     *
     */
    public SilentRefreshAuthenticationProvider(final Set<String> scopes, final PublicClientApplication client) {
        m_scopes = scopes;
        m_client = client;
        m_expiresOn = new Date(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void authenticateRequest(final IHttpRequest request) {
        request.addHeader("Authorization", "Bearer " + getAccessToken());
    }

    private String getAccessToken() {
        if (m_accessToken == null || m_expiresOn.before(new Date())) {
            try {
                refreshToken();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (MalformedURLException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return m_accessToken;
    }

    private void refreshToken()
            throws MalformedURLException, InterruptedException, ExecutionException {
        IAccount account = m_client.getAccounts().get().iterator().next();
        IAuthenticationResult result = m_client
                .acquireTokenSilently(SilentParameters.builder(m_scopes, account).build()).get();
        m_accessToken = result.accessToken();
        m_expiresOn = result.expiresOnDate();
    }

}
