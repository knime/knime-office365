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
 *   2022-10-31 (zkriyarakhimberdiyev): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.application;

import static org.knime.ext.microsoft.authentication.port.oauth2.Scope.OTHER;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;
import org.knime.ext.microsoft.authentication.port.oauth2.Scope;
import org.knime.ext.microsoft.authentication.port.oauth2.ScopeType;
import org.knime.ext.microsoft.authentication.providers.oauth2.ScopesEditComponent;

/**
 * Editor component for selecting Microsoft scopes with application permissions.
 *
 * @author Zkriya Rakhimberdiyev
 */
public class ApplicationPermissionsScopesEditComponent extends ScopesEditComponent {

    private static final long serialVersionUID = 1L;

    private final SettingsModelString m_manualScope; // NOSONAR not intended for serialization

    private JLabel m_resourceIdentifierLabel;

    /**
     * Creates new instance.
     *
     * @param scopes
     *            Settings model to store settings.
     * @param manualScope
     *            Settings model for manual scope
     */
    public ApplicationPermissionsScopesEditComponent(final SettingsModelStringArray scopes,
            final SettingsModelString manualScope) {
        super(scopes);
        m_manualScope = manualScope;
        initUI();

        m_scopes.addChangeListener(e -> updateCheckboxes());
        updateCheckboxes();
    }

    private void initUI() {
        m_resourceIdentifierLabel = new JLabel("Resource identifier:");
        final var otherScope = new DialogComponentString(m_manualScope, "");
        otherScope.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));
        otherScope.getComponentPanel().setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

        setLayout(new GridBagLayout());

        var gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = 0;

        for (Scope scope : Scope.listByScopeType(ScopeType.APPLICATION)) {

            add(createCheckbox(scope), gbc);
            gbc.gridy += 1;

            if (scope == OTHER) {
                gbc.weightx = 0;
                gbc.gridwidth = 1;
                gbc.insets = new Insets(0, 20, 0, 5);
                add(m_resourceIdentifierLabel, gbc);

                gbc.insets = new Insets(0, 15, 0, 5);
                gbc.gridx = 1;
                gbc.weightx = 0.5;
                gbc.insets = new Insets(0, 0, 0, 0);
                add(otherScope.getComponentPanel(), gbc);

                gbc.gridx = 0;
                gbc.gridwidth = 2;
                gbc.gridy += 1;
            }
        }
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;

        add(Box.createVerticalGlue(), gbc);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Request access to"));
    }

    private void updateCheckboxes() {
        final var set = scopes();
        for (Entry<Scope, JCheckBox> entry : m_checkboxes.entrySet()) {
            entry.getValue().setSelected(set.contains(entry.getKey().getScope()));
        }
        m_resourceIdentifierLabel.setEnabled(set.contains(OTHER.getScope()));
        m_manualScope.setEnabled(set.contains(OTHER.getScope()));
    }
}
