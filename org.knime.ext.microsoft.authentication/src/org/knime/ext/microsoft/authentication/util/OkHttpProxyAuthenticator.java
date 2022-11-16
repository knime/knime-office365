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
 *   2022-09-04 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.util;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * {@link Authenticator} implementation to perform proxy authentication.
 * Credentials are fetched from the standard properties.
 *
 * @author Alexander Bondaletov
 */
public class OkHttpProxyAuthenticator implements Authenticator {

    private static final String PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization";
    private static final String PROXY_USER = ".proxyUser";
    private static final String PROXY_PASSWORD = ".proxyPassword";

    @Override
    public Request authenticate(final Route route, final Response response) throws IOException {
        if (response.request().header(PROXY_AUTHORIZATION_HEADER) != null) {
            return null; // Give up, we've already failed to authenticate.
        }

        String scheme = route.address().url().scheme();
        String credentials = getCredentials(scheme);

        if (credentials == null) {
            return null;
        }

        return response.request().newBuilder().header(PROXY_AUTHORIZATION_HEADER, credentials).build();
    }

    private static String getCredentials(final String protocol) {
        String user = System.getProperty(protocol.toLowerCase(Locale.ENGLISH) + PROXY_USER);
        String password = System.getProperty(protocol.toLowerCase(Locale.ENGLISH) + PROXY_PASSWORD);

        if (user == null || password == null) {
            return null;
        } else {
            return Credentials.basic(user, password);
        }
    }
}
