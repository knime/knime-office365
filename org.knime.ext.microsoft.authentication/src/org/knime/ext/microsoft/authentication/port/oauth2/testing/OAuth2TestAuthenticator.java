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
 *   2020-07-06 (bjoern): created
 */
package org.knime.ext.microsoft.authentication.port.oauth2.testing;

import java.io.IOException;
import java.util.UUID;

import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.microsoft.authentication.providers.oauth2.userpass.UsernamePasswordAuthProvider;

/**
 * This class is part of the testing infrastructure and allows to easily
 * authenticate in order to get an {@link OAuth2Credential}.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public final class OAuth2TestAuthenticator {

    private OAuth2TestAuthenticator() {
    }

    /**
     * Logs into the Microsoft Identity platform using the given user/password pair
     * and returns a {@link OAuth2Credential} which contains an access token.
     *
     * @param username
     *            The username to use for login.
     * @param password
     *            The password to use for login.
     * @return a {@link OAuth2Credential} which contains an access token
     * @throws IOException
     *             when something went wrong during login.
     */
    public static OAuth2Credential authenticateWithUsernamePassword(final String username, final String password)
            throws IOException {

        final var provider = new UsernamePasswordAuthProvider(UUID.randomUUID().toString());
        provider.getUsernameModel().setStringValue(username);
        provider.getPasswordModel().setStringValue(password);
        return provider.getCredential(null);
    }
}