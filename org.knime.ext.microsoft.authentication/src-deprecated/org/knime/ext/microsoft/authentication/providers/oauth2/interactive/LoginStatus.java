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
import java.io.StringReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.microsoft.aad.msal4j.IAuthenticationResult;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;

/**
 * Captures the current OAuth2 login status. Main use case is displaying it in
 * the node dialog.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class LoginStatus {

    /**
     * Predefined {@link LoginStatus} when we are not logged in.
     */
    public static final LoginStatus NOT_LOGGED_IN = new LoginStatus(null, null);

    private final String m_username;

    private final Instant m_accessTokenExpiry;

    private LoginStatus(final String username, final Instant accessTokenExpiry) {
        m_username = username;
        m_accessTokenExpiry = accessTokenExpiry;
    }

    /**
     *
     * @return whether we are currently logged in.
     */
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

    /**
     * Parse the given token cache string for a valid access token.
     *
     * @param tokenCache
     *            The given token cache string to check.
     * @return the {@link LoginStatus} corresponding to the given token cache
     *         string.
     * @throws IOException
     *             When something went wrong while trying to parse the token cache
     *             string.
     */
    public static LoginStatus parseFromTokenCache(final String tokenCache) throws IOException {
        try (final var strReader = new StringReader(tokenCache); final var jsonReader = Json.createReader(strReader)) {
            final var obj = jsonReader.readObject();

            final var accessTokenObj = obj.getJsonObject("AccessToken");

            final var expiresOnJsonValue = accessTokenObj.getJsonObject(accessTokenObj.keySet().iterator().next())//
                    .get("expires_on");

            final long expiresOnSecs;
            if (expiresOnJsonValue instanceof JsonString jsonStr) {
                expiresOnSecs = Long.parseLong(jsonStr.getString());
            } else if (expiresOnJsonValue instanceof JsonNumber jsonNum) {
                expiresOnSecs = jsonNum.longValue();
            } else {
                // will assume expiry in the distant future...
                expiresOnSecs = Instant.now().plus(100, ChronoUnit.DAYS).getEpochSecond();
            }

            final var accountObj = obj.getJsonObject("Account");
            final var username = accountObj.getJsonObject(accountObj.keySet().iterator().next()).getString("username");
            return new LoginStatus(username, Instant.ofEpochSecond(expiresOnSecs));
        } catch (Exception e) { // NOSONAR intentionally catching a generic one here
            throw new IOException("Could not read token", e);
        }
    }

    /**
     * Creates a {@link LoginStatus} from the given {@link IAuthenticationResult}.
     *
     * @param result
     *            The {@link IAuthenticationResult} to check.
     * @return a {@link LoginStatus} created from the given
     *         {@link IAuthenticationResult}.
     */
    public static LoginStatus fromAuthenticationResult(final IAuthenticationResult result) {
        return new LoginStatus(result.account().username(), result.expiresOnDate().toInstant());
    }

    @Override
    public int hashCode() {
        final var prime = 31;
        var result = 1;
        result = prime * result + ((m_accessTokenExpiry == null) ? 0 : m_accessTokenExpiry.hashCode());
        result = prime * result + ((m_username == null) ? 0 : m_username.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        LoginStatus other = (LoginStatus) obj;
        if (m_accessTokenExpiry == null) {
            if (other.m_accessTokenExpiry != null) {
                return false;
            }
        } else if (!m_accessTokenExpiry.equals(other.m_accessTokenExpiry)) {
            return false;
        }
        if (m_username == null) {
            if (other.m_username != null) {
                return false;
            }
        } else if (!m_username.equals(other.m_username)) {
            return false;
        }
        return true;
    }

}
