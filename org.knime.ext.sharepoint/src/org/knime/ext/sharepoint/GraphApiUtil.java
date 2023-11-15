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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import org.knime.core.node.Node;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;
import org.knime.okhttp3.OkHttpProxyAuthenticator;
import org.osgi.framework.FrameworkUtil;

import com.azure.core.credential.AccessToken;
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
     * Creates a {@link GraphServiceClient} with a {@link Credential}.
     *
     * @param credential
     *            the credential
     * @param connectionTimeout
     *            Connection timeout in milliseconds.
     * @param readTimeout
     *            Read timeout in milliseconds.
     * @return the {@link GraphServiceClient}
     */
    public static GraphServiceClient<Request> createClient(final JWTCredential credential,
            final int connectionTimeout,
            final int readTimeout) {

        final var authProvider = createAuthenticationProvider(credential);
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
     * Creates a {@link IAuthenticationProvider} based on the given
     * {@link JWTCredential}.
     *
     * @param credential
     *            The credential.
     * @return the {@link IAuthenticationProvider}
     */
    public static IAuthenticationProvider createAuthenticationProvider(final JWTCredential credential) {
        // as is the nature of monos, this will defer the actual retrieval of the access
        // token to when it
        // is fact needed.
        final var accessTokenMono = Mono.fromCallable(() -> toMsalAccessToken(credential));
        return new TokenCredentialAuthProvider(ignored -> accessTokenMono);
    }

    private static AccessToken toMsalAccessToken(final JWTCredential credential) throws IOException {
        final var accessToken = credential.getAccessToken(); // potentially throws IOE when token cannot be refreshed
        final var expiration = credential.getExpiresAfter()
                .map(instant -> OffsetDateTime.ofInstant(instant, ZoneId.of("UTC")))
                .orElseThrow();

        return new AccessToken(accessToken, expiration);
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
