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

import java.io.IOException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelPassword;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.ext.microsoft.authentication.providers.MemoryCredentialCache;

/**
 * Concrete storage provider that stores an MSAL4J token cache string in node
 * settings.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
class NodeSettingsStorage implements StorageProvider {

    private static final String KEY_TOKEN_CACHE = "tokenCache";

    private static final String TOKEN_CACHE_ENCRYPTION_SECRET = "aeth*ie0ahw3kaiyooph;ue4nahMigh1eMou2ul3";

    /**
     * Holds a key that is unique to this node instance (even when the node is
     * copied it will have a different key). The key is used to store the token
     * cache in-memory.
     */
    private final String m_cacheKey;

    /**
     * Stores the token cache, when storing the token cache in the node settings.
     */
    private final SettingsModelString m_tokenCache = new SettingsModelPassword(KEY_TOKEN_CACHE,
            TOKEN_CACHE_ENCRYPTION_SECRET, "");

    /**
     * @param nodeInstanceId
     */
    public NodeSettingsStorage(final String nodeInstanceId) {
        m_cacheKey = "node-" + nodeInstanceId;
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_tokenCache.loadSettingsFrom(settings);

        if (!m_tokenCache.getStringValue().isEmpty()) {
            // we need to put the token cache into the MemoryTokenCache because
            // the port object must not save the actual token cache.
            MemoryCredentialCache.put(m_cacheKey, m_tokenCache.getStringValue());
        }
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_tokenCache.saveSettingsTo(settings);
    }

    @Override
    public void validate() throws InvalidSettingsException {
        // do nothing
    }

    @Override
    public void writeTokenCache(final String tokenCacheString) throws IOException {
        m_tokenCache.setStringValue(tokenCacheString);
    }

    @Override
    public String readTokenCache() throws IOException {
        final var tokenCacheString = m_tokenCache.getStringValue();
        if (tokenCacheString.isEmpty()) {
            return null;
        }
        return tokenCacheString;
    }

    @Override
    public void clear() throws IOException {
        m_tokenCache.setStringValue("");
        MemoryCredentialCache.remove(m_cacheKey);
    }

    @Override
    public void clearMemoryTokenCache() {
        MemoryCredentialCache.remove(m_cacheKey);
    }
}
