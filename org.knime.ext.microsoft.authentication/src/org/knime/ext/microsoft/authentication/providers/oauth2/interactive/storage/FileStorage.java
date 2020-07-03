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
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.FileUtil;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.BaseAccessTokenSupplier;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.FileAccessTokenSupplier;

import com.microsoft.aad.msal4j.MsalClientException;

/**
 * Concrete storage provider that stores an MSAL4J token cache string in a file.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
class FileStorage implements StorageProvider {

    private static final String KEY_FILE_PATH = "filePath";

    /**
     * Path to file that stores the token cache, when storing the token cache in a
     * file.
     */
    private final SettingsModelString m_filePath = new SettingsModelString(KEY_FILE_PATH, "");

    private final String m_authority;

    public FileStorage(final String authority) {
        m_authority = authority;
    }

    /**
     * @return the file path settings model
     */
    public SettingsModelString getFilePathModel() {
        return m_filePath;
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_filePath.loadSettingsFrom(settings);
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_filePath.saveSettingsTo(settings);
    }

    @Override
    public void validate() throws InvalidSettingsException {
        if (m_filePath.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("File path is not set");
        }
    }

    @Override
    public void writeTokenCache(final String tokenCacheString) throws IOException {
        // FIXME: Replace with new file handling
        try {
            Files.write(FileUtil.resolveToPath(FileUtil.toURL(m_filePath.getStringValue())),
                    tokenCacheString.getBytes(Charset.forName("utf8")));
        } catch (InvalidPathException | URISyntaxException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    @Override
    public String readTokenCache() throws IOException {
        try {
            // FIXME: Replace with new file handling
            final Path filePath = FileUtil.resolveToPath(FileUtil.toURL(m_filePath.getStringValue()));
            if (!Files.exists(filePath)) {
                return null;
            }

            return new String(Files.readAllBytes(filePath), Charset.forName("utf8"));
        } catch (InvalidPathException | MsalClientException | URISyntaxException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    @Override
    public BaseAccessTokenSupplier createAccessTokenSupplier() {
        return new FileAccessTokenSupplier(m_authority, m_filePath.getStringValue());
    }

    @Override
    public void clear() throws IOException {
        try {
            final Path file = FileUtil.resolveToPath(FileUtil.toURL(m_filePath.getStringValue()));
            Files.deleteIfExists(file);
        } catch (InvalidPathException | URISyntaxException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    @Override
    public void clearMemoryTokenCache() {
        // nothing to do
    }
}
