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
package org.knime.ext.microsoft.authentication.port;

import javax.swing.JComponent;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.ext.microsoft.authentication.port.azure.storage.AzureSasTokenCredential;
import org.knime.ext.microsoft.authentication.port.azure.storage.AzureSharedKeyCredential;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;

/**
 * Abstract superclass for classes that provide access to credentials for
 * services in Microsoft Office 365/Azure.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public abstract class MicrosoftCredential {

    /**
     * Type of credentials.
     */
    public enum Type {
        /**
         * Indicates that the credentials are an OAuth2 access token.
         */
        OAUTH2_ACCESS_TOKEN,
        /**
         * Indicates that the credentials are Azure Storage shared key.
         */
        AZURE_SHARED_KEY,
        /**
         * Indicates that the credentials are Azure Storage SAS token.
         */
        AZURE_SAS_TOKEN;

        // more credential types will be supported in the future
    }

    private static final String KEY_TYPE = "type";

    private final Type m_type;

    /**
     * Creates a new credential.
     *
     * @param type
     *            The type of the credential.
     */
    protected MicrosoftCredential(final Type type) {
        m_type = type;
    }

    /**
     * @return the providerType
     */
    public Type getType() {
        return m_type;
    }

    /**
     * Saves the settings from the current instance to the given {@link ConfigWO}
     *
     * @param config
     *            The config.
     */
    public void saveSettings(final ConfigWO config) {
        config.addString(KEY_TYPE, m_type.name());
    }

    /**
     * Loads settings from the give {@link ConfigRO}.
     *
     * @param config
     *            The config
     * @throws InvalidSettingsException
     */
    static MicrosoftCredential loadFromSettings(final ConfigRO config) throws InvalidSettingsException {
        final var type = Type.valueOf(config.getString(KEY_TYPE, Type.OAUTH2_ACCESS_TOKEN.name()));

        switch (type) {
        case OAUTH2_ACCESS_TOKEN:
            return OAuth2Credential.loadFromSettings(config);
        case AZURE_SHARED_KEY:
            return AzureSharedKeyCredential.loadFromSettings(config);
        case AZURE_SAS_TOKEN:
            return AzureSasTokenCredential.loadFromSettings(config);
        default:
            throw new InvalidSettingsException("Unsupported credential type " + type);
        }
    }

    /**
     * @return The view component.
     */
    public abstract JComponent getView();

    /**
     * @return A short human readable summary
     */
    public abstract String getSummary();
}
