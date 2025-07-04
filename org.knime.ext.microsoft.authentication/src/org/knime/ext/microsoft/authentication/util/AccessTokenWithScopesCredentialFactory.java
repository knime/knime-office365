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
import java.util.concurrent.ExecutionException;

import org.knime.core.node.NodeLogger;
import org.knime.credentials.base.oauth.api.AccessTokenWithScopesCredential;

import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;

/**
 * Factory class to create {@link AccessTokenWithScopesCredential} instances.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class AccessTokenWithScopesCredentialFactory {

    private static final NodeLogger LOG = NodeLogger.getLogger(AccessTokenWithScopesCredentialFactory.class);

    private AccessTokenWithScopesCredentialFactory() {
        // prevent instantiation
    }

    /**
     * Creates a new {@link AccessTokenWithScopesCredential} for the given public
     * client application.
     *
     * @param publicApp
     *            The application.
     * @return A new {@link AccessTokenWithScopesCredential} that uses the refresh
     *         token in the given public app to fetch an access tokens on-demand.
     */
    public static AccessTokenWithScopesCredential create(final PublicClientApplication publicApp) {
        return new AccessTokenWithScopesCredential(scopes -> { // NOSONAR lambda expression is not too long
            try {
                var params = SilentParameters.builder(scopes)//
                        .account(publicApp.getAccounts().get().iterator().next())//
                        .forceRefresh(true)//
                        .build();
                var authResult = MSALUtil.doLogin(() -> publicApp.acquireTokenSilently(params));
                return JWTCredentialFactory.create(authResult, publicApp);
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new UncheckedIOException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // restore interrupted status
                throw new UncheckedIOException(new IOException(ex));
            } catch (ExecutionException ex) { // NOSONAR ExecutionException is just a wrapper
                throw new UncheckedIOException(new IOException(ex.getCause()));
            }
        });
    }

    /**
     * Creates a new {@link AccessTokenWithScopesCredential} for the given
     * confidential application. This uses the client credentials flow to acquire
     * access tokens on demand for the ad-hoc requested scopes.
     *
     * @param confidentialApp
     *            A confidential client application.
     * @return a new {@link AccessTokenWithScopesCredential} that uses the client
     *         credentials flow to acquire access tokens on demand for the ad-hoc
     *         requested scopes.
     */
    public static AccessTokenWithScopesCredential create(final ConfidentialClientApplication confidentialApp) {
        return new AccessTokenWithScopesCredential(scopes -> { // NOSONAR lambda expression is not too long
            try {
                var params = ClientCredentialParameters.builder(scopes).build();
                var authResult = MSALUtil.doLogin(() -> confidentialApp.acquireToken(params));
                return JWTCredentialFactory.create(authResult, confidentialApp, scopes);
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new UncheckedIOException(ex);
            }
        });
    }
}
