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
 *   2020-08-20 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers.azure.storage.sas;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentFlowVariableNameSelection2;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.VariableType.CredentialsType;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProviderEditor;

/**
 * Editor component for the {@link AzureStorageSasTokenAuthProvider}.
 *
 * @author Alexander Bondaletov
 */
public class AzureStorageSasTokenAuthProviderEditor implements MicrosoftAuthProviderEditor {

    private final AzureStorageSasTokenAuthProvider m_provider;
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
    public AzureStorageSasTokenAuthProviderEditor(final AzureStorageSasTokenAuthProvider provider,
            final MicrosoftAuthenticationNodeDialog parent) {
        m_provider = provider;
        m_parent = parent;

        initUI();
    }

    private void initUI() {
        JRadioButton rbEnterCreds = new JRadioButton("Blob service SAS URL");
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
        component.add(createSaSUrlPanel(), c);

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

    private JComponent createSaSUrlPanel() {
        DialogComponentString sasUrl = new DialogComponentString(m_provider.getSasUrlModel(), "", true, 50);
        sasUrl.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));
        return sasUrl.getComponentPanel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JComponent getComponent() {
        return component;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_flowVarSelector.loadSettingsFrom(settings, specs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onShown() {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCancel() {
        // nothing to do
    }

}
