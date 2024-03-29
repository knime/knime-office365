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
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.DialogComponentWriterFileChooser;

/**
 * Dialog panel that allows the user to choose between the different ways of
 * storing the access token.
 *
 * @author Alexander Bondaletov
 */
public class StorageEditor extends JPanel {

    private static final long serialVersionUID = 1L;

    private final StorageSettings m_settings;// NOSONAR not intended for serialization

    private final DialogComponentWriterFileChooser m_fileChooser;// NOSONAR not intended for serialization

    private final JRadioButton m_rbMemory;
    private final JRadioButton m_rbFile;
    private final JRadioButton m_rbSettings;
    private final JButton m_clearCurrent;
    private final JButton m_clearAll;

    private final LoginStatusEventHandler m_loginStatusEventHandler;// NOSONAR not intended for serialization

    /**
     * Constructor.
     *
     * @param settings
     * @param nodeDialog
     * @param loginStatusEventHandler
     */
    public StorageEditor(final StorageSettings settings, final NodeDialogPane nodeDialog,
            final LoginStatusEventHandler loginStatusEventHandler) {
        super(new GridBagLayout());
        m_settings = settings;
        m_loginStatusEventHandler = loginStatusEventHandler;

        m_rbMemory = createRadioBtn(StorageType.MEMORY);
        m_rbFile = createRadioBtn(StorageType.FILE);
        m_rbSettings = createRadioBtn(StorageType.SETTINGS);

        m_clearCurrent = new JButton("Clear selected");
        m_clearCurrent.addActionListener(e -> onClearCurrent());

        m_clearAll = new JButton("Clear all");
        m_clearAll.addActionListener(e -> clearAll());

        var group = new ButtonGroup();
        group.add(m_rbMemory);
        group.add(m_rbFile);
        group.add(m_rbSettings);

        final var fileModel = m_settings.getFileStorage().getFileModel();
        fileModel.addChangeListener(e -> m_loginStatusEventHandler.run());
        final var fvm = nodeDialog //
                .createFlowVariableModel(fileModel.getKeysForFSLocation(), //
                        FSLocationVariableType.INSTANCE);
        m_fileChooser = new DialogComponentWriterFileChooser(fileModel, "microsoft_auth_token_cache_file", fvm);
        m_fileChooser.getComponentPanel().setBorder(
                (BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Token file to read/write")));

        initLayout(); // NOSONAR
    }

    private void initLayout() {
        var gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.gridx = 0;
        gbc.gridy = 0;

        add(m_rbMemory, gbc);

        gbc.gridy += 1;
        add(m_rbFile, gbc);

        gbc.gridy += 1;
        gbc.insets = new Insets(5, 20, 5, 5);
        add(m_fileChooser.getComponentPanel(), gbc);

        gbc.gridy += 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        add(m_rbSettings, gbc);

        gbc.gridy += 1;
        gbc.insets = new Insets(15, 0, 0, 0);
        final var buttonBox = Box.createHorizontalBox();
        buttonBox.add(m_clearCurrent);
        buttonBox.add(Box.createHorizontalStrut(5));
        buttonBox.add(m_clearAll);
        buttonBox.add(Box.createHorizontalGlue());
        add(buttonBox, gbc);

        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Token storage"));
    }

    private void onClearCurrent() {
        m_clearCurrent.setEnabled(false);
        m_clearAll.setEnabled(false);
        new SwingWorkerWithContext<Void, Void>() {
            @Override
            protected Void doInBackgroundWithContext() throws Exception {
                m_settings.clearCurrentStorage();
                return null;
            }

            @Override
            protected void doneWithContext() {
                m_clearCurrent.setEnabled(true);
                m_clearAll.setEnabled(true);
                m_loginStatusEventHandler.run();
            }

        }.execute();
    }

    /**
     * Clears all of the storages.
     */
    public void clearAll() {
        m_clearCurrent.setEnabled(false);
        m_clearAll.setEnabled(false);
        new SwingWorkerWithContext<Void, Void>() {
            @Override
            protected Void doInBackgroundWithContext() throws Exception {
                m_settings.clearStorage();
                return null;
            }

            @Override
            protected void doneWithContext() {
                m_clearCurrent.setEnabled(true);
                m_clearAll.setEnabled(true);
                m_loginStatusEventHandler.run();
            }
        }.execute();
    }

    private JRadioButton createRadioBtn(final StorageType location) {
        var rb = new JRadioButton(location.getTitle());
        rb.setSelected(m_settings.getStorageType() == location);
        rb.addActionListener(e -> {
            m_settings.setStorageType(location);
            m_loginStatusEventHandler.run();
        });
        return rb;
    }

    /**
     * To be invoked just before the dialog or this storage editor is being shown.
     */
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

    /**
     * Load storage editor settings in the node dialog.
     *
     * @param settings
     * @param specs
     * @throws NotConfigurableException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_fileChooser.loadSettingsFrom(settings, specs);
    }

    /**
     * Method which should be called in the onClose method of the node dialog.
     */
    public void onClose() {
        m_fileChooser.onClose();
    }
}
