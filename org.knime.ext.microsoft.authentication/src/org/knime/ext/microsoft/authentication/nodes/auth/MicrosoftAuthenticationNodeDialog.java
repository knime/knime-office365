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
package org.knime.ext.microsoft.authentication.nodes.auth;

import java.awt.CardLayout;
import java.awt.FlowLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.microsoft.authentication.providers.AuthProviderType;



/**
 * Microsoft Authentication node dialog.
 *
 * @author Alexander Bondaletov
 */
public class MicrosoftAuthenticationNodeDialog extends NodeDialogPane {

    private final MicrosoftAuthenticationSettings m_settings;

    /**
     * Creates new instance.
     */
    public MicrosoftAuthenticationNodeDialog() {
        super();
        m_settings = new MicrosoftAuthenticationSettings();

        Box box = new Box(BoxLayout.PAGE_AXIS);
        box.add(createProviderCombo());
        box.add(createEditorPanel());

        addTab("Settings", box);
    }

    private JComponent createProviderCombo() {
        JComboBox<AuthProviderType> cb = new JComboBox<>(AuthProviderType.values());

        cb.addActionListener(e -> {
            AuthProviderType provider = (AuthProviderType) cb.getSelectedItem();
            m_settings.getProviderTypeModel().setStringValue(provider.name());
        });

        m_settings.getProviderTypeModel().addChangeListener(e -> {
            cb.setSelectedItem(m_settings.getProviderType());
        });

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Authentication mode:"));
        panel.add(cb);

        return panel;
    }

    private JPanel createEditorPanel() {
        JPanel cards = new JPanel(new CardLayout());

        for (AuthProviderType type : AuthProviderType.values()) {
            JComponent editor = m_settings.getProvider(type).createEditor();
            cards.add(editor, type.name());
        }

        m_settings.getProviderTypeModel().addChangeListener(e -> {
            ((CardLayout) cards.getLayout()).show(cards, m_settings.getProviderType().name());
        });

        return cards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_settings.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        try {
            m_settings.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex) {
            // ignore
        }
    }
}
