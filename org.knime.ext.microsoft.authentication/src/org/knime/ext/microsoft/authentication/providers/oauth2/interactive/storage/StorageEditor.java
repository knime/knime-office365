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
 *   2020-07-03 (bjoern): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.FlowVariableModel;
import org.knime.core.node.NodeDialogPane;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.DialogComponentWriterFileChooser;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.SettingsModelWriterFileChooser;
import org.knime.filehandling.core.defaultnodesettings.filtermode.SettingsModelFilterMode.FilterMode;

/**
 * Dialog panel that allows the user to choose between the different ways of
 * storing the access token.
 *
 * @author Alexander Bondaletov
 */
public class StorageEditor extends JPanel {

    private final StorageSettings m_settings;

    private final DialogComponentWriterFileChooser m_fileChooser;

    private final JRadioButton m_rbMemory;
    private final JRadioButton m_rbFile;
    private final JRadioButton m_rbSettings;

    public StorageEditor(final StorageSettings settings, final NodeDialogPane nodeDialog) {
        super(new GridBagLayout());
        m_settings = settings;

        m_rbMemory = createRadioBtn(StorageType.MEMORY);
        m_rbFile = createRadioBtn(StorageType.FILE);
        m_rbSettings = createRadioBtn(StorageType.SETTINGS);

        ButtonGroup group = new ButtonGroup();
        group.add(m_rbMemory);
        group.add(m_rbFile);
        group.add(m_rbSettings);

        final SettingsModelWriterFileChooser fileModel = m_settings.getFileStorage().getFileModel();
        final FlowVariableModel fvm = nodeDialog //
                .createFlowVariableModel(fileModel.getKeysForFSLocation(), //
                        FSLocationVariableType.INSTANCE);
        m_fileChooser = new DialogComponentWriterFileChooser(fileModel,
                "microsoft_auth_token_cache_file",
                fvm,
                FilterMode.FILE);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        add(m_rbMemory, c);

        c.gridy += 1;
        add(m_rbFile, c);

        c.gridy += 1;
        c.insets = new Insets(5, 15, 5, 5);
        add(m_fileChooser.getComponentPanel(), c);

        c.gridy += 1;
        c.insets = new Insets(0, 0, 0, 0);
        add(m_rbSettings, c);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Access token storage"));
    }

    private JRadioButton createRadioBtn(final StorageType location) {
        JRadioButton rb = new JRadioButton(location.getTitle());
        rb.setSelected(m_settings.getStorageType() == location);
        rb.addActionListener(e -> {
            m_settings.setStorageType(location);
        });
        return rb;
    }

    public void onShown() {
        switch (m_settings.getStorageType()) {
        case MEMORY:
            m_rbMemory.setSelected(true);
            break;
        case FILE:
            m_rbFile.setSelected(true);
            break;
        case SETTINGS:
            m_rbSettings.setSelected(true);
            break;
        }
    }
}
