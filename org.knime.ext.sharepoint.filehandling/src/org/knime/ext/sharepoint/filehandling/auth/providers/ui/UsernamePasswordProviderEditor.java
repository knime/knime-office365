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
package org.knime.ext.sharepoint.filehandling.auth.providers.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.ext.sharepoint.filehandling.auth.providers.UsernamePasswordAuthProvider;

/**
 * Editor component for {@link UsernamePasswordAuthProvider}.
 *
 * @author Alexander Bondaletov
 */
public class UsernamePasswordProviderEditor extends AbstractAuthProviderEditor<UsernamePasswordAuthProvider> {
    private static final long serialVersionUID = 1L;

    /**
     * Creates new instance.
     *
     * @param provider
     *            The auth provider.
     *
     */
    public UsernamePasswordProviderEditor(final UsernamePasswordAuthProvider provider) {
        super(provider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JComponent createContentPane() {
        DialogComponentString usernameInput = new DialogComponentString(m_provider.getUsernameModel(), "Username",
                false, 40);
        DialogComponentString passwordInput = new DialogComponentString(m_provider.getPasswordModel(),
                "Password",
                false, 40);

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(usernameInput.getComponentPanel(), c);
        c.gridy += 1;
        panel.add(passwordInput.getComponentPanel(), c);
        return panel;
    }

}
