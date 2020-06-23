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
 *   2020-06-04 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers;

import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.swing.JComponent;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.data.MicrosoftConnection;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;

/**
 * Base class for auth providers implementing different authentication methods.
 *
 * @author Alexander Bondaletov
 */
public abstract class AbstractAuthProvider {
    private static final String DEFAULT_AUTHORITY = "https://login.microsoftonline.com/common";

    /**
     * Microsoft connection object.
     */
    protected final MicrosoftConnection m_connection;

    /**
     * Creates new instance.
     *
     * @param connection
     *            The Microsoft connection object.
     *
     */
    public AbstractAuthProvider(final MicrosoftConnection connection) {
        m_connection = connection;
    }

    /**
     * @return the Microsoft connection object.
     */
    public MicrosoftConnection getConnection() {
        return m_connection;
    }

    /**
     * Performs authentication and stores authentication info into Microsoft
     * connection object.
     *
     * @throws MalformedURLException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public void login() throws MalformedURLException, InterruptedException, ExecutionException {
        m_connection.setAuthority(getAuthority());
        m_connection.setTokenCache(null);

        PublicClientApplication app = m_connection.createClientApp(false);
        acquireToken(app).get();
        m_connection.setTokenCache(app.tokenCache().serialize());
    }

    /**
     * Returns appropriate authority for a current provider.
     *
     * @return The authority.
     */
    protected String getAuthority() {
        return DEFAULT_AUTHORITY;
    }

    /**
     * Performs actual authentication and retrieves authentication token using
     * provider-specific authentication method;
     *
     * @param app
     *            The client app instance.
     * @return The future containing authentication result.
     */
    protected abstract CompletableFuture<IAuthenticationResult> acquireToken(PublicClientApplication app);

    /**
     * Creates editor component for the provider.
     *
     * @return The editor component.
     */
    public abstract JComponent createEditor();

    /**
     * Saves provider's settings into a given {@link ConfigWO}.
     *
     * @param settings
     *            The settings.
     */
    public void saveSettingsTo(final ConfigWO settings) {
        // default empty implementation
    }

    /**
     * Validates settings stored in a give {@link ConfigRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void validateSettings(final ConfigRO settings) throws InvalidSettingsException {
        // default empty implementation
    }

    /**
     * Validates consistency of the current settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        // default empty implementation
    }

    /**
     * Loads provider's settings from a given {@link ConfigRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final ConfigRO settings) throws InvalidSettingsException {
        // default empty implementation
    }
}
