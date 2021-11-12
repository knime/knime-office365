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
package org.knime.ext.microsoft.authentication.providers.oauth2;

import static org.knime.ext.microsoft.authentication.port.oauth2.Scope.AZURE_BLOB_STORAGE;
import static org.knime.ext.microsoft.authentication.port.oauth2.Scope.OTHERS;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.defaultnodesettings.DialogComponent;
import org.knime.core.node.defaultnodesettings.DialogComponentMultiLineString;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;
import org.knime.ext.microsoft.authentication.port.oauth2.Scope;


/**
 * Editor component for selecting Microsoft scopes.
 *
 * @author Alexander Bondaletov
 */
public class ScopesEditComponent extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SettingsModelStringArray m_scopes;
    private final SettingsModelString m_blobStorageAccount;
    private final SettingsModelString m_manualScopes;

    private JLabel m_blobStorageAccountLabel;
    private Map<Scope, JCheckBox> m_checkboxes;


    /**
     * Creates new instance.
     *
     * @param scopes
     *            Settings model to store settings.
     * @param blobStorageAccount
     *            Settings model to store blob storage account.
     * @param manualScopes
     */
    public ScopesEditComponent(final SettingsModelStringArray scopes, final SettingsModelString blobStorageAccount,
            final SettingsModelString manualScopes) {
        m_scopes = scopes;
        m_blobStorageAccount = blobStorageAccount;
        m_manualScopes = manualScopes;
        m_checkboxes = new EnumMap<>(Scope.class);
        initUI();

        m_scopes.addChangeListener(e -> updateCheckboxes());
        updateCheckboxes();
    }

    private void initUI() {
        m_blobStorageAccountLabel = new JLabel("Storage account:");
        final DialogComponentString blobStorageAcc = new DialogComponentString(m_blobStorageAccount, "");
        blobStorageAcc.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));
        blobStorageAcc.getComponentPanel().setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

        final DialogComponent otherScopes = new DialogComponentMultiLineString(m_manualScopes, "", true, 90, 5);
        otherScopes.setToolTipText("You can enter multiple scopes separated by <new line>");
        otherScopes.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));
        otherScopes.getComponentPanel().setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

        setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.5;
        c.gridwidth = 2;
        c.gridx = 0;
        c.gridy = 0;

        for (Scope scope : Scope.values()) {
            add(createCheckbox(scope), c);
            c.gridy += 1;

            if (scope == AZURE_BLOB_STORAGE) {
                c.weightx = 0;
                c.gridwidth = 1;
                c.insets = new Insets(0, 20, 0, 5);
                add(m_blobStorageAccountLabel, c);

                c.insets = new Insets(0, 15, 0, 5);
                c.gridx = 1;
                c.weightx = 0.5;
                c.insets = new Insets(0, 0, 0, 0);
                add(blobStorageAcc.getComponentPanel(), c);

                c.gridx = 0;
                c.gridwidth = 2;
                c.gridy += 1;
            } else if (scope == OTHERS) {
                c.gridwidth = 2;
                c.insets = new Insets(0, 20, 0, 5);
                c.weightx = 0.5;
                c.insets = new Insets(0, 0, 0, 0);
                add(otherScopes.getComponentPanel(), c);

                c.gridx = 0;
                c.gridwidth = 2;
                c.gridy += 1;
            }
        }

        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Request access to"));
    }

    private JCheckBox createCheckbox(final Scope scope) {
        JCheckBox cb = new JCheckBox(scope.getTitle());
        cb.addActionListener(e -> {
            onSelected(scope, cb.isSelected());
        });
        m_checkboxes.put(scope, cb);
        return cb;
    }

    private void onSelected(final Scope scope, final boolean selected) {
        final var currentScopes = toSet(m_scopes);

        final var newScopes = new HashSet<String>();

        if (selected) {
            newScopes.add(scope.getScope());
            currentScopes.stream() //
                    .filter(s -> Scope.fromScope(s).canBeGroupedWith(scope)) //
                    .forEach(newScopes::add);
        } else {
            newScopes.addAll(currentScopes);
            newScopes.remove(scope.getScope());
        }

        m_scopes.setStringArrayValue(newScopes.toArray(String[]::new));
    }

    private void updateCheckboxes() {
        Set<String> set = toSet(m_scopes);
        for (Entry<Scope, JCheckBox> entry : m_checkboxes.entrySet()) {
            entry.getValue().setSelected(set.contains(entry.getKey().getScope()));
        }

        m_blobStorageAccount.setEnabled(set.contains(AZURE_BLOB_STORAGE.getScope()));
        m_blobStorageAccountLabel.setEnabled(set.contains(AZURE_BLOB_STORAGE.getScope()));
        m_manualScopes.setEnabled(set.contains(OTHERS.getScope()));
    }

    private static Set<String> toSet(final SettingsModelStringArray settings) {
        return new HashSet<>(Arrays.asList(settings.getStringArrayValue()));
    }
}
