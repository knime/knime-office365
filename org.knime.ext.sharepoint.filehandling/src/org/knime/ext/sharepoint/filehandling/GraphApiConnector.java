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
 *   2020-05-02 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.UserNamePasswordParameters;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.IHttpRequest;

/**
 * Temporary class to provide Graph API authentication for filehandling node.
 * Should be replace with proper implementation later.
 *
 * @author Alexander Bondaletov
 */
public class GraphApiConnector {
    private static final String APP_ID = "e915aace-9024-416c-b797-14601fc3b94c";
    private static final String AUTHORITY_PREFIX = "https://login.microsoftonline.com/";

    /**
     *
     */
    public static final String SHAREPOINT_USERNAME = "sharepoint.username";
    /**
     *
     */
    public static final String SHAREPOINT_PASSWORD = "sharepoint.password";
    /**
     *
     */
    public static final String SHAREPOINT_TENANT_ID = "sharepoint.tenant";

    /**
     * @param pool
     * @return The authentication provider.
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws MalformedURLException
     */
    public static IAuthenticationProvider connect(final ExecutorService pool)
            throws InterruptedException, ExecutionException, MalformedURLException {
        String authority = AUTHORITY_PREFIX + System.getProperty(SHAREPOINT_TENANT_ID);
        Set<String> scopeSet = new HashSet<>(
                Arrays.asList("Files.ReadWrite.All", "Sites.ReadWrite.All", "Directory.Read.All"));

        PublicClientApplication app = PublicClientApplication.builder(APP_ID).authority(authority).executorService(pool)
                .build();

        IAuthenticationResult result = app.acquireToken(UserNamePasswordParameters.builder(scopeSet,
                System.getProperty(SHAREPOINT_USERNAME), System.getProperty(SHAREPOINT_PASSWORD).toCharArray()).build())
                .get();

        String token = result.accessToken();

        return new SimpleAuthProvider(token);
    }

    private static class SimpleAuthProvider implements IAuthenticationProvider {

        private String m_accessToken = null;

        public SimpleAuthProvider(final String accessToken) {
            this.m_accessToken = accessToken;
        }

        @Override
        public void authenticateRequest(final IHttpRequest request) {
            request.addHeader("Authorization", "Bearer " + m_accessToken);
        }
    }
}
