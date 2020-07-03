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
 *   2020-07-04 (bjoern): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.interactive;

import java.io.IOException;
import java.time.Instant;

import org.json.JSONObject;

import com.microsoft.aad.msal4j.IAuthenticationResult;

/**
 * Captures the current OAuth2 login status. Main use case is displaying it in
 * the node dialog.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public class LoginStatus {

    public final static LoginStatus NOT_LOGGED_IN = new LoginStatus(null, null);

    private final String m_username;

    private final Instant m_accessTokenExpiry;

    private LoginStatus(final String username, final Instant accessTokenExpiry) {
        m_username = username;
        m_accessTokenExpiry = accessTokenExpiry;
    }

    public boolean isLoggedIn() {
        return m_username != null;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return m_username;
    }

    /**
     * @return the accessTokenExpiresOn
     */
    public Instant getAccessTokenExpiry() {
        return m_accessTokenExpiry;
    }

    public static LoginStatus parseFromTokenCache(final String tokenCache) throws IOException {
        try {
            final JSONObject obj = new JSONObject(tokenCache);

            final JSONObject accessTokenObj = obj.getJSONObject("AccessToken");
            final long expiresOnSecs = accessTokenObj.getJSONObject((String) accessTokenObj.keys().next())
                    .getLong("expires_on");

            final JSONObject accountObj = obj.getJSONObject("Account");
            final String username = accountObj.getJSONObject((String) accountObj.keys().next()).getString("username");
            return new LoginStatus(username, Instant.ofEpochSecond(expiresOnSecs));
        } catch (Exception e) {
            throw new IOException("Could not read token", e);
        }
    }

    public static LoginStatus fromAuthenticationResult(final IAuthenticationResult result) {
        return new LoginStatus(result.account().username(), result.expiresOnDate().toInstant());
    }

}
