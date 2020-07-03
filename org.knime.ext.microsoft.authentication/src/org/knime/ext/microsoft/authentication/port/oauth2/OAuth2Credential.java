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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.AccessTokenSupplierFactory;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.BaseAccessTokenSupplier;

/**
 * Subclass of {@link MicrosoftCredential} that provides access to an OAuth2
 * access token.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public class OAuth2Credential extends MicrosoftCredential {

    private static final DateTimeFormatter ZONED_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder() //
            .append(DateTimeFormatter.ISO_LOCAL_DATE) //
            .appendLiteral(' ') //
            .append(DateTimeFormatter.ISO_LOCAL_TIME) //
            .appendLiteral(' ') //
            .appendZoneRegionId() //
            .toFormatter();

    private static final String KEY_TOKEN = "token";
    private static final String KEY_AUTHORITY = "authority";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TOKEN_EXPIRY = "tokenExpiry";
    private static final String KEY_SCOPES = "scopes";

    private BaseAccessTokenSupplier m_tokenSupplier;
    private String m_username;
    private Instant m_accessTokenExpiresAt;
    private EnumSet<Scope> m_scopes;
    private String m_authority;

    /**
     * Creates new instance
     *
     * @param tokenSupplier
     *            The access token supplier.
     * @param accessTokenExpiresAt
     * @param username
     * @param scopes
     *            The scopes
     * @param authority
     *            The authority
     */
    public OAuth2Credential(
            final BaseAccessTokenSupplier tokenSupplier, //
            final String username, //
            final Instant accessTokenExpiresAt, //
            final EnumSet<Scope> scopes, //
            final String authority) {

        super(Type.OAUTH2_ACCESS_TOKEN);
        m_tokenSupplier = tokenSupplier;
        m_username = username;
        m_accessTokenExpiresAt = accessTokenExpiresAt;
        m_scopes = scopes;
        m_authority = authority;
    }

    /**
     * @return the access token
     * @throws IOException
     */
    public String getAccessToken() throws IOException {
        return m_tokenSupplier.getAccessToken(m_scopes);
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return m_username;
    }

    /**
     * @return the instant after which the access token expires
     */
    public Instant getAccessTokenExpiresAt() {
        return m_accessTokenExpiresAt;
    }

    /**
     * @return the scopes
     */
    public EnumSet<Scope> getScopes() {
        return m_scopes;
    }

    /**
     * @return the authority
     */
    public String getAuthority() {
        return m_authority;
    }

    @Override
    public void saveSettings(final ConfigWO config) {
        super.saveSettings(config);
        config.addString(KEY_USERNAME, m_username);
        config.addLong(KEY_TOKEN_EXPIRY, m_accessTokenExpiresAt.toEpochMilli());
        config.addString(KEY_AUTHORITY, m_authority);

        final String[] scopes = m_scopes.stream().map(Objects::toString).toArray(String[]::new);
        config.addStringArray(KEY_SCOPES, scopes);
        m_tokenSupplier.saveSettings(config.addConfig(KEY_TOKEN));
    }

    public static MicrosoftCredential loadFromSettings(final ConfigRO config) throws InvalidSettingsException {
        final String username = config.getString(KEY_USERNAME);
        final Instant tokenExpiry = Instant.ofEpochMilli(config.getLong(KEY_TOKEN_EXPIRY));
        final String authority = config.getString(KEY_AUTHORITY);

        final List<Scope> scopeList = Arrays.stream(config.getStringArray(KEY_SCOPES)) //
                .<Scope>map(Scope::valueOf) //
                .collect(Collectors.toList());
        final EnumSet<Scope> scopes = EnumSet.copyOf(scopeList);

        final BaseAccessTokenSupplier tokenSupplier = AccessTokenSupplierFactory
                .createFromSettings(authority, config.getConfig(KEY_TOKEN));

        return new OAuth2Credential(tokenSupplier, username, tokenExpiry, scopes, authority);
    }

    @Override
    public JComponent getView() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        panel.add(Box.createHorizontalStrut(20));

        final Box labelBox = new Box(BoxLayout.Y_AXIS);
        labelBox.add(createLabel("Username: "));
        labelBox.add(createLabel("Authority/Endpoint: "));
        labelBox.add(createLabel("Scopes: "));
        labelBox.add(Box.createVerticalGlue());
        panel.add(labelBox);

        panel.add(Box.createHorizontalStrut(5));

        final Box valueBox = new Box(BoxLayout.Y_AXIS);
        valueBox.add(createLabel(m_username));
        valueBox.add(createLabel(m_authority));
        valueBox.add(createLabel(String.join(", ", m_scopes.stream().map((s) -> s.getScope()).toArray(String[]::new))));
        valueBox.add(Box.createVerticalGlue());
        panel.add(valueBox);

        panel.add(Box.createHorizontalGlue());

        panel.setName("OAuth2 Access Token");
        return panel;
    }

    private static JLabel createLabel(final String text) {
        final JLabel label = new JLabel(text);
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

    /**
     * @param instant
     * @return
     */
    private static String formatInstant(final Instant instant) {
        final ZonedDateTime zoned = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        return zoned.format(ZONED_DATE_TIME_FORMATTER);
    }

}
