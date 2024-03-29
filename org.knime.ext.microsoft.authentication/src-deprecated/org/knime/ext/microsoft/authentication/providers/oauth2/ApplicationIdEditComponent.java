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
 *   2022-03-28 (Zkriya Rakhimberdiyev): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationSettings;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProvider;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.InteractiveAuthProvider;

/**
 * Editor component for editing the Application (client) ID and redirect URL.
 *
 * @author Zkriya Rakhimberdiyev
 */
public final class ApplicationIdEditComponent extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MicrosoftAuthenticationSettings m_settings;// NOSONAR not intended for serialization

    private final DocumentListener m_appIdChangedListener = new DocumentListener() { // NOSONAR not intended for
        // serialization
        @Override
        public void removeUpdate(final DocumentEvent e) {
            setAppId();
        }

        @Override
        public void insertUpdate(final DocumentEvent e) {
            setAppId();
        }

        @Override
        public void changedUpdate(final DocumentEvent e) {
            setAppId();
        }

        private void setAppId() {
            if (m_provider != null) {
                m_provider.getCustomAppIdModel().setStringValue(m_appId.getText());
            }
        }
    };

    private final DocumentListener m_redirectUrlChangedListener = new DocumentListener() { // NOSONAR not intended for
        // serialization
        @Override
        public void removeUpdate(final DocumentEvent e) {
            setRedirectUrl();
        }

        @Override
        public void insertUpdate(final DocumentEvent e) {
            setRedirectUrl();
        }

        @Override
        public void changedUpdate(final DocumentEvent e) {
            setRedirectUrl();
        }

        private void setRedirectUrl() {
            if (m_provider instanceof InteractiveAuthProvider) {
                ((InteractiveAuthProvider) m_provider).getRedirectUrlModel().setStringValue(m_redirectUrl.getText());
            }
        }
    };

    private final JRadioButton m_useDefaultAppId;
    private final JRadioButton m_useCustomAppId;
    private final JTextField m_appId;
    private final JLabel m_appIdLabel;
    private final JTextField m_redirectUrl;
    private final JLabel m_redirectUrlLabel;

    private DelegatedPermissionsOAuth2Provider m_provider; // NOSONAR not intended for serialization

    /**
     * Creates new instance
     *
     * @param settings
     *            The node settings.
     */
    public ApplicationIdEditComponent(final MicrosoftAuthenticationSettings settings) {
        m_settings = settings;
        settings.getProviderTypeModel().addChangeListener(e -> onProviderChanged());

        m_useDefaultAppId = new JRadioButton("Default");
        m_useCustomAppId = new JRadioButton("Custom:");
        m_appId = new JTextField(52);
        m_redirectUrl = new JTextField(52);

        m_appIdLabel = new JLabel("Application ID:");
        m_redirectUrlLabel = new JLabel("Redirect URL:");

        var group = new ButtonGroup();
        group.add(m_useDefaultAppId);
        group.add(m_useCustomAppId);

        m_useDefaultAppId.addActionListener(e -> setUseCustomAppId(false));
        m_useCustomAppId.addActionListener(e -> setUseCustomAppId(true));

        setLayout(new GridBagLayout());
        var c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 1;

        c.insets = new Insets(10, 10, 0, 10);

        c.gridx = 0;
        c.gridy = 0;
        add(m_useDefaultAppId, c);

        c.gridy += 1;
        add(m_useCustomAppId, c);

        c.insets = new Insets(5, 30, 10, 10);
        c.gridy += 1;

        add(createCustomPanel(), c);

        setBorder(BorderFactory.createTitledBorder("Application ID"));
        onProviderChanged();
    }

    private JComponent createCustomPanel() {
        var customAppPanel = new JPanel();
        customAppPanel.setLayout(new GridBagLayout());

        var c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        customAppPanel.add(m_appIdLabel, c);

        c.gridx += 1;
        customAppPanel.add(m_appId, c);

        c.gridx = 0;
        c.gridy += 1;
        customAppPanel.add(m_redirectUrlLabel, c);

        c.gridx += 1;
        customAppPanel.add(m_redirectUrl, c);
        return customAppPanel;
    }

    private void setUseCustomAppId(final boolean useCustom) {
        if (m_provider != null) {
            m_provider.getUseCustomAppIdModel().setBooleanValue(useCustom);
            m_appId.setEnabled(useCustom);
            m_appIdLabel.setEnabled(useCustom);

            final var redirectEnabled = useCustom && m_provider instanceof InteractiveAuthProvider;
            m_redirectUrl.setEnabled(redirectEnabled);
            m_redirectUrlLabel.setEnabled(redirectEnabled);
        }
    }

    private void onProviderChanged() {
        MicrosoftAuthProvider current = m_settings.getCurrentProvider();

        if (current instanceof DelegatedPermissionsOAuth2Provider) {
            m_provider = (DelegatedPermissionsOAuth2Provider) current;
        } else {
            m_provider = null;
        }

        updateComponent();
    }

    private void updateComponent() {
        if (m_provider == null) {
            m_useDefaultAppId.setSelected(true);
            m_appId.setText("");
            m_redirectUrl.setText("");
        } else {
            var useCustom = m_provider.getUseCustomAppIdModel().getBooleanValue();
            m_useDefaultAppId.setSelected(!useCustom);
            m_useCustomAppId.setSelected(useCustom);
            m_appId.setText(m_provider.getCustomAppIdModel().getStringValue());

            if (m_provider instanceof InteractiveAuthProvider) {
                m_redirectUrl.setText(((InteractiveAuthProvider) m_provider).getRedirectUrlModel().getStringValue());
            }
        }

        setEnabled(m_provider != null);
    }

    /**
     * To be invoked just before the dialog or this component is being shown.
     */
    public void onShown() {
        onProviderChanged();
        m_appId.getDocument().addDocumentListener(m_appIdChangedListener);
        m_redirectUrl.getDocument().addDocumentListener(m_redirectUrlChangedListener);
    }

    /**
     * To be invoked just before the dialog is being closed.
     */
    public void onClosed() {
        m_appId.getDocument().removeDocumentListener(m_appIdChangedListener);
        m_redirectUrl.getDocument().removeDocumentListener(m_redirectUrlChangedListener);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        m_useDefaultAppId.setEnabled(enabled);
        m_useCustomAppId.setEnabled(enabled);
        m_appIdLabel.setEnabled(enabled && m_useCustomAppId.isSelected());
        m_appId.setEnabled(enabled && m_useCustomAppId.isSelected());

        final var redirectEnabled = enabled && m_provider instanceof InteractiveAuthProvider
                && m_useCustomAppId.isSelected();
        m_redirectUrlLabel
                .setEnabled(redirectEnabled);
        m_redirectUrl
                .setEnabled(redirectEnabled);
    }
}
