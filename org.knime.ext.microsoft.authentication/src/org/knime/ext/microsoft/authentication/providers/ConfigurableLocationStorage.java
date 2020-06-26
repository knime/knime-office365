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
package org.knime.ext.microsoft.authentication.providers;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.core.node.defaultnodesettings.DialogComponentFileChooser;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.FileUtil;

/**
 * Class for storing settings in a one of the different locations. Supported
 * locations are: Memory, Node settings, External file.
 *
 * @author Alexander Bondaletov
 */
public class ConfigurableLocationStorage {
    private static final NodeLogger LOGGER = NodeLogger.getLogger(ConfigurableLocationStorage.class);
    private static final String KEY_LOCATION = "location";
    private static final String KEY_FILE_PATH = "filePath";

    private static Map<String, String> storage = new HashMap<>();

    private final SettingsModelString m_location;
    private final SettingsModelString m_filePath;

    private final SettingsModelString m_dataModel;

    /**
     * Creates new instance.
     *
     * @param dataModel
     *            Data model to store.
     *
     */
    public ConfigurableLocationStorage(final SettingsModelString dataModel) {
        m_location = new SettingsModelString(KEY_LOCATION, StorageLocation.MEMORY.name());
        m_filePath = new SettingsModelString(KEY_FILE_PATH, "");
        m_dataModel = dataModel;
    }

    private StorageLocation getLocation() {
        try {
            return StorageLocation.valueOf(m_location.getStringValue());
        } catch (IllegalArgumentException e) {
            return StorageLocation.MEMORY;
        }
    }

    /**
     * Saves the location settings to a given {@link ConfigWO} and the dataModel to
     * the location specified by a current settings.
     *
     * @param settings
     *            The settings.
     */
    public void saveSettings(final ConfigWO settings) {
        settings.addString(m_location.getKey(), m_location.getStringValue());
        settings.addString(m_filePath.getKey(), m_filePath.getStringValue());

        try {
            saveData(settings);
        } catch (IOException | URISyntaxException ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    private void saveData(final ConfigWO settings) throws IOException, URISyntaxException {
        StorageLocation location = getLocation();
        switch (location) {
        case MEMORY:
            putToMemory(m_dataModel.getKey(), m_dataModel.getStringValue());
            break;
        case FILE:
            String data = m_dataModel.getStringValue();
            byte[] bytes = data == null ? new byte[0] : data.getBytes();

            Files.write(FileUtil.resolveToPath(FileUtil.toURL(m_filePath.getStringValue())), bytes);
            break;
        case SETTINGS:
            settings.addString(m_dataModel.getKey(), m_dataModel.getStringValue());
            break;

        }
    }

    /**
     * Validates the consistency of the current settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        StorageLocation location = getLocation();

        if (location == StorageLocation.FILE && m_filePath.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("File path is not set");
        }
    }

    /**
     * Loads the location settings from a given {@link ConfigRO} and the data model
     * from the location specified by these settings.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void loadSettings(final ConfigRO settings) throws InvalidSettingsException {
        m_location.setStringValue(settings.getString(KEY_LOCATION));
        m_filePath.setStringValue(settings.getString(KEY_FILE_PATH));

        try {
            loadData(settings);
        } catch (IOException | URISyntaxException ex) {
            throw new InvalidSettingsException(ex);
        }
    }

    private void loadData(final ConfigRO settings) throws IOException, URISyntaxException, InvalidSettingsException {
        StorageLocation location = getLocation();
        switch (location) {
        case MEMORY:
            m_dataModel.setStringValue(getFromMemory(m_dataModel.getKey()));
            break;
        case FILE:
            m_dataModel.setStringValue(new String(
                    Files.readAllBytes(FileUtil.resolveToPath(FileUtil.toURL(m_filePath.getStringValue())))));
            break;
        case SETTINGS:
            m_dataModel.setStringValue(settings.getString(m_dataModel.getKey()));
            break;

        }
    }

    /**
     * Creates the editor component for a location settings.
     *
     * @return The component.
     */
    public JComponent createEditor() {
        JRadioButton rbMemory = createRadioBtn(StorageLocation.MEMORY);
        JRadioButton rbFile = createRadioBtn(StorageLocation.FILE);
        JRadioButton rbSettings = createRadioBtn(StorageLocation.SETTINGS);

        ButtonGroup group = new ButtonGroup();
        group.add(rbMemory);
        group.add(rbFile);
        group.add(rbSettings);

        DialogComponentFileChooser fileChooser = new DialogComponentFileChooser(m_filePath, "knime",
                JFileChooser.SAVE_DIALOG, false);
        fileChooser.getComponentPanel().setVisible(false);
        m_location.addChangeListener(e -> {
            fileChooser.getComponentPanel().setVisible(getLocation() == StorageLocation.FILE);
        });

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(rbMemory, c);

        c.gridy += 1;
        panel.add(rbFile, c);

        c.gridy += 1;
        panel.add(fileChooser.getComponentPanel(), c);

        c.gridy += 1;
        panel.add(rbSettings, c);
        panel.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Access token storage"));

        return panel;
    }

    private JRadioButton createRadioBtn(final StorageLocation location) {
        JRadioButton rb = new JRadioButton(location.getTitle());
        rb.setSelected(getLocation() == location);
        rb.addActionListener(e -> {
            m_location.setStringValue(location.name());
        });
        m_location.addChangeListener(e -> {
            rb.setSelected(getLocation() == location);
        });
        return rb;
    }

    private static void putToMemory(final String key, final String value) {
        storage.put(key, value);
    }

    private static String getFromMemory(final String key) {
        return storage.get(key);
    }

    enum StorageLocation {
        MEMORY("Memory (stores token in-memory)"), //
        FILE("File (stores token in separate file)"), //
        SETTINGS("Node (stores token in node settings)");

        private String m_title;

        private StorageLocation(final String title) {
            m_title = title;
        }

        /**
         * @return the title
         */
        public String getTitle() {
            return m_title;
        }
    }
}
