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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.EnumSet;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.crypto.Encrypter;
import org.knime.core.util.crypto.IEncrypter;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeFactory;
import org.knime.ext.microsoft.authentication.providers.MemoryCredentialCache;
import org.knime.ext.microsoft.authentication.providers.oauth2.tokensupplier.MemoryCacheAccessTokenSupplier;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.defaultnodesettings.EnumConfig;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.FileOverwritePolicy;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.SettingsModelWriterFileChooser;
import org.knime.filehandling.core.defaultnodesettings.filechooser.writer.WritePathAccessor;
import org.knime.filehandling.core.defaultnodesettings.filtermode.SettingsModelFilterMode.FilterMode;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;

/**
 * Concrete storage provider that stores an MSAL4J token cache string in a file.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
class FileStorage implements StorageProvider {

    final static NodeLogger LOG = NodeLogger.getLogger(FileStorage.class);

    private static final String TOKEN_CACHE_ENCRYPTION_SECRET = "fu&Fu6aef7Eshoozu^inie6Oz8weeP1iucei6pei";

    /**
     * Path to file that stores the token cache, when storing the token cache in a
     * file.
     */
    private final SettingsModelWriterFileChooser m_file;

    private final String m_endpoint;
    private final String m_appId;

    private final String m_cacheKey;

    public FileStorage(final PortsConfiguration portsConfig, final String nodeInstanceId, final String endpoint,
            final String appId) {
        m_endpoint = endpoint;
        m_appId = appId;
        m_cacheKey = "file-" + nodeInstanceId;
        m_file = new SettingsModelWriterFileChooser(
                "token_cache_file", //
                portsConfig, //
                MicrosoftAuthenticationNodeFactory.FILE_SYSTEM_CONNECTION_PORT_NAME, //
                EnumConfig.create(FilterMode.FILE), //
                EnumConfig.create(FileOverwritePolicy.OVERWRITE));
    }

    /**
     * @return the file path settings model
     */
    public SettingsModelWriterFileChooser getFileModel() {
        return m_file;
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_file.loadSettingsFrom(settings);
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_file.saveSettingsTo(settings);
    }

    @Override
    public void validate() throws InvalidSettingsException {
        if (m_file.getLocation() == null) {
            throw new InvalidSettingsException("File path is not set");
        }
    }

    @Override
    public void writeTokenCache(final String tokenCacheString) throws IOException {
        final NodeModelStatusConsumer statusConsumer = new NodeModelStatusConsumer(
                EnumSet.of(MessageType.ERROR, MessageType.WARNING));

        final String encrypted = encrypt(tokenCacheString);

        try (final WritePathAccessor accessor = m_file.createWritePathAccessor()) {
            final FSPath path = accessor.getOutputPath(statusConsumer);
            statusConsumer.setWarningsIfRequired(LOG::warn);
            try (final OutputStream out = FSFiles.newOutputStream(path);) {

                out.write(encrypted.getBytes(Charset.forName("utf8")));
            }
        } catch (InvalidSettingsException ex) {
            throw new IOException(ex.getMessage(), ex);
        }

        MemoryCredentialCache.put(m_cacheKey, tokenCacheString);
    }

    private static String encrypt(final String plaintextString) throws IOException {
        try {
            return createEncrypter().encrypt(plaintextString);
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException
                | InvalidAlgorithmParameterException ex) {
            throw new IOException("Failed to encrypt token before saving to file", ex);
        }
    }

    private static String decrypt(final String encryptedString) throws IOException {
        try {
            return createEncrypter().decrypt(encryptedString);
        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException
                | InvalidAlgorithmParameterException ex) {
            throw new IOException("Failed to encrypt token before saving to file", ex);
        }
    }

    @Override
    public String readTokenCache() throws IOException {
        final NodeModelStatusConsumer statusConsumer = new NodeModelStatusConsumer(
                EnumSet.of(MessageType.ERROR, MessageType.WARNING));



        try (final WritePathAccessor accessor = m_file.createWritePathAccessor()) {
            final FSPath path = accessor.getOutputPath(statusConsumer);
            statusConsumer.setWarningsIfRequired(LOG::warn);

            final BasicFileAttributes attribs = Files.readAttributes(path, BasicFileAttributes.class);

            if (attribs.isDirectory()) {
                return null;
            }

            if (attribs.size() > 1000 * 1000) {
                throw new IOException(String.format("File %s is too large to plausibly store a token.",
                        path.toFSLocation().getPath()));
            }

            final String encryptedTokenCacheString;

            try (final InputStream in = FSFiles.newInputStream(path)) {

                final byte[] tokenCacheBytes = new byte[(int) attribs.size()];
                int bytesRead = 0;
                while (bytesRead < tokenCacheBytes.length) {
                    bytesRead += in.read(tokenCacheBytes, bytesRead, tokenCacheBytes.length - bytesRead);
                }

                encryptedTokenCacheString = new String(tokenCacheBytes, Charset.forName("utf8"));
            }

            final String tokenCacheString = decrypt(encryptedTokenCacheString);
            MemoryCredentialCache.put(m_cacheKey, tokenCacheString);
            return tokenCacheString;

        } catch (InvalidSettingsException ex) {
            throw new IOException(ex.getMessage(), ex);
        } catch (NoSuchFileException e) {
            return null; // NOSONAR we are intentionally returning null here and not rethrowing.
        }
    }

    @Override
    public MemoryCacheAccessTokenSupplier createAccessTokenSupplier() {
        return new MemoryCacheAccessTokenSupplier(m_endpoint, m_cacheKey, m_appId);
    }

    @Override
    public void clear() throws IOException {

        final NodeModelStatusConsumer statusConsumer = new NodeModelStatusConsumer(
                EnumSet.of(MessageType.ERROR, MessageType.WARNING));

        try (final WritePathAccessor accessor = m_file.createWritePathAccessor()) {
            final FSPath path = accessor.getOutputPath(statusConsumer);
            statusConsumer.setWarningsIfRequired(LOG::warn);

            FSFiles.deleteSafely(path);

        } catch (InvalidSettingsException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    @Override
    public void clearMemoryTokenCache() {
        MemoryCredentialCache.remove(m_cacheKey);
    }

    public void configureFileChooserInModel(final PortObjectSpec[] inSpecs,
            final Consumer<StatusMessage> msgConsumer) throws InvalidSettingsException {
        m_file.configureInModel(inSpecs, msgConsumer);
    }

    private static IEncrypter createEncrypter() {
        try {
            return new Encrypter(TOKEN_CACHE_ENCRYPTION_SECRET);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeySpecException ex) {
            throw new RuntimeException("Could not create encrypter: " + ex.getMessage(), ex);
        }
    }

}
