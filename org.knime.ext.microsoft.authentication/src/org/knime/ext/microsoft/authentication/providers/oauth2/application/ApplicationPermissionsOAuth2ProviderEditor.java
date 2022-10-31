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
 *   2022-10-16 (Zkriya Rakhimberdiyev): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.application;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentFlowVariableNameSelection2;
import org.knime.core.node.defaultnodesettings.DialogComponentPasswordField;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.StringHistoryPanel;
import org.knime.core.node.workflow.VariableType.CredentialsType;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.providers.oauth2.MSALAuthProviderEditor;

/**
 * Editor component for {@link ApplicationPermissionsOAuth2Provider}.
 *
 * @author Zkriya Rakhimberdiyev
 */
public class ApplicationPermissionsOAuth2ProviderEditor
        extends MSALAuthProviderEditor<ApplicationPermissionsOAuth2Provider> {

    private final MicrosoftAuthenticationNodeDialog m_parent;

    private final StringHistoryPanel m_tenantIdInput;
    private JRadioButton m_rbEnterCreds;
    private JRadioButton m_rbUseFw;
    private DialogComponentFlowVariableNameSelection2 m_flowVarSelector;

    /**
     * @param provider
     */
    ApplicationPermissionsOAuth2ProviderEditor(final ApplicationPermissionsOAuth2Provider provider,
            final MicrosoftAuthenticationNodeDialog parent) {
        super(provider);
        m_tenantIdInput = new StringHistoryPanel("tenant_id");
        m_parent = parent;
        m_flowVarSelector = new DialogComponentFlowVariableNameSelection2(m_provider.getCredentialsNameModel(), "",
                () -> m_parent.getAvailableFlowVariables(CredentialsType.INSTANCE));
    }

    @Override
    protected JComponent createContentPane() {
        m_rbEnterCreds = new JRadioButton("Client secret");
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

        var clientIdSecretPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clientIdSecretPanel.add(createClientIdSecretPanel());

        var box = new Box(BoxLayout.PAGE_AXIS);
        box.add(enterCredPanel);
        box.add(clientIdSecretPanel);
        box.add(flowVarPanel);

        box.add(new ApplicationPermissionsScopesEditComponent(m_provider.getScopesModel(),
                m_provider.getOtherScopeModel()));
        return box;
    }

    private JPanel createClientIdSecretPanel() {
        final var clientIdInput = new DialogComponentString(m_provider.getClientIdModel(), "", false, 26);
        clientIdInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);

        final var secretInput = new DialogComponentPasswordField(m_provider.getSecretModel(), "", 26);
        secretInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);

        final var panel = new JPanel(new GridBagLayout());
        final var gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("Tenant ID/Domain:"), gbc);
        gbc.gridy = 1;
        panel.add(new JLabel("Client/Application ID: "), gbc);
        gbc.gridy = 2;
        panel.add(new JLabel("Secret: "), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;

        gbc.insets = new Insets(0, 12, 0, 0);
        panel.add(m_tenantIdInput, gbc);
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridy = 1;
        panel.add(clientIdInput.getComponentPanel(), gbc);
        gbc.gridy = 2;
        panel.add(secretInput.getComponentPanel(), gbc);

        gbc.weightx = 1;
        gbc.gridx = 2;
        gbc.gridy = 0;
        panel.add(Box.createHorizontalGlue(), gbc);
        gbc.gridy = 1;
        panel.add(Box.createHorizontalGlue(), gbc);
        gbc.gridy = 2;
        panel.add(Box.createHorizontalGlue(), gbc);

        panel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        return panel;
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_tenantIdInput.setSelectedString(m_provider.getTenantIdModel().getStringValue());
        m_tenantIdInput.commitSelectedToHistory();
        m_tenantIdInput.updateHistory();

        m_flowVarSelector.loadSettingsFrom(settings, specs);
    }

    @Override
    public void beforeSaveSettings(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_provider.getTenantIdModel().setStringValue(m_tenantIdInput.getSelectedString());
        m_tenantIdInput.commitSelectedToHistory();
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
