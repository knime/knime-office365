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

import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;

import com.google.gson.JsonObject;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.extensions.DirectoryObject;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;

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

    private static final String PROP_DISPLAY_NAME = "displayName";

    private GraphApiUtil() {
        // hide constructor for Utils class
    }

    /**
     *
     * <p>
     * Attempts to unwrap provided {@link ClientException}.
     * </p>
     * <p>
     * It iterates through the whole 'cause-chain' in attempt to find
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
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            cause = cause.getCause();
        }

        return ex;
    }

    /**
     * Gets the displayName from the raw JSON of the {@link DirectoryObject}.
     *
     * @param obj
     *            Directory object.
     * @return Display name or empty string if the property is missing.
     */
    public static String getDisplayName(final DirectoryObject obj) {
        JsonObject json = obj.getRawObject();
        if (json.has(PROP_DISPLAY_NAME)) {
            return json.get(PROP_DISPLAY_NAME).getAsString();
        }
        return "";
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
     * Creates a {@link IGraphServiceClient} with a {@link MicrosoftCredential}.
     *
     * @param connection
     *            the {@link MicrosoftCredential}
     * @return the {@link IGraphServiceClient}
     * @throws IOException
     */
    @SuppressWarnings("deprecation")
    public static IGraphServiceClient createClient(final MicrosoftCredential connection) throws IOException {
        final IAuthenticationProvider authProvider = createAuthenticationProvider(connection);

        return GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();
    }

    /**
     * Creates a {@link IAuthenticationProvider} with a {@link MicrosoftCredential}.
     *
     * @param connection
     *            the {@link MicrosoftCredential}
     * @return the {@link IAuthenticationProvider}
     * @throws IOException
     */
    @SuppressWarnings("deprecation")
    public static IAuthenticationProvider createAuthenticationProvider(final MicrosoftCredential connection)
            throws IOException {
        if (!(connection instanceof OAuth2Credential)) {
            throw new UnsupportedOperationException("Unsupported credential type: " + connection.getType());
        }

        final String accessToken = ((OAuth2Credential) connection).getAccessToken().getToken();

        return (r -> r.addHeader("Authorization", "Bearer " + accessToken));
    }

}
