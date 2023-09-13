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
package org.knime.ext.microsoft.authentication.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.regex.Pattern;

import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;

import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IClientCredential;
import com.microsoft.aad.msal4j.MsalClientException;
import com.microsoft.aad.msal4j.MsalException;
import com.microsoft.aad.msal4j.PublicClientApplication;

/**
 * Utility class the help with MSAL4J.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class MSALUtil {

    /**
     * The default Application (client) ID
     */
    public static final String DEFAULT_APP_ID = "cf47ff49-7da6-4603-b339-f4475176432b";

    /**
     * Common OAuth2 authorization endpoint to sign in work or school accounts and
     * personal Microsoft accounts (MSA), such as hotmail.com, outlook.com, and
     * msn.com.
     *
     * See list of available endpoints here:
     * https://docs.microsoft.com/en-us/azure/active-directory/develop/msal-client-application-configuration
     */
    public static final String COMMON_ENDPOINT = "https://login.microsoftonline.com/common";

    /**
     * OAuth2 authorization endpoint to sign in users with work and school accounts.
     *
     * See list of available endpoints here:
     * https://docs.microsoft.com/en-us/azure/active-directory/develop/msal-client-application-configuration
     */
    public static final String ORGANIZATIONS_ENDPOINT = "https://login.microsoftonline.com/organizations";

    private MSALUtil() {
    }

    /**
     * Creates the {@link PublicClientApplication} instance.
     *
     * @param appId
     *            The Application (client) ID.
     * @param endpoint
     *            The OAuth authorization endpoint URL to use with the
     *            {@link PublicClientApplication}.
     *
     * @return the {@link PublicClientApplication}.
     */
    public static PublicClientApplication createClientApp(final String appId, final String endpoint) {
        try {
            return PublicClientApplication.builder(appId) //
                    .authority(endpoint) //
                    .httpClient(new OkHttpClientAdapter()) //
                    .build();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    /**
     * Creates the {@link PublicClientApplication} instance.
     *
     * @param appId
     *            The Application (client) ID
     * @param endpoint
     *            The OAuth authorization endpoint URL to use with the
     *            {@link PublicClientApplication}.
     * @param tokenCache
     *            A string from which to load access/refresh token into the
     *            {@link PublicClientApplication}.
     * @return the {@link PublicClientApplication}.
     * @throws IOException
     *             when something went wrong while trying to load the access/refresh
     *             token.
     */
    public static PublicClientApplication createClientAppWithToken(final String appId, final String endpoint,
            final String tokenCache) throws IOException {
        try {
            final PublicClientApplication app = createClientApp(appId, endpoint);
            app.tokenCache().deserialize(tokenCache);
            return app;
        } catch (MsalClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Creates a {@link ConfidentialClientApplication} instance.
     *
     * @param appId
     *            The Application (client) ID.
     * @param endpoint
     *            The OAuth authorization endpoint URL to use with the
     *            {@link ConfidentialClientApplication}.
     * @param secret
     *            The secret to use to authenticate as the application.
     *
     * @return the {@link ConfidentialClientApplication}.
     */
    public static ConfidentialClientApplication createConfidentialApp(final String appId, final String endpoint,
            final IClientCredential secret) {

        try {
            return ConfidentialClientApplication.builder(appId, secret) //
                    .authority(endpoint) //
                    .httpClient(new OkHttpClientAdapter()) //
                    .build();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    /**
     * Attempts to produce a good error message for the given error, with special
     * handling for {@link MsalException}s.
     *
     * @param error
     *            An error that occured during login with MSAL4J.
     * @return A (hopefully) useful error message.
     */
    public static String formatException(final Throwable error) {
        String message = null;

        var msalEx = extractMsalException(error);
        if (msalEx != null) {
            message = removeLeadingExceptionType(msalEx.getMessage());
        }

        if (message == null) {
            message = ExceptionUtil.getDeepestNIOErrorMessage(error);
        }

        if (message == null) {
            message = ExceptionUtil.getDeepestErrorMessage(error, true);
        }

        if (message == null) {
            message = String.format("An error occured (%s)", error.getClass().getSimpleName());
        }

        return removeLeadingExceptionType(message);
    }

    private static final Pattern LEADING_EXCEPTION_PATTERN = Pattern
            .compile("^([\\w.]+\\.[\\w]+Exception: )(.+)$"); // NOSONAR

    private static String removeLeadingExceptionType(String message) {
        message = message.trim();

        var matcher = LEADING_EXCEPTION_PATTERN.matcher(message);
        while (matcher.matches()) {
            message = matcher.group(2);
            matcher = LEADING_EXCEPTION_PATTERN.matcher(message);
        }

        return message;
    }

    private static MsalException extractMsalException(final Throwable ex) {
        var current = ex;
        while (current != null) {
            if (current instanceof MsalException msalEx) {
                return msalEx;
            }
            current = current.getCause();
        }
        return null;
    }
}
