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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentFlowVariableNameSelection2;
import org.knime.core.node.defaultnodesettings.DialogComponentPasswordField;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.StringHistoryPanel;
import org.knime.core.node.workflow.FlowVariable;
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

    private final StringHistoryPanel m_tenantIdInput;

    private final DialogComponentString m_clientIdInput;

    private final DialogComponentPasswordField m_secretInput;

    private final DialogComponentBoolean m_useCredentials; // NOSONAR not using serialization

    private final DialogComponentFlowVariableNameSelection2 m_credentialsFlowVarChooser;

    private final Supplier<Map<String, FlowVariable>> m_flowVariablesSupplier;

    private final JLabel m_clientIdLabel = new JLabel("Client/Application ID: ");

    private final JLabel m_secretLabel = new JLabel("Secret: ");

    ApplicationPermissionsOAuth2ProviderEditor(final ApplicationPermissionsOAuth2Provider provider,
            final MicrosoftAuthenticationNodeDialog parent) {
        super(provider);

        m_tenantIdInput = new StringHistoryPanel("msauth.tenant_id");
        m_tenantIdInput.setPrototypeDisplayValue("M".repeat(40));

        m_clientIdInput = new DialogComponentString(m_provider.getClientIdModel(), "", false, 46);
        m_clientIdInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);

        m_secretInput = new DialogComponentPasswordField(m_provider.getSecretModel(), "", 46);
        m_secretInput.getComponentPanel().setAlignmentX(Component.LEFT_ALIGNMENT);

        m_flowVariablesSupplier = () -> parent.getAvailableFlowVariables(CredentialsType.INSTANCE);
        m_useCredentials = new DialogComponentBoolean(m_provider.getUseCredentialsModel(), "Use credentials:");
        m_credentialsFlowVarChooser = new DialogComponentFlowVariableNameSelection2(
                m_provider.getCredentialsNameModel(), "",
                m_flowVariablesSupplier);

        m_provider.getUseCredentialsModel().addChangeListener(e -> updateEnabledness());
    }

    @SuppressWarnings("deprecation")
    private void updateEnabledness() {
        if (m_flowVariablesSupplier.get().isEmpty()) {
            m_provider.getUseCredentialsModel().setBooleanValue(false);
            m_useCredentials.setEnabled(false);
        } else {
            m_useCredentials.setEnabled(true);
        }

        m_clientIdLabel.setEnabled(!m_provider.getUseCredentialsModel().getBooleanValue());
        m_secretLabel.setEnabled(!m_provider.getUseCredentialsModel().getBooleanValue());
    }

    @Override
    protected JComponent createContentPane() {
        var panel = new JPanel(new GridBagLayout());

        final var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(10, 5, 5, 5);
        panel.add(new JLabel("Tenant ID/Domain:"), gbc);

        gbc.gridx++;
        gbc.insets = new Insets(10, 10, 5, 5);
        panel.add(m_tenantIdInput, gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(20, 5, 5, 5);
        panel.add(m_clientIdLabel, gbc);

        gbc.gridx++;
        gbc.insets = new Insets(20, 0, 5, 5);
        panel.add(m_clientIdInput.getComponentPanel(), gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 5, 5, 5);
        panel.add(m_secretLabel, gbc);

        gbc.gridx++;
        gbc.insets = new Insets(0, 0, 5, 5);
        panel.add(m_secretInput.getComponentPanel(), gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(m_useCredentials.getComponentPanel(), gbc);

        gbc.gridx++;
        panel.add(m_credentialsFlowVarChooser.getComponentPanel(), gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new ApplicationPermissionsScopesEditComponent(m_provider.getScopesModel(),
                m_provider.getOtherScopeModel()), gbc);

        gbc.gridy++;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    @Override
    public void onShown() {
        updateEnabledness();
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_tenantIdInput.setSelectedString(m_provider.getTenantIdModel().getStringValue());
        m_tenantIdInput.commitSelectedToHistory();
        m_tenantIdInput.updateHistory();

        m_clientIdInput.loadSettingsFrom(settings, specs);
        m_secretInput.loadSettingsFrom(settings, specs);
        m_useCredentials.loadSettingsFrom(settings, specs);
        m_credentialsFlowVarChooser.loadSettingsFrom(settings, specs);
    }

    @Override
    public void beforeSaveSettings(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_provider.getTenantIdModel().setStringValue(m_tenantIdInput.getSelectedString());
        m_tenantIdInput.commitSelectedToHistory();
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
