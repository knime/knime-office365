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
 *   2020-06-06 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.userpass;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import org.knime.ext.microsoft.authentication.providers.oauth2.MSALAuthProviderEditor;
import org.knime.ext.microsoft.authentication.providers.oauth2.ScopesEditComponent;


/**
 * Editor component for {@link UsernamePasswordAuthProvider}.
 *
 * @author Alexander Bondaletov
 */
public class UsernamePasswordProviderEditor extends MSALAuthProviderEditor<UsernamePasswordAuthProvider> {

    private MicrosoftAuthenticationNodeDialog m_parent;

    private JRadioButton m_rbEnterCreds;
    private JRadioButton m_rbUseFw;
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
    public UsernamePasswordProviderEditor(final UsernamePasswordAuthProvider provider,
            final MicrosoftAuthenticationNodeDialog parent) {
        super(provider);

        m_parent = parent;
        m_flowVarSelector = new DialogComponentFlowVariableNameSelection2(m_provider.getCredentialsNameModel(),
                "",
                () -> m_parent.getAvailableFlowVariables(CredentialsType.INSTANCE));
    }

    @Override
    protected JComponent createContentPane() {
        m_rbEnterCreds = new JRadioButton("Username/Password");
        m_rbEnterCreds.addActionListener(e -> m_provider.getUseCredentialsModel().setBooleanValue(false));

        m_rbUseFw = new JRadioButton("Credentials flow variable");
        m_rbUseFw.addActionListener(e -> m_provider.getUseCredentialsModel().setBooleanValue(true));

        var group = new ButtonGroup();
        group.add(m_rbEnterCreds);
        group.add(m_rbUseFw);

        var enterCredPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enterCredPanel.add(m_rbEnterCreds);

        var flowVarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        flowVarPanel.add(m_rbUseFw);
        flowVarPanel.add(m_flowVarSelector.getComponentPanel());

        var box = new Box(BoxLayout.PAGE_AXIS);
        box.add(enterCredPanel);
        box.add(createUsernamePasswordPanel());
        box.add(flowVarPanel);
        box.add(Box.createVerticalStrut(10));

        box.add(new ScopesEditComponent(m_provider.getScopesModel(), m_provider.getBlobStorageAccountModel(),
                m_provider.getOtherScopesModel()));
        box.add(Box.createVerticalGlue());
        return box;
    }

    private JPanel createUsernamePasswordPanel() {
        var usernameInput = new DialogComponentString(m_provider.getUsernameModel(), "", false, 30);
        usernameInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);

        var passwordInput = new DialogComponentPasswordField(m_provider.getPasswordModel(), "",
                30);
        passwordInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);

        final var panel = new JPanel(new GridBagLayout());
        final var gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Username: "), gbc);

        gbc.gridy = 1;
        panel.add(new JLabel("Password: "), gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(usernameInput.getComponentPanel(), gbc);

        gbc.gridy = 1;
        panel.add(passwordInput.getComponentPanel(), gbc);

        panel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        return panel;
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_flowVarSelector.loadSettingsFrom(settings, specs);
    }

    @Override
    public void onShown() {
        if (!m_provider.getUseCredentialsModel().getBooleanValue()) {
            m_rbEnterCreds.setSelected(true);
        } else {
            m_rbUseFw.setSelected(true);
        }
    }

    @Override
    public void onCancel() {
        // Do nothing
    }

    @Override
    public void onClose() {
        // nothing to do
    }
}
