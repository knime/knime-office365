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
package org.knime.ext.microsoft.authentication.providers.oauth2;

import java.io.IOException;
import java.net.MalformedURLException;

import com.microsoft.aad.msal4j.MsalClientException;
import com.microsoft.aad.msal4j.PublicClientApplication;

/**
 * Utility class the help with MSAL4J.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public class MSALUtil {

    private static final String APP_ID = "cf47ff49-7da6-4603-b339-f4475176432b";

    public static final String COMMON_AUTHORITY = "https://login.microsoftonline.com/common";

    public static final String ORGANIZATIONS_AUTHORITY = "https://login.microsoftonline.com/organizations";

    /**
     * Creates the {@link PublicClientApplication} instance.
     *
     * @return The client application.
     * @throws MalformedURLException
     */
    public static PublicClientApplication createClientApp(final String authority) {
        try {
            return PublicClientApplication.builder(APP_ID).authority(authority).build();
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public static PublicClientApplication createClientAppWithToken(final String authority, final String tokenCache)
            throws IOException {
        try {
            final PublicClientApplication app = MSALUtil.createClientApp(authority);
            app.tokenCache().deserialize(tokenCache);
            return app;
        } catch (MsalClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

}
