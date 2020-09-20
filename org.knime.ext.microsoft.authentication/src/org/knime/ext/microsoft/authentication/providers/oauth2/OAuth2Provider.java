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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;
import org.knime.ext.microsoft.authentication.port.oauth2.Scope;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProvider;

/**
 * Base class for auth providers implementing OAuth2 authentication using
 * msal4j.
 *
 * @author Alexander Bondaletov
 */
public abstract class OAuth2Provider implements MicrosoftAuthProvider {

    private static final String KEY_SCOPES = "scopes";

    /**
     * Added with KNIME AP 4.3 to support OAuth2 with Azure Blob Storage.
     */
    private static final String KEY_BLOB_STORAGE_ACCOUNT = "blobStorageAccount";

    private final SettingsModelStringArray m_scopes;
    private final SettingsModelString m_blobStorageAccount;

    /**
     * Creates new instance.
     *
     */
    public OAuth2Provider() {
        m_scopes = new SettingsModelStringArray(KEY_SCOPES,
                new String[] { Scope.SITES_READ_WRITE.getScope() });
        m_blobStorageAccount = new SettingsModelString(KEY_BLOB_STORAGE_ACCOUNT, "");
        m_blobStorageAccount.setEnabled(false);
    }

    /**
     * @return the scopes model
     */
    public SettingsModelStringArray getScopesModel() {
        return m_scopes;
    }

    /**
     * @return the model for the storage account required for
     *         {@link Scope#AZURE_BLOB_STORAGE}.
     */
    public SettingsModelString getBlobStorageAccountModel() {
        return m_blobStorageAccount;
    }

    /**
     * Returns the scopes as a set of strings. This method must be called if you
     * need the proper scope as a string, because it enriches the scope string for
     * {@link Scope#AZURE_BLOB_STORAGE} with the necessary storage account.
     *
     * @return the scopes as a set of strings.
     */
    public Set<String> getScopesStringSet() {
        return getScopesEnumSet().stream().map(scope -> {
            if (scope == Scope.AZURE_BLOB_STORAGE) {
                return String.format(scope.getScope(), m_blobStorageAccount.getStringValue());
            }
            return scope.getScope();
        }).collect(Collectors.toSet());
    }

    /**
     * Returns the scopes as a set of enums. Note that when you need the proper
     * scope as a string, then you should call {@link #getScopesStringSet()},
     * because it enriches the scope string for {@link Scope#AZURE_BLOB_STORAGE}
     * with the necessary storage account.
     *
     * @return the scopes as a set of enums.
     */
    public Set<Scope> getScopesEnumSet() {
        final List<Scope> scopeList = Arrays.stream(m_scopes.getStringArrayValue()) //
                .<Scope>map(
                        Scope::fromScope) //
                .collect(Collectors.toList());

        return EnumSet.copyOf(scopeList);
    }

    /**
     * Returns appropriate authority for the current provider.
     *
     * @return The authority.
     */
    protected abstract String getAuthority();

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_scopes.saveSettingsTo(settings);
        m_blobStorageAccount.saveSettingsTo(settings);
    }

    /**
     * Validates consistency of the current settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        String[] scopes = m_scopes.getStringArrayValue();
        if (scopes == null || scopes.length == 0) {
            throw new InvalidSettingsException("Scopes cannot be empty");
        }

        if (getScopesEnumSet().contains(Scope.AZURE_BLOB_STORAGE) && m_blobStorageAccount.getStringValue().isEmpty()) {
            throw new InvalidSettingsException("Storage account cannot be empty");
        }
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_scopes.loadSettingsFrom(settings);

        // Added with KNIME AP 4.3 to support OAuth2 with Azure Blob Storage.
        if (settings.containsKey(m_blobStorageAccount.getKey())) {
            m_blobStorageAccount.loadSettingsFrom(settings);
        }
    }
}
