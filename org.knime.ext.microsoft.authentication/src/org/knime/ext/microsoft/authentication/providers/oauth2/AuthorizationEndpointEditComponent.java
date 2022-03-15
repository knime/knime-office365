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
 *   2022-01-25 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationSettings;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProvider;

/**
 * Editor component for editing the OAuth 2 authorization endpoint URL for
 * {@link OAuth2Provider} providers.
 *
 * @author Alexander Bondaletov
 */
public final class AuthorizationEndpointEditComponent extends JPanel {
    private static final long serialVersionUID = 1L;

    private final MicrosoftAuthenticationSettings m_settings;// NOSONAR not intended for serialization

    private final DocumentListener m_endpointUrlChangedListener = new DocumentListener() { // NOSONAR not intended for
                                                                                           // serialization

        @Override
        public void removeUpdate(final DocumentEvent e) {
            setEndpointUrl();
        }

        @Override
        public void insertUpdate(final DocumentEvent e) {
            setEndpointUrl();
        }

        @Override
        public void changedUpdate(final DocumentEvent e) {
            setEndpointUrl();
        }

        private void setEndpointUrl() {
            if (m_provider != null) {
                m_provider.getCustomEndpointUrlModel().setStringValue(m_endpointUrl.getText());
            }
        }
    };

    private OAuth2Provider m_provider; // NOSONAR not intended for serialization

    private JRadioButton m_useDefaultEndpoint;
    private JRadioButton m_useCustomEndpoint;
    private JTextField m_endpointUrl;


    /**
     * Creates new instance
     *
     * @param settings
     *            The node settings.
     */
    public AuthorizationEndpointEditComponent(final MicrosoftAuthenticationSettings settings) {
        m_settings = settings;
        settings.getProviderTypeModel().addChangeListener(e -> onProviderChanged());

        m_useDefaultEndpoint = new JRadioButton("Default");
        m_useCustomEndpoint = new JRadioButton("Custom:");
        m_endpointUrl = new JTextField(60);

        var group = new ButtonGroup();
        group.add(m_useDefaultEndpoint);
        group.add(m_useCustomEndpoint);

        m_useDefaultEndpoint.addActionListener(e -> setUseCustomEndpoint(false));
        m_useCustomEndpoint.addActionListener(e -> setUseCustomEndpoint(true));

        setLayout(new GridBagLayout());
        var c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        c.insets = new Insets(10, 10, 0, 10);
        c.gridx = 0;
        c.gridy = 0;

        add(m_useDefaultEndpoint, c);

        c.gridy += 1;
        add(m_useCustomEndpoint, c);

        c.insets = new Insets(5, 30, 10, 10);
        c.gridy += 1;
        add(m_endpointUrl, c);

        setBorder(BorderFactory.createTitledBorder("OAuth 2.0 authorization endpoint"));

        onProviderChanged();
    }

    private void setUseCustomEndpoint(final boolean useCustom) {
        if (m_provider != null) {
            m_provider.getUseCustomEndpointModel().setBooleanValue(useCustom);
            m_endpointUrl.setEnabled(useCustom);
        }
    }

    private void onProviderChanged() {
        MicrosoftAuthProvider current = m_settings.getCurrentProvider();

        if (current instanceof OAuth2Provider) {
            m_provider = (OAuth2Provider) current;
        } else {
            m_provider = null;
        }

        updateComponent();
    }

    private void updateComponent() {
        if (m_provider == null) {
            m_useDefaultEndpoint.setSelected(true);
            m_endpointUrl.setText("");
        } else {
            var useCustom = m_provider.getUseCustomEndpointModel().getBooleanValue();
            m_useDefaultEndpoint.setSelected(!useCustom);
            m_useCustomEndpoint.setSelected(useCustom);
            m_endpointUrl.setText(m_provider.getCustomEndpointUrlModel().getStringValue());
        }

        setEnabled(m_provider != null);
    }

    /**
     * To be invoked just before the dialog or this component is being shown.
     */
    public void onShown() {
        onProviderChanged();
        m_endpointUrl.getDocument().addDocumentListener(m_endpointUrlChangedListener);
    }

    /**
     * To be invoked just before the dialog is being closed.
     */
    public void onClosed() {
        m_endpointUrl.getDocument().removeDocumentListener(m_endpointUrlChangedListener);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);

        m_useDefaultEndpoint.setEnabled(enabled);
        m_useCustomEndpoint.setEnabled(enabled);
        m_endpointUrl.setEnabled(enabled && m_useCustomEndpoint.isSelected());

    }
}
