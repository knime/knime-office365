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
 *   2025-05-22 (bjoern): created
 */
package org.knime.ext.microsoft.authentication.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.knime.core.node.NodeLogger;
import org.knime.credentials.base.oauth.api.JWTCredential;

import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;

/**
 * Factory class for creating {@link JWTCredential} instances.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class JWTCredentialFactory {

    private static final NodeLogger LOG = NodeLogger.getLogger(JWTCredentialFactory.class);

    private static final Pattern WHITESPACES_PATTERN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private JWTCredentialFactory() {
        // prevent instantiation
    }

    /**
     * Creates {@link JWTCredential} that wraps the access/refresh tokens from the
     * provided authResult. Access tokens are refreshed using the access token in
     * the authResult.
     *
     * @param authResult
     *            The authentication result.
     * @param publicApp
     *            The public app to use for refreshing the access token.
     * @return The {@link JWTCredential}
     */
    public static JWTCredential create(final IAuthenticationResult authResult,
            final PublicClientApplication publicApp) {

        var accessToken = authResult.accessToken();
        var idToken = authResult.idToken();
        var expiresAfter = Optional.ofNullable(authResult.expiresOnDate())//
                .map(Date::toInstant)//
                .orElse(null);
        var tokenType = "Bearer";
        var scopes = extractScopes(authResult);

        try {
            return new JWTCredential(accessToken, tokenType, expiresAfter, idToken,
                    createPublicClientRefresher(publicApp, scopes));
        } catch (ParseException ex) {
            throw new IllegalStateException("Failed to parse authentication result");
        }
    }

    private static Set<String> extractScopes(final IAuthenticationResult authResult) {
        var scopesString = authResult.scopes();
        if (scopesString == null) {
            return Set.of();
        } else {
            return Set.of(WHITESPACES_PATTERN.split(scopesString));
        }
    }

    private static Supplier<JWTCredential> createPublicClientRefresher(final PublicClientApplication publicApp,
            final Set<String> scopes) {

        return () -> {// NOSONAR
            try {
                var params = SilentParameters.builder(scopes).build();
                var authResult = MSALUtil.doLogin(() -> publicApp.acquireTokenSilently(params));
                return create(authResult, publicApp);
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new UncheckedIOException(ex);
            }
        };
    }

    /**
     * Creates {@link JWTCredential} that wraps the access tokens from the provided
     * authResult. Expired access tokens are replaced with the client credentials
     * flow using the provided confidential app.
     *
     * @param authResult
     *            The authentication result.
     * @param confidentialApp
     *            The confidential app to use to acquire new access tokens.
     * @return The {@link JWTCredential}
     */
    public static JWTCredential create(final IAuthenticationResult authResult,
            final ConfidentialClientApplication confidentialApp) {

        var accessToken = authResult.accessToken();
        var idToken = authResult.idToken();
        var expiresAfter = Optional.ofNullable(authResult.expiresOnDate())//
                .map(Date::toInstant)//
                .orElse(null);
        var tokenType = "Bearer";
        var scopes = extractScopes(authResult);

        try {
            return new JWTCredential(accessToken, tokenType, expiresAfter, idToken,
                    createConfidentialClientRefresher(confidentialApp, scopes));
        } catch (ParseException ex) {
            throw new IllegalStateException("Failed to parse authentication result");
        }
    }

    private static Supplier<JWTCredential> createConfidentialClientRefresher(
            final ConfidentialClientApplication confidentialApp, final Set<String> scopes) {

        return () -> {// NOSONAR
            try {
                var params = SilentParameters.builder(scopes).build();
                var authResult = MSALUtil.doLogin(() -> confidentialApp.acquireTokenSilently(params));
                return create(authResult, confidentialApp);
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new UncheckedIOException(ex);
            }
        };
    }
}
