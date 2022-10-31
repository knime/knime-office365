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
package org.knime.ext.microsoft.authentication.port.oauth2;

import java.awt.Component;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.providers.oauth2.MSALUtil;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.ApplicationPermissionsTokenSupplier;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.DelegatedPermissionsTokenSupplier;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.MemoryCacheAccessTokenSupplier;

/**
 * Subclass of {@link MicrosoftCredential} that provides access to an OAuth2
 * access token.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public class OAuth2Credential extends MicrosoftCredential {

    private static final String KEY_TOKEN = "token";
    private static final String KEY_ENDPOINT = "authority"; // key is called "authority for backwards compatibility
                                                            // reasons
    private static final String KEY_USERNAME = "username";
    private static final String KEY_SCOPES = "scopes";
    private static final String KEY_APP_ID = "appId";
    private static final String KEY_SCOPE_TYPE = "scope_type";

    private final MemoryCacheAccessTokenSupplier m_tokenSupplier;
    private final String m_username;
    private final Set<String> m_scopes;
    private final String m_endpoint;
    private final String m_appId;
    private final String m_scopeTypeValue;

    /**
     * Creates new instance
     *
     * @param tokenSupplier
     *            The access token supplier.
     * @param username
     * @param scopes
     *            The scopes
     * @param endpoint
     *            The OAuth2 authorization endpoint
     * @param appId
     *            The Application (client) ID
     * @param scopeType
     *            The scope type {@link ScopeType}
     */
    public OAuth2Credential(
            final MemoryCacheAccessTokenSupplier tokenSupplier, //
            final String username, //
            final Set<String> scopes, //
            final String endpoint, //
            final String appId, //
            final ScopeType scopeType) {

        super(Type.OAUTH2_ACCESS_TOKEN);
        m_tokenSupplier = tokenSupplier;
        m_username = username;
        m_scopes = scopes;
        m_endpoint = endpoint;
        m_appId = appId;
        m_scopeTypeValue = scopeType.getSettingsValue();
    }

    /**
     * Fetches access token and updates token expire time.
     *
     * @return the access token
     * @throws IOException
     */
    public OAuth2AccessToken getAccessToken() throws IOException {
        return m_tokenSupplier.getAuthenticationResult(m_scopes);
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return m_username;
    }

    /**
     * @return the scopes
     */
    public Set<String> getScopes() {
        return m_scopes;
    }

    /**
     * @return the OAuth2 authorization endpoint URL
     */
    public String getEndpoint() {
        return m_endpoint;
    }

    /**
     * @return the appId
     */
    protected String getAppId() {
        return m_appId;
    }

    /**
     * @return the scope type
     */
    public ScopeType getScopeType() {
        return ScopeType.forSettingsValue(m_scopeTypeValue);
    }

    /**
     * @return the OAuth2 authorization endpoint URL
     * @deprecated since 4.5.2, use {@link #getEndpoint()} instead
     */
    @Deprecated(since = "4.5.2")
    public String getAuthority() {
        return getEndpoint();
    }

    @Override
    public void saveSettings(final ConfigWO config) {
        super.saveSettings(config);
        config.addString(KEY_USERNAME, m_username);
        config.addString(KEY_ENDPOINT, m_endpoint);
        config.addString(KEY_APP_ID, m_appId);
        config.addString(KEY_SCOPE_TYPE, m_scopeTypeValue);

        final String[] scopes = m_scopes.toArray(new String[] {});
        config.addStringArray(KEY_SCOPES, scopes);
        m_tokenSupplier.saveSettings(config.addConfig(KEY_TOKEN));
    }

    /**
     * Creates a {@link MicrosoftCredential} instance from the give config object.
     *
     * @param config
     *            The {@link ConfigRO} to load from.
     * @return a new {@link MicrosoftCredential} instance loaded from the give
     *         config object.
     * @throws InvalidSettingsException
     */
    public static MicrosoftCredential loadFromSettings(final ConfigRO config) throws InvalidSettingsException {
        final var username = config.getString(KEY_USERNAME);
        final var endpoint = config.getString(KEY_ENDPOINT);
        var appId = MSALUtil.DEFAULT_APP_ID;

        if (config.containsKey(KEY_APP_ID)) {
            appId = config.getString(KEY_APP_ID);
        }

        var scopeType = ScopeType.DELEGATED;
        if (config.containsKey(KEY_SCOPE_TYPE)) {
            scopeType = ScopeType.forSettingsValue(config.getString(KEY_SCOPE_TYPE));
        }

        final var scopes = new HashSet<>(Arrays.asList(config.getStringArray(KEY_SCOPES)));

        final var tokenSupplier = scopeType == ScopeType.DELEGATED
                ? new DelegatedPermissionsTokenSupplier(endpoint, appId)
                : new ApplicationPermissionsTokenSupplier(endpoint, appId);

        tokenSupplier.loadSettings(config.getConfig(KEY_TOKEN));
        return new OAuth2Credential(tokenSupplier, username, scopes, endpoint, appId, scopeType);
    }

    @Override
    public JComponent getView() {
        final var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        panel.add(Box.createHorizontalStrut(20));

        final var labelBox = new Box(BoxLayout.Y_AXIS);
        labelBox.add(createLabel("Username: "));
        labelBox.add(createLabel("OAuth2 Authorization Endpoint: "));
        labelBox.add(createLabel("Application (client) ID: "));
        labelBox.add(createLabel("Scopes: "));
        labelBox.add(Box.createVerticalGlue());
        panel.add(labelBox);

        panel.add(Box.createHorizontalStrut(5));

        final var valueBox = new Box(BoxLayout.Y_AXIS);
        valueBox.add(createLabel(m_username));
        valueBox.add(createLabel(m_endpoint));
        valueBox.add(createLabel(m_appId));
        valueBox.add(createLabel(String.join(", ", m_scopes.toArray(new String[] {}))));
        valueBox.add(Box.createVerticalGlue());
        panel.add(valueBox);

        panel.add(Box.createHorizontalGlue());

        panel.setName("OAuth2 Access Token");
        return panel;
    }

    private static JLabel createLabel(final String text) {
        final var label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * @return A short human readable summary
     */
    @Override
    public String getSummary() {
        return String.format("%s", m_username);
    }
}
