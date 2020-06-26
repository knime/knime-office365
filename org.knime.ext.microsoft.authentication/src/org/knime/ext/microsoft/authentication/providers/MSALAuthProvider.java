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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;
import org.knime.ext.microsoft.authentication.SilentRefreshAuthenticationProvider;
import org.knime.ext.microsoft.authentication.port.MicrosoftConnection;
import org.knime.ext.microsoft.authentication.port.MicrosoftScopes;

import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Base class for auth providers implementing different authentication methods
 * supported by MSAL.
 *
 * @author Alexander Bondaletov
 */
public abstract class MSALAuthProvider implements MicrosoftAuthProvider {
    private static final String APP_ID = "cf47ff49-7da6-4603-b339-f4475176432b";
    private static final String DEFAULT_AUTHORITY = "https://login.microsoftonline.com/common";

    private static final String KEY_SCOPES = "scopes";

    private final SettingsModelStringArray m_scopes;

    /**
     * Creates new instance.
     *
     */
    public MSALAuthProvider() {
        m_scopes = new SettingsModelStringArray(KEY_SCOPES,
                new String[] { MicrosoftScopes.SITES_READ_WRITE.getScope() });
    }

    /**
     * @return the scopes model
     */
    public SettingsModelStringArray getScopesModel() {
        return m_scopes;
    }

    /**
     * @return the scopes
     */
    public Set<String> getScopes() {
        return new HashSet<>(Arrays.asList(m_scopes.getStringArrayValue()));
    }

    /**
     * Creates {@link IAuthenticationProvider} instance used to authenticate graph
     * API client.
     *
     * @param connection
     *            The Microsoft connection object.
     *
     * @return The auth provider.
     * @throws MalformedURLException
     */
    public IAuthenticationProvider createGraphAuthProvider(final MicrosoftConnection connection)
            throws MalformedURLException {
        PublicClientApplication app = createClientApp();
        app.tokenCache().deserialize(connection.getTokenCache());
        return new SilentRefreshAuthenticationProvider(connection.getScopes(), app);
    }

    /**
     * Creates the {@link PublicClientApplication} instance.
     *
     * @return The client application.
     * @throws MalformedURLException
     */
    protected PublicClientApplication createClientApp() throws MalformedURLException {
        return PublicClientApplication.builder(APP_ID).authority(getAuthority()).build();
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
     * {@inheritDoc}
     */
    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_scopes.saveSettingsTo(settings);
    }

    /**
     * Validates consistency of the current settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        String[] scopes = m_scopes.getStringArrayValue();
        if (scopes == null || scopes.length == 0) {
            throw new InvalidSettingsException("Scopes cannot be empty");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_scopes.loadSettingsFrom(settings);
    }
}
