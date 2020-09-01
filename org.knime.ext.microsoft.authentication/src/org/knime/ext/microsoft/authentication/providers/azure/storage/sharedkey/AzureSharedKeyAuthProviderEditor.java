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
package org.knime.ext.microsoft.authentication.providers.azure.storage.sharedkey;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentFlowVariableNameSelection2;
import org.knime.core.node.defaultnodesettings.DialogComponentPasswordField;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.VariableType.CredentialsType;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProviderEditor;

/**
 * Editor component for the {@link AzureSharedKeyAuthProvider}.
 *
 * @author Alexander Bondaletov
 */
public class AzureSharedKeyAuthProviderEditor implements MicrosoftAuthProviderEditor {

    private final AzureSharedKeyAuthProvider m_provider;
    private final MicrosoftAuthenticationNodeDialog m_parent;

    private JComponent component;
    private DialogComponentFlowVariableNameSelection2 m_flowVarSelector;

    /**
     * Creates new instance.
     *
     * @param provider
     *            The auth provider.
     * @param parent
     *            The node dialog.
     *
     */
    public AzureSharedKeyAuthProviderEditor(final AzureSharedKeyAuthProvider provider,
            final MicrosoftAuthenticationNodeDialog parent) {
        m_provider = provider;
        m_parent = parent;

        initUI();
    }

    private void initUI() {
        JRadioButton rbEnterCreds = new JRadioButton("Account/Secret Key");
        rbEnterCreds.addActionListener(e -> m_provider.getUseCredentialsModel().setBooleanValue(false));

        JRadioButton rbUseFw = new JRadioButton("Credential flow variable");
        rbUseFw.addActionListener(e -> m_provider.getUseCredentialsModel().setBooleanValue(true));

        ButtonGroup group = new ButtonGroup();
        group.add(rbEnterCreds);
        group.add(rbUseFw);

        m_provider.getUseCredentialsModel().addChangeListener(e -> {
            boolean useCreds = m_provider.getUseCredentialsModel().getBooleanValue();
            rbEnterCreds.setSelected(!useCreds);
            rbUseFw.setSelected(useCreds);
        });
        rbEnterCreds.setSelected(true);

        m_flowVarSelector = new DialogComponentFlowVariableNameSelection2(m_provider.getCredentialsNameModel(), "",
                () -> m_parent.getAvailableFlowVariables(CredentialsType.INSTANCE));

        JPanel enterCredPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enterCredPanel.add(rbEnterCreds);

        JPanel flowVarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        flowVarPanel.add(rbUseFw);
        flowVarPanel.add(m_flowVarSelector.getComponentPanel());

        component = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.insets = new Insets(5, 10, 5, 10);
        component.add(rbEnterCreds, c);

        c.gridy += 1;
        component.add(createAccountSectetPanel(), c);

        c.gridy += 1;
        c.gridwidth = 1;
        component.add(rbUseFw, c);

        c.gridx = 1;
        component.add(m_flowVarSelector.getComponentPanel(), c);

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy += 1;
        c.gridwidth = 2;
        c.weightx = 1;
        c.weighty = 1;
        component.add(Box.createVerticalGlue(), c);
    }

    private JPanel createAccountSectetPanel() {
        DialogComponentString accountInput = new DialogComponentString(m_provider.getAccountModel(), "", false, 30);
        accountInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);
        DialogComponentPasswordField secretKeyInput = new DialogComponentPasswordField(m_provider.getSecretKeyModel(),
                "", 30);
        secretKeyInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Account:"), c);

        c.gridy = 1;
        panel.add(new JLabel("Secret key:"), c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 1;
        c.gridy = 0;
        panel.add(accountInput.getComponentPanel(), c);

        c.gridy = 1;
        panel.add(secretKeyInput.getComponentPanel(), c);

        panel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        return panel;
    }

    @Override
    public JComponent getComponent() {
        return component;
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_flowVarSelector.loadSettingsFrom(settings, specs);
    }

    @Override
    public void onShown() {
        // nothing to do
    }

    @Override
    public void onCancel() {
        // nothing to do
    }

}
