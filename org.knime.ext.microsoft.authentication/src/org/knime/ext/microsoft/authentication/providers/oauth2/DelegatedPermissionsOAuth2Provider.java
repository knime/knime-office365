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
 *   2020-06-04 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers.oauth2;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.ext.microsoft.authentication.port.oauth2.Scope;

/**
 * Base class for auth providers implementing OAuth2 authentication using
 * delegated permissions.
 *
 * @author Alexander Bondaletov
 */
public abstract class DelegatedPermissionsOAuth2Provider extends OAuth2Provider {

    private static final String KEY_SCOPES = "scopes";

    /**
     * Added with KNIME AP 4.3 to support OAuth2 with Azure Blob Storage.
     */
    private static final String KEY_BLOB_STORAGE_ACCOUNT = "blobStorageAccount";

    /**
     * Added with KNIME AP 4.5 to support OAuth2 with manually entered scopes.
     */
    private static final String KEY_OTHER_SCOPES = "otherScopes";

    /**
     * Added with KNIME AP 4.6 to support custom endpoint URL
     */
    private static final String KEY_USE_CUSTOM_AUTH_ENDPOINT = "useCustomEndpoint";
    private static final String KEY_CUSTOM_AUTH_ENDPOINT_URL = "customEndpoint";

    private static final String KEY_USE_CUSTOM_APP_ID = "useCustomAppId";
    private static final String KEY_CUSTOM_APP_ID = "customAppId";

    private final SettingsModelString m_blobStorageAccount;
    private final SettingsModelString m_otherScopes;

    private final SettingsModelBoolean m_useCustomEndpoint;
    private final SettingsModelString m_customEndpointUrl;
    private final SettingsModelBoolean m_useCustomAppId;
    private final SettingsModelString m_customAppId;

    /**
     * Creates new instance.
     */
    protected DelegatedPermissionsOAuth2Provider() {
        super(KEY_SCOPES, Scope.SITES_READ_WRITE);
        m_blobStorageAccount = new SettingsModelString(KEY_BLOB_STORAGE_ACCOUNT, "");
        m_blobStorageAccount.setEnabled(false);
        m_otherScopes = new SettingsModelString(KEY_OTHER_SCOPES, "");
        m_otherScopes.setEnabled(false);
        m_useCustomEndpoint = new SettingsModelBoolean(KEY_USE_CUSTOM_AUTH_ENDPOINT, false);
        m_customEndpointUrl = new SettingsModelString(KEY_CUSTOM_AUTH_ENDPOINT_URL, "");
        m_useCustomAppId = new SettingsModelBoolean(KEY_USE_CUSTOM_APP_ID, false);
        m_customAppId = new SettingsModelString(KEY_CUSTOM_APP_ID, "");
    }

    /**
     * @return the model for the storage account required for
     *         {@link Scope#AZURE_BLOB_STORAGE}.
     */
    public SettingsModelString getBlobStorageAccountModel() {
        return m_blobStorageAccount;
    }

    /**
     * @return the model for the manually entered scopes.
     */
    public SettingsModelString getOtherScopesModel() {
        return m_otherScopes;
    }

    /**
     * @return the useCustomEndpoint model
     */
    public SettingsModelBoolean getUseCustomEndpointModel() {
        return m_useCustomEndpoint;
    }

    /**
     * @return the customEndpointUrl model
     */
    public SettingsModelString getCustomEndpointUrlModel() {
        return m_customEndpointUrl;
    }

    /**
     * @return the useCustomAppId model
     */
    protected SettingsModelBoolean getUseCustomAppIdModel() {
        return m_useCustomAppId;
    }

    /**
     * @return the customAppId model
     */
    protected SettingsModelString getCustomAppIdModel() {
        return m_customAppId;
    }

    /**
     * Returns appropriate OAuth2 authorization endpoint for the current provider.
     *
     * @return The endpoint URL as a string.
     */
    protected String getEndpoint() {
        if (m_useCustomEndpoint.getBooleanValue()) {
            return m_customEndpointUrl.getStringValue();
        } else {
            return getDefaultEndpoint();
        }
    }

    /**
     * Returns the Application (client) ID.
     *
     * @return The application id as a string.
     */
    protected String getAppId() {
        if (m_useCustomAppId.getBooleanValue()) {
            return m_customAppId.getStringValue();
        } else {
            return MSALUtil.DEFAULT_APP_ID;
        }
    }

    @Override
    public Set<String> getScopesStringSet() {
        final Set<String> scopeStrings = new HashSet<>();
        final Set<Scope> scopes = getScopesEnumSet();
        for (Scope scope : scopes) {
            if (scope == Scope.AZURE_BLOB_STORAGE) {
                scopeStrings.add(String.format(scope.getScope(), m_blobStorageAccount.getStringValue()));
            } else if (scope == Scope.OTHERS) {
                // get the unescaped string and split it into lines
                m_otherScopes.getJavaUnescapedStringValue() //
                        .lines() //
                        .map(String::trim) //
                        .filter(StringUtils::isNotBlank) //
                        .forEach(scopeStrings::add);
            } else {
                scopeStrings.add(scope.getScope());
            }
        }
        return scopeStrings;
    }

    /**
     * Returns the default endpoint for the current provider.
     *
     * @return The endpoint.
     */
    protected abstract String getDefaultEndpoint();

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        m_blobStorageAccount.saveSettingsTo(settings);
        m_otherScopes.saveSettingsTo(settings);
        m_useCustomEndpoint.saveSettingsTo(settings);
        m_customEndpointUrl.saveSettingsTo(settings);
        m_useCustomAppId.saveSettingsTo(settings);
        m_customAppId.saveSettingsTo(settings);
    }

    /**
     * Validates consistency of the current settings.
     *
     * @throws InvalidSettingsException
     */
    @Override
    public void validate() throws InvalidSettingsException {
        super.validate();

        if (getScopesEnumSet().contains(Scope.AZURE_BLOB_STORAGE) && m_blobStorageAccount.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Storage account cannot be empty");
        }

        if (getScopesEnumSet().contains(Scope.OTHERS) && m_otherScopes.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Other scopes list cannot be empty");
        }

        if (m_useCustomEndpoint.getBooleanValue() && m_customEndpointUrl.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Custom OAuth authorization endpoint URL must not be empty");
        }
        if (m_useCustomAppId.getBooleanValue() && m_customAppId.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Custom Application (client) ID must not be empty");
        }
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.loadSettingsFrom(settings);

        // Added with KNIME AP 4.3 to support OAuth2 with Azure Blob Storage.
        if (settings.containsKey(m_blobStorageAccount.getKey())) {
            m_blobStorageAccount.loadSettingsFrom(settings);
        }

        // Added with KNIME AP 4.5 to support OAuth2 with manually entered scopes.
        if (settings.containsKey(m_otherScopes.getKey())) {
            m_otherScopes.loadSettingsFrom(settings);
        }

        // Added with KNIME AP 4.6 to support custom endpoint URL
        if (settings.containsKey(KEY_USE_CUSTOM_AUTH_ENDPOINT)) {
            m_useCustomEndpoint.loadSettingsFrom(settings);
            m_customEndpointUrl.loadSettingsFrom(settings);
        }
        if (settings.containsKey(KEY_USE_CUSTOM_APP_ID)) {
            m_useCustomAppId.loadSettingsFrom(settings);
            m_customAppId.loadSettingsFrom(settings);
        }
    }
}
