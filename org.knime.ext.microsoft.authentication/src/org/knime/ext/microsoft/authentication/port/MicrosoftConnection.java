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
package org.knime.ext.microsoft.authentication.port;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Date;
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
import org.knime.ext.microsoft.authentication.providers.AuthProviderType;

/**
 * Class for holding Microsoft connection information required to restore
 * authenticated session
 *
 * @author Alexander Bondaletov
 */
public class MicrosoftConnection {

    private static final DateTimeFormatter ZONED_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder() //
            .append(DateTimeFormatter.ISO_LOCAL_DATE) //
            .appendLiteral(' ') //
            .append(DateTimeFormatter.ISO_LOCAL_TIME) //
            .appendLiteral(
                    ' ') //
            .appendZoneRegionId() //
            .toFormatter();

    private static final String KEY_PROVIDER_TYPE = "providerType";
    private static final String KEY_AUTHORITY = "authority";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TOKEN_EXPIRY = "tokenExpiry";
    private static final String KEY_TOKEN_CACHE = "tokenCache";
    private static final String KEY_SCOPES = "scopes";

    private AuthProviderType m_providerType;
    private String m_tokenCache;
    private String m_username;
    private Date m_tokenExpiryDate;
    private Set<String> m_scopes;
    private String m_authority;

    /**
     * Creates new instance
     *
     * @param providerType
     *            The provider type.
     * @param tokenCache
     *            The token cache.
     * @param tokenExpiry
     * @param username
     * @param scopes
     *            The scopes
     * @param authority
     *            The authority
     */
    public MicrosoftConnection(final AuthProviderType providerType, final String tokenCache,
            final String username, final Date tokenExpiry, final Set<String> scopes,
            final String authority) {

        m_providerType = providerType;
        m_tokenCache = tokenCache;
        m_username = username;
        m_tokenExpiryDate = tokenExpiry;
        m_scopes = scopes;
        m_authority = authority;
    }

    /**
     * Creates new instance from a given config.
     *
     * @param config
     *            The config.
     * @throws InvalidSettingsException
     *
     */
    public MicrosoftConnection(final ConfigRO config) throws InvalidSettingsException {
        loadSettings(config);
    }

    /**
     * @return the providerType
     */
    public AuthProviderType getProviderType() {
        return m_providerType;
    }

    /**
     * @return the tokenCache
     */
    public String getTokenCache() {
        return m_tokenCache;
    }

    /**
     * @return the scopes
     */
    public Set<String> getScopes() {
        return m_scopes;
    }

    /**
     * @return the authority
     */
    public String getAuthority() {
        return m_authority;
    }

    /**
     * Saves the settings from the current instance to the given {@link ConfigWO}
     *
     * @param config
     *            The config.
     */
    public void saveSettings(final ConfigWO config) {
        config.addString(KEY_PROVIDER_TYPE, m_providerType.name());
        config.addString(KEY_TOKEN_CACHE, m_tokenCache);
        config.addString(KEY_USERNAME, m_username);
        config.addLong(KEY_TOKEN_EXPIRY, m_tokenExpiryDate.getTime());
        config.addString(KEY_AUTHORITY, m_authority);
        config.addStringArray(KEY_SCOPES, m_scopes.toArray(new String[] {}));
    }

    /**
     * Loads settings from the give {@link ConfigRO}.
     *
     * @param config
     *            The config
     * @throws InvalidSettingsException
     */
    public void loadSettings(final ConfigRO config) throws InvalidSettingsException {
        m_providerType = AuthProviderType
                .valueOf(config.getString(KEY_PROVIDER_TYPE, AuthProviderType.INTERACTIVE.name()));
        m_tokenCache = config.getString(KEY_TOKEN_CACHE);
        m_username = config.getString(KEY_USERNAME);
        m_tokenExpiryDate = new Date(config.getLong(KEY_TOKEN_EXPIRY));
        m_authority = config.getString(KEY_AUTHORITY);
        m_scopes = new HashSet<>(Arrays.asList(config.getStringArray(KEY_SCOPES)));
    }

    /**
     * @return The view component.
     */
    public JComponent getView() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("Username: ").append(m_username).append("<br>");
        sb.append("Token expires on: ").append(formatDate(m_tokenExpiryDate)).append("<br>");
        sb.append("Provider: ").append(m_providerType.name()).append("<br>");
        sb.append("Authority: ").append(m_authority).append("<br>");

        Set<String> scopes = getScopes();
        sb.append("Scopes:<br>");
        for (String s : scopes) {
            sb.append(s).append("<br>");
        }

        JTextArea textarea = new JTextArea(5, 50);
        textarea.setLineWrap(true);
        textarea.setEditable(false);
        if (m_tokenCache != null) {
            textarea.setText(m_tokenCache);
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

    /**
     * @return A short human readable summary
     */
    public String getSummary() {
        return String.format("logged in as %s / expires %s", m_username, formatDate(m_tokenExpiryDate));
    }

    /**
     * @param tokenExpiryDate
     * @return
     */
    private Object formatDate(final Date tokenExpiryDate) {
        final ZonedDateTime zoned = ZonedDateTime.ofInstant(m_tokenExpiryDate.toInstant(), ZoneId.systemDefault());
        return zoned.format(ZONED_DATE_TIME_FORMATTER);
    }
}
