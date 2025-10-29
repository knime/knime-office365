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
package org.knime.ext.sharepoint;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.regex.Pattern;

import org.knime.core.node.InvalidSettingsException;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.credentials.base.oauth.api.AccessTokenAccessor;
import org.knime.credentials.base.oauth.api.AccessTokenWithScopesAccessor;
import org.knime.credentials.base.oauth.api.IdentityProviderException;

import com.azure.core.credential.AccessToken;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;

import reactor.core.publisher.Mono;

/**
 * Utility class to create an {@link IAuthenticationProvider} (Graph library)
 * from KNIME {@link Credential}.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class GraphCredentialUtil {

    private static final String GRAPH_API_SCOPE = "https://graph.microsoft.com/.default";

    private static final Pattern ERROR_PREFIX = Pattern.compile("^\\s*AADSTS(\\d+)", Pattern.CASE_INSENSITIVE); // NOSONAR

    private GraphCredentialUtil() {
        // Utility class, no instantiation
    }

    /**
     * Checks the credential port object spec provided to the node mode during
     * configure.
     *
     * @param credSpec
     *            The port object spec provided to the node mode during configure.
     *
     * @throws InvalidSettingsException
     *             if the credential is not present or incompatible
     */
    public static void validateCredentialPortObjectSpecOnConfigure(final CredentialPortObjectSpec credSpec)
            throws InvalidSettingsException {

        if (credSpec.isPresent()) {
            try {
                final var isCompatible = credSpec.hasAccessor(AccessTokenAccessor.class)
                        || credSpec.hasAccessor(AccessTokenWithScopesAccessor.class);

                if (!isCompatible) {
                    throw new InvalidSettingsException("Provided credential cannot be used with SharePoint");
                }
            } catch (NoSuchCredentialException ex) {
                throw new InvalidSettingsException(ex.getMessage(), ex);
            }
        }
    }

    /**
     * Creates a {@link IAuthenticationProvider} based on the given
     * {@link CredentialPortObjectSpec}. This tries to fetch an access token with
     * the MS Graph scope, which may fail, hence the method throws an
     * {@link IOException}.
     *
     * @param credSpec
     *            The ingoing {@link CredentialPortObjectSpec} during execute().
     * @return a {@link IAuthenticationProvider} wraps the ingoing KNIME credential
     * @throws NoSuchCredentialException
     *             If no credential was found.
     * @throws IOException
     *             If an access token with MS Graph scope could not be fetched.
     */
    public static IAuthenticationProvider createAuthenticationProvider(
            final CredentialPortObjectSpec credSpec) throws NoSuchCredentialException, IOException {

        final AccessTokenAccessor tokenAccessor;

        if (credSpec.hasAccessor(AccessTokenAccessor.class)) {
            tokenAccessor = credSpec.toAccessor(AccessTokenAccessor.class);
        } else if (credSpec.hasAccessor(AccessTokenWithScopesAccessor.class)) {
            try {
                tokenAccessor = credSpec.toAccessor(AccessTokenWithScopesAccessor.class)
                        .getAccessTokenWithScopes(Set.of(GRAPH_API_SCOPE));
            } catch (IdentityProviderException e) {
                throw handleIdentityProviderException(e);
            }
        } else {
            // this should never happen, as we already validated the
            // credential spec in the configure phase
            throw new IllegalStateException("The provided credential is incompatible.");
        }

        return createAuthenticationProvider(tokenAccessor);
    }

    /**
     * Wraps the given {@link AccessTokenAccessor} instance in a
     * {@link IAuthenticationProvider}, which can be used with the Graph API client.
     *
     * @param tokenAccessor
     *            Accessor instance for the access token.
     * @return the {@link IAuthenticationProvider}
     */
    public static IAuthenticationProvider createAuthenticationProvider(final AccessTokenAccessor tokenAccessor) {
        // as is the nature of monos, this will defer the actual retrieval of the access
        // token to when it
        // is fact needed.
        final var accessTokenMono = Mono.fromCallable(() -> toMsalAccessToken(tokenAccessor));
        return new TokenCredentialAuthProvider(ignored -> accessTokenMono);
    }

    private static AccessToken toMsalAccessToken(final AccessTokenAccessor tokenAccessor) throws IOException {
        final var accessToken = tokenAccessor.getAccessToken(); // potentially throws IOE when token cannot be refreshed
        final var expiration = tokenAccessor.getExpiresAfter()
                .map(instant -> OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"))).orElseThrow();

        return new AccessToken(accessToken, expiration);
    }

    private static IOException handleIdentityProviderException(final IdentityProviderException e) {
        final var matcher = ERROR_PREFIX.matcher(e.getErrorSummary());
        if (matcher.find()) {
            final var errorCode = Integer.parseInt(matcher.group(1));
            // See
            // https://learn.microsoft.com/en-us/entra/identity-platform/reference-error-codes#aadsts-error-codes
            if (errorCode == 65001 || errorCode == 65004) {
                return new IOException("Consent mssing. Please refer to the node description to find "
                        + "scopes your or your admin need to consent to.", e);
            }
        }
        return e;
    }
}
