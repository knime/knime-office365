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
package org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage;

import java.io.IOException;
import java.util.function.Consumer;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.LoginStatus;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.MemoryCacheAccessTokenSupplier;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;

/**
 * Class for storing settings in a one of the different locations. Supported
 * locations are: Memory, Node settings, External file.
 *
 * @author Alexander Bondaletov
 */
public class StorageSettings {

    private static final String KEY_STORAGE_TYPE = "storageType";

    private static final String KEY_MEMORY = "memory";

    private static final String KEY_FILE = "file";

    private static final String KEY_NODE_SETTINGS = "nodesettings";

    /**
     * Specifies where to store the token cache.
     */
    private final SettingsModelString m_storageType = new SettingsModelString(KEY_STORAGE_TYPE,
            StorageType.MEMORY.name());

    private final InMemoryStorage m_inMemoryStorage;

    private final FileStorage m_fileStorage;

    private final NodeSettingsStorage m_nodeSettingsStorage;

    /**
     * Constructor.
     *
     * @param portsConfig
     * @param nodeInstanceId
     * @param endpoint
     */
    public StorageSettings(final PortsConfiguration portsConfig, final String nodeInstanceId, final String endpoint) {
        m_inMemoryStorage = new InMemoryStorage(nodeInstanceId, endpoint);
        m_nodeSettingsStorage = new NodeSettingsStorage(nodeInstanceId, endpoint);
        m_fileStorage = new FileStorage(portsConfig, nodeInstanceId, endpoint);

        m_storageType
                .addChangeListener(e -> m_fileStorage.getFileModel().setEnabled(getStorageType() == StorageType.FILE));
    }

    /**
     * @return the model for the storage type.
     */
    public SettingsModelString getStorageTypeModel() {
        return m_storageType;
    }

    /**
     * @return where to store the token cache.
     */
    public StorageType getStorageType() {
        return StorageType.valueOf(m_storageType.getStringValue());
    }

    /**
     * Sets the storage type to use.
     *
     * @param type
     *            The storage type to use.
     */
    public void setStorageType(final StorageType type) {
        m_storageType.setStringValue(type.name());
    }

    /**
     * @return the inMemoryStorage
     */
    public InMemoryStorage getInMemoryStorage() {
        return m_inMemoryStorage;
    }

    /**
     * @return the fileStorage
     */
    public FileStorage getFileStorage() {
        return m_fileStorage;
    }

    /**
     * @return the nodeSettingsStorage
     */
    public NodeSettingsStorage getNodeSettingsStorage() {
        return m_nodeSettingsStorage;
    }

    /**
     * Clears the stored credential of the current storage provider.
     *
     * @throws IOException
     */
    public void clearCurrentStorage() throws IOException {
        final var oldLoginStatus = getLoginStatus();
        if (!oldLoginStatus.isLoggedIn()) {
            return;
        }

        getCurrentStorageProvider().clear();
    }

    /**
     * Clears the stored credential of the all storage providers.
     *
     * @throws IOException
     */
    public void clearStorage() throws IOException {
        getLoginStatus();

        m_inMemoryStorage.clear();
        m_fileStorage.clear();
        m_nodeSettingsStorage.clear();
    }

    /**
     * Tries to reads the currently stored token (if present) and returns its login
     * status.
     *
     * @return the current login status
     * @throws IOException
     *             if something went wrong while reading the currently stored token.
     */
    public LoginStatus getLoginStatus() throws IOException {
        final var tokenCacheString = readTokenCache();
        if (tokenCacheString == null) {
            return LoginStatus.NOT_LOGGED_IN;
        } else {
            return LoginStatus.parseFromTokenCache(tokenCacheString);
        }
    }


    /**
     * Saves provider's settings into a given {@link NodeSettingsWO}.
     *
     * @param settings
     *            The settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_inMemoryStorage.saveSettingsTo(settings.addNodeSettings(KEY_MEMORY));
        m_fileStorage.saveSettingsTo(settings.addNodeSettings(KEY_FILE));
        m_nodeSettingsStorage.saveSettingsTo(settings.addNodeSettings(KEY_NODE_SETTINGS));
        m_storageType.saveSettingsTo(settings);
    }

    /**
     * Loads provider's settings from a given {@link NodeSettingsRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_inMemoryStorage.loadSettingsFrom(settings.getNodeSettings(KEY_MEMORY));
        m_fileStorage.loadSettingsFrom(settings.getNodeSettings(KEY_FILE));
        m_nodeSettingsStorage.loadSettingsFrom(settings.getNodeSettings(KEY_NODE_SETTINGS));
        m_storageType.loadSettingsFrom(settings);
        m_fileStorage.getFileModel().setEnabled(getStorageType() == StorageType.FILE);
    }

    /**
     * Validates storage settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        getCurrentStorageProvider().validate();
    }

    /**
     * @return a {@link MemoryCacheAccessTokenSupplier} that provides the credential
     *         from the current storage provider.
     */
    public MemoryCacheAccessTokenSupplier createAccessTokenSupplier() {
        return getCurrentStorageProvider().createAccessTokenSupplier();
    }

    /**
     * Writes the given token cache string to the current storage provider.
     *
     * @param tokenCacheString
     * @throws IOException
     */
    public void writeTokenCache(final String tokenCacheString) throws IOException {
        getCurrentStorageProvider().writeTokenCache(tokenCacheString);
    }

    /**
     * Reads the given token cache string from the current storage provider.
     *
     * @return the token cache string from the current storage provider, or null if
     *         the current provider has nothing stored.
     * @throws IOException
     */
    public String readTokenCache() throws IOException {
        return getCurrentStorageProvider().readTokenCache();
    }

    private StorageProvider getCurrentStorageProvider() {
        switch (getStorageType()) {
        case MEMORY:
            return m_inMemoryStorage;
        case FILE:
            return m_fileStorage;
        case SETTINGS:
            return m_nodeSettingsStorage;
        default:
            throw new IllegalStateException();
        }
    }

    /**
     * Clears the memory cache from any credentials currently cached by the storage
     * providers in this instance.
     */
    public void clearMemoryTokenCache() {
        m_inMemoryStorage.clearMemoryTokenCache();
        m_fileStorage.clearMemoryTokenCache();
        m_nodeSettingsStorage.clearMemoryTokenCache();
    }

    /**
     * Configure file chooser model of file storage provider. Needed for
     * initialization in the node dialog.
     *
     * @param inSpecs
     * @param msgConsumer
     * @throws InvalidSettingsException
     */
    public void configureFileChoosersInModel(final PortObjectSpec[] inSpecs,
            final Consumer<StatusMessage> msgConsumer) throws InvalidSettingsException {
        m_fileStorage.configureFileChooserInModel(inSpecs, msgConsumer);
    }
}
