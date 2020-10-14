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
 *   2020-08-09 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.port.azure.storage;

import java.awt.Component;
import java.io.IOException;
import java.util.Optional;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.providers.MemoryCredentialCache;

/**
 * Subclass of {@link MicrosoftCredential} that provides access to Azure Blob
 * Storage using shared key.
 *
 * @author Alexander Bondaletov
 */
public class AzureSharedKeyCredential extends MicrosoftCredential {

    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_CACHE_KEY = "cacheKey";
    private static final String ENDPOINT_FORMAT = "https://%s.blob.core.windows.net";

    private String m_account;
    private String m_cacheKey;

    /**
     * @param account
     *            The storage account.
     * @param cacheKey
     *            The memory cache key under which the secret key is stored.
     */
    public AzureSharedKeyCredential(final String account, final String cacheKey) {
        super(Type.AZURE_SHARED_KEY);
        m_account = account;
        m_cacheKey = cacheKey;
    }

    /**
     * @return the account
     */
    public String getAccount() {
        return m_account;
    }

    /**
     * @return the secret key
     * @throws IOException
     */
    public String getSecretKey() throws IOException {
        return Optional.ofNullable(MemoryCredentialCache.get(m_cacheKey)) //
                .orElseThrow(() -> new IOException(
                        "Storage account key not available anymore. Please re-execute the Microsoft Authentication node."));
    }

    /**
     * @return the endpoint URL
     */
    public String getEndpoint() {
        return String.format(ENDPOINT_FORMAT, m_account);
    }

    @Override
    public void saveSettings(final ConfigWO config) {
        super.saveSettings(config);

        config.addString(KEY_ACCOUNT, m_account);
        config.addString(KEY_CACHE_KEY, m_cacheKey);
    }

    /**
     * Creates new {@link MicrosoftCredential} object restoring it's state from the
     * provided {@link ConfigRO}.
     *
     * @param config
     *            The config.
     * @return The credentials.
     * @throws InvalidSettingsException
     */
    public static MicrosoftCredential loadFromSettings(final ConfigRO config) throws InvalidSettingsException {
        String account = config.getString(KEY_ACCOUNT);
        String cacheKey = config.getString(KEY_CACHE_KEY);
        return new AzureSharedKeyCredential(account, cacheKey);
    }

    @Override
    public JComponent getView() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        panel.add(Box.createHorizontalStrut(20));

        Box labelBox = new Box(BoxLayout.Y_AXIS);
        labelBox.add(createLabel("Account:"));
        labelBox.add(createLabel("Endpoint:"));
        labelBox.add(Box.createVerticalGlue());
        panel.add(labelBox);

        panel.add(Box.createHorizontalStrut(5));

        Box valueBox = new Box(BoxLayout.Y_AXIS);
        valueBox.add(createLabel(m_account));
        valueBox.add(createLabel(getEndpoint()));
        valueBox.add(Box.createVerticalGlue());
        panel.add(valueBox);

        panel.add(Box.createHorizontalGlue());
        panel.setName("Shared key credentials");
        return panel;
    }

    private static JLabel createLabel(final String text) {
        final JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    @Override
    public String getSummary() {
        return String.format("Account: %s", m_account);
    }

}
