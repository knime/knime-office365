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
 *   2020-05-10 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import org.knime.core.node.Node;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;
import org.knime.okhttp3.OkHttpProxyAuthenticator;
import org.osgi.framework.FrameworkUtil;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.httpcore.HttpClients;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import reactor.core.publisher.Mono;

/**
 * Utility class for Graph API.
 *
 * @author Alexander Bondaletov
 */
public final class GraphApiUtil {
    /**
     * oDataType corresponds to a Group
     */
    public static final String GROUP_DATA_TYPE = "#microsoft.graph.group";

    private GraphApiUtil() {
        // hide constructor for Utils class
    }

    /**
     *
     * <p>
     * Attempts to unwrap provided {@link ClientException}.
     * </p>
     * <p>
     * It iterates through the whole 'cause-chain' in attempt to find the deepest
     * {@link IOException} and throws it if one is found.<br>
     * </p>
     * <p>
     * In case no {@link IOException} occurs the original exception is returned.
     * </p>
     *
     * @param ex
     *            The exception.
     * @return {@link IOException} instance or the original exception
     *
     * @throws IOException
     */
    public static RuntimeException unwrapIOE(final ClientException ex) throws IOException {
        var optIoe = ExceptionUtil.getLastThrowable(ex, IOException.class::isInstance);
        if (optIoe.isPresent()) {
            throw (IOException) optIoe.get();
        } else {
            return ex;
        }
    }

    /**
     *
     * @param urlString
     *            Web URL that a user entered, which points to a Sharepoint site.
     * @return the site ID as a string.
     * @throws MalformedURLException
     */
    public static String getSiteIdFromSharepointSiteWebURL(final String urlString) throws MalformedURLException {
        final var url = new URL(urlString);
        String result = url.getHost();

        if (url.getPath() != null && !url.getPath().isEmpty()) {
            result += ":" + url.getPath();
        }

        return result;
    }

    /**
     * Creates a {@link GraphServiceClient} with a {@link MicrosoftCredential}.
     *
     * @param connection
     *            the {@link MicrosoftCredential}
     * @param connectionTimeout
     *            Connection timeout in milliseconds.
     * @param readTimeout
     *            Read timeout in milliseconds.
     * @return the {@link GraphServiceClient}
     * @throws IOException
     */
    public static GraphServiceClient<Request> createClient(final MicrosoftCredential connection,
            final int connectionTimeout,
            final int readTimeout) throws IOException {

        final IAuthenticationProvider authProvider = createAuthenticationProvider(connection);
        return createClient(authProvider, connectionTimeout, readTimeout);
    }

    /**
     * Creates a {@link GraphServiceClient} with a {@link IAuthenticationProvider}.
     *
     * @param authProvider
     *            The {@link IAuthenticationProvider}
     * @param connectionTimeout
     *            Connection timeout in milliseconds.
     * @param readTimeout
     *            Read timeout in milliseconds.
     * @return the {@link GraphServiceClient}
     */
    public static GraphServiceClient<Request> createClient(final IAuthenticationProvider authProvider,
            final int connectionTimeout, final int readTimeout) {
        return GraphServiceClient.builder()//
                .httpClient(createOkHttpClient(authProvider, connectionTimeout, readTimeout))
                .buildClient();
    }

    private static OkHttpClient createOkHttpClient(final IAuthenticationProvider authProvider,
            final int connectionTimeout, final int readTimeout) {
        return HttpClients.createDefault(authProvider)//
                .newBuilder()//
                .connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS) //
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS) //
                .retryOnConnectionFailure(true) // To address this bug
                                                // (https://github.com/microsoftgraph/msgraph-sdk-java/issues/313).
                .proxyAuthenticator(new OkHttpProxyAuthenticator()) // Proxy authentication
                .addInterceptor(new UserAgentInterceptor()) // Adds user-agent header
                .build();
    }

    /**
     * Creates a {@link IAuthenticationProvider} with a {@link MicrosoftCredential}.
     *
     * @param msCredential
     *            the {@link MicrosoftCredential}
     * @return the {@link IAuthenticationProvider}
     * @throws IOException
     *             if the (new) token could not be accessed.
     */
    public static IAuthenticationProvider createAuthenticationProvider(final MicrosoftCredential msCredential)
            throws IOException {

        if (!(msCredential instanceof OAuth2Credential)) {
            throw new IOException("Unsupported credential type: " + msCredential.getType());
        }

        return new TokenCredentialAuthProvider(new RefreshingTokenCredential((OAuth2Credential) msCredential));
    }

    private static class RefreshingTokenCredential implements TokenCredential {

        private final OAuth2Credential m_credential;

        private AccessToken m_accessToken;

        private long m_lastAccessTokenRefresh;

        RefreshingTokenCredential(final OAuth2Credential credential) {
            m_credential = credential;
        }

        @Override
        public Mono<AccessToken> getToken(final TokenRequestContext request) {
            return Mono.fromCallable(this::getTokenAndRefreshIfNecessary);
        }

        private synchronized AccessToken getTokenAndRefreshIfNecessary() throws IOException {
            if (m_accessToken == null || tokenMightNeedRefresh()) {
                // we don't want to call m_credential.getAccessToken() for EVERY request
                // (because it might be a little heavyweight) but we still do it frequently
                // (max every 5s)
                m_lastAccessTokenRefresh = System.currentTimeMillis();
                var oauthToken = m_credential.getAccessToken();
                m_accessToken = new AccessToken(oauthToken.getToken(), //
                        oauthToken.getAccessTokenExpiresAt().atOffset(ZoneOffset.of("Z")));
            }

            return m_accessToken;
        }

        private boolean tokenMightNeedRefresh() {
            return (System.currentTimeMillis() - m_lastAccessTokenRefresh) >= 5000;
        }
    }

    private static class UserAgentInterceptor implements Interceptor {

        private static final String USER_AGENT = "ISV|KNIME|Analytics Platform/" + getVersion();

        private static String getVersion() {
            var bundle = FrameworkUtil.getBundle(Node.class);
            if (bundle != null) {
                return bundle.getVersion().getMajor() + "." + bundle.getVersion().getMinor();
            } else {
                return "Unknown";
            }
        }

        @Override
        public Response intercept(final Chain chain) throws IOException {
            var request = chain.request() //
                    .newBuilder() //
                    .header("User-Agent", USER_AGENT) //
                    .build();

            return chain.proceed(request);
        }

    }
}
