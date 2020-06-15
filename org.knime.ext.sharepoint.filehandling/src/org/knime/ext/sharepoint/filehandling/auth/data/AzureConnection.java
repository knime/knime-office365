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
package org.knime.ext.sharepoint.filehandling.auth.data;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;
import org.knime.ext.sharepoint.filehandling.auth.SilentRefreshAuthenticationProvider;
import org.knime.ext.sharepoint.filehandling.auth.providers.AuthProviderType;

import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Class for holding Azure connection information required to restore
 * authenticated session
 *
 * @author Alexander Bondaletov
 */
public class AzureConnection {
    private static final String APP_ID = "e915aace-9024-416c-b797-14601fc3b94c";

    private static final String KEY_PROVIDER_TYPE = "providerType";
    private static final String KEY_AUTHORITY = "authority";
    private static final String KEY_TOKEN_CACHE = "tokenCache";
    private static final String KEY_SCOPES = "scopes";

    private final SettingsModelString m_providerType;
    private final SettingsModelString m_tokenCache;
    private final ConfigurableLocationStorage m_storageLocation;
    private final SettingsModelStringArray m_scopes;

    private String m_authority;

    /**
     * Creates new instance
     */
    public AzureConnection() {
        m_providerType = new SettingsModelString(KEY_PROVIDER_TYPE, "");
        m_tokenCache = new SettingsModelString(KEY_TOKEN_CACHE, null);
        m_storageLocation = new ConfigurableLocationStorage(m_tokenCache);
        m_scopes = new SettingsModelStringArray(KEY_SCOPES, new String[] { AzureScopes.FILES_READ_WRITE.getScope(),
                AzureScopes.SITES_READ_WRITE.getScope(), AzureScopes.DIRECTORY_READ.getScope() });

        m_providerType.addChangeListener(e -> logout());
        m_scopes.addChangeListener(e -> logout());
    }

    /**
     * Creates new instance from a given config.
     *
     * @param config
     *            The config.
     * @throws InvalidSettingsException
     *
     */
    public AzureConnection(final ConfigRO config) throws InvalidSettingsException {
        this();
        loadSettings(config);
    }

    /**
     * @return the providerType model
     */
    public SettingsModelString getProviderTypeModel() {
        return m_providerType;
    }

    /**
     * @return the providerType
     */
    public AuthProviderType getProviderType() {
        try {
            return AuthProviderType.valueOf(m_providerType.getStringValue());
        } catch (IllegalArgumentException e) {
            return AuthProviderType.INTERACTIVE;
        }
    }

    /**
     * @param authority
     *            the authority to set
     */
    public void setAuthority(final String authority) {
        m_authority = authority;
    }

    /**
     * @param tokenCache
     *            the tokenCache to set
     */
    public void setTokenCache(final String tokenCache) {
        m_tokenCache.setStringValue(tokenCache);
    }

    /**
     * @return the tokenCache model
     */
    public SettingsModelString getTokenCacheModel() {
        return m_tokenCache;
    }

    /**
     * @return the storageLocation
     */
    public ConfigurableLocationStorage getStorageLocation() {
        return m_storageLocation;
    }

    /**
     * @return the scopes
     */
    public Set<String> getScopes() {
        return new HashSet<>(Arrays.asList(m_scopes.getStringArrayValue()));
    }

    /**
     * @return the scopes model
     */
    public SettingsModelStringArray getScopesModel() {
        return m_scopes;
    }

    /**
     * @return The logged in state of the connection
     */
    public boolean isLoggedIn() {
        return m_tokenCache.getStringValue() != null && !m_tokenCache.getStringValue().isEmpty();
    }

    /**
     * Performs logout by clearing the token cache.
     */
    public void logout() {
        m_tokenCache.setStringValue(null);
    }

    /**
     * Creates the {@link PublicClientApplication} instance from a current config.
     *
     * @param withCache
     *            If set to <code>true</code> token cache would be restored.
     * @return The client application.
     * @throws MalformedURLException
     */
    public PublicClientApplication createClientApp(final boolean withCache) throws MalformedURLException {
        PublicClientApplication app = PublicClientApplication.builder(APP_ID).authority(m_authority).build();
        if (withCache) {
            app.tokenCache().deserialize(m_tokenCache.getStringValue());
        }
        return app;
    }

    /**
     * Creates {@link IAuthenticationProvider} instance used to authenticate graph
     * API client.
     *
     * @return The auth provider.
     * @throws MalformedURLException
     */
    public IAuthenticationProvider createGraphAuthProvider() throws MalformedURLException {
        return new SilentRefreshAuthenticationProvider(getScopes(), createClientApp(true));
    }

    /**
     * Saves the settings from the current instance to the given {@link ConfigWO}
     *
     * @param config
     *            The config.
     */
    public void saveSettings(final ConfigWO config) {
        config.addString(m_providerType.getKey(), m_providerType.getStringValue());
        config.addString(KEY_AUTHORITY, m_authority);
        config.addStringArray(KEY_SCOPES, m_scopes.getStringArrayValue());

        m_storageLocation.saveSettings(config);
    }

    /**
     * Loads settings from the give {@link ConfigRO}.
     *
     * @param config
     *            The config
     * @throws InvalidSettingsException
     */
    public void loadSettings(final ConfigRO config) throws InvalidSettingsException {
        m_providerType.setStringValue(config.getString(KEY_PROVIDER_TYPE));
        m_authority = config.getString(KEY_AUTHORITY);
        m_scopes.setStringArrayValue(config.getStringArray(KEY_SCOPES));

        m_storageLocation.loadSettings(config);
    }

    /**
     * Validates consistency of the current settigs.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        String[] scopes = m_scopes.getStringArrayValue();
        if (scopes == null || scopes.length == 0) {
            throw new InvalidSettingsException("Scopes cannot be empty");
        }

        m_storageLocation.validate();
    }

    /**
     * @return The view component.
     */
    public JComponent getView() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("Provider: ").append(m_providerType.getStringValue()).append("<br>");
        sb.append("Authority: ").append(m_authority).append("<br>");

        Set<String> scopes = getScopes();
        sb.append("Scopes:<br>");
        for (String s : scopes) {
            sb.append(s).append("<br>");
        }

        JTextArea textarea = new JTextArea(5, 50);
        textarea.setLineWrap(true);
        textarea.setEditable(false);
        if (m_tokenCache.getStringValue() != null) {
            textarea.setText(m_tokenCache.getStringValue());
        }
        textarea.setBorder(new TitledBorder("Token cache"));
        textarea.setAutoscrolls(true);

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0.5;
        c.weighty = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.add(new JLabel(sb.toString()), c);
        c.gridy += 1;
        c.weighty = 0.4;
        panel.add(textarea, c);
        panel.setName("Connection");
        return panel;
    }
}
