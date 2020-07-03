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

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.BaseAccessTokenSupplier;

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
     * @param nodeInstanceId
     */
    public StorageSettings(final String nodeInstanceId, final String authority) {
        m_inMemoryStorage = new InMemoryStorage(nodeInstanceId, authority);
        m_nodeSettingsStorage = new NodeSettingsStorage(nodeInstanceId, authority);
        m_fileStorage = new FileStorage(authority);

    }

    public SettingsModelString getStorageTypeModel() {
        return m_storageType;
    }

    /**
     * @return where to store the token cache.
     */
    public StorageType getStorageType() {
        return StorageType.valueOf(m_storageType.getStringValue());
    }

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

    public void clearCurrentStorage() throws IOException {
        switch (getStorageType()) {
        case MEMORY:
            m_inMemoryStorage.clear();
            break;
        case FILE:
            m_fileStorage.clear();
            break;
        case SETTINGS:
            m_nodeSettingsStorage.clear();
            break;
        }
    }

    public boolean isLoggedIn() throws IOException {
        try {
            final String tokenCacheString;

            switch (getStorageType()) {
            case MEMORY:
                tokenCacheString = m_inMemoryStorage.readTokenCache();
                break;
            case FILE:
                tokenCacheString = m_fileStorage.readTokenCache();
                break;
            case SETTINGS:
                tokenCacheString = m_nodeSettingsStorage.readTokenCache();
                break;
            default:
                throw new IllegalStateException();
            }

            return tokenCacheString != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void clearStorage() throws IOException {
        m_inMemoryStorage.clear();
        m_fileStorage.clear();
        m_nodeSettingsStorage.clear();
    }

    /**
     * Saves provider's settings into a given {@link NodeSettingsWO}.
     *
     * @param settings
     *            The settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_storageType.saveSettingsTo(settings);
        m_inMemoryStorage.saveSettingsTo(settings.addNodeSettings(KEY_MEMORY));
        m_fileStorage.saveSettingsTo(settings.addNodeSettings(KEY_FILE));
        m_nodeSettingsStorage.saveSettingsTo(settings.addNodeSettings(KEY_NODE_SETTINGS));

    }

    /**
     * Loads provider's settings from a given {@link NodeSettingsRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_storageType.loadSettingsFrom(settings);
        m_inMemoryStorage.loadSettingsFrom(settings.getNodeSettings(KEY_MEMORY));
        m_fileStorage.loadSettingsFrom(settings.getNodeSettings(KEY_FILE));
        m_nodeSettingsStorage.loadSettingsFrom(settings.getNodeSettings(KEY_NODE_SETTINGS));
    }

    /**
     * Validates storage settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        switch (getStorageType()) {
        case MEMORY:
            m_inMemoryStorage.validate();
            break;
        case FILE:
            m_fileStorage.validate();
            break;
        case SETTINGS:
            m_nodeSettingsStorage.validate();
            break;
        }
    }

    public BaseAccessTokenSupplier createAccessTokenSupplier() {
        switch (getStorageType()) {
        case MEMORY:
            return m_inMemoryStorage.createAccessTokenSupplier();
        case FILE:
            return m_fileStorage.createAccessTokenSupplier();
        case SETTINGS:
            return m_nodeSettingsStorage.createAccessTokenSupplier();
        default:
            throw new IllegalStateException();
        }
    }

    public void writeTokenCache(final String tokenCacheString) throws IOException {
        switch(getStorageType()) {
        case MEMORY:
            m_inMemoryStorage.writeTokenCache(tokenCacheString);
            break;
        case FILE:
            m_fileStorage.writeTokenCache(tokenCacheString);
            break;
        case SETTINGS:
            m_nodeSettingsStorage.writeTokenCache(tokenCacheString);
            break;
        }
    }

    public String readTokenCache() throws IOException {
        switch (getStorageType()) {
        case MEMORY:
            return m_inMemoryStorage.readTokenCache();
        case FILE:
            return m_fileStorage.readTokenCache();
        case SETTINGS:
            return m_nodeSettingsStorage.readTokenCache();
        default:
            throw new IllegalStateException();
        }
    }

    public void clearMemoryTokenCache() {
        m_inMemoryStorage.clearMemoryTokenCache();
        m_fileStorage.clearMemoryTokenCache();
        m_nodeSettingsStorage.clearMemoryTokenCache();
    }
}
