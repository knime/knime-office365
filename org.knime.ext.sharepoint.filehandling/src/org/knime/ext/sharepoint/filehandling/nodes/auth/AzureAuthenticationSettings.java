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
package org.knime.ext.sharepoint.filehandling.nodes.auth;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.config.Config;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;
import org.knime.ext.sharepoint.filehandling.auth.data.AzureConnection;
import org.knime.ext.sharepoint.filehandling.auth.data.ConfigurableLocationStorage;
import org.knime.ext.sharepoint.filehandling.auth.providers.AbstractAuthProvider;
import org.knime.ext.sharepoint.filehandling.auth.providers.AuthProviderType;
import org.knime.ext.sharepoint.filehandling.auth.providers.InteractiveAuthProvider;
import org.knime.ext.sharepoint.filehandling.auth.providers.UsernamePasswordAuthProvider;

/**
 * Node settings for {@link AzureAuthenticationNodeModel}.
 *
 * @author Alexander Bondaletov
 */
public class AzureAuthenticationSettings {

    private final AzureConnection m_connection;
    private final Map<AuthProviderType, AbstractAuthProvider> m_providers;
    
    /**
     * Creates new instance.
     */
    public AzureAuthenticationSettings() {
        m_connection = new AzureConnection();

        m_providers = new EnumMap<>(AuthProviderType.class);
        m_providers.put(AuthProviderType.INTERACTIVE, new InteractiveAuthProvider(m_connection));
        m_providers.put(AuthProviderType.USERNAME_PASSWORD, new UsernamePasswordAuthProvider(m_connection));
    }

    /**
     * @return the providerType model
     */
    public SettingsModelString getProviderTypeModel() {
        return m_connection.getProviderTypeModel();
    }

    /**
     * @return the providerType
     */
    public AuthProviderType getProviderType() {
        return m_connection.getProviderType();
    }

    /**
     * Returns the provider for a given type.
     *
     * @param type
     *            The provider type
     * @return The provider.
     */
    public AbstractAuthProvider getProvider(final AuthProviderType type) {
        return m_providers.get(type);
    }

    /**
     * Returns the provider for a currently selected provider type.
     *
     * @return The provider.
     */
    public AbstractAuthProvider getCurrentProvider() {
        return getProvider(getProviderType());
    }

    /**
     * @return the storage location settings.
     */
    public ConfigurableLocationStorage getStorageLocation() {
        return m_connection.getStorageLocation();
    }

    /**
     * @return the scopes model.
     */
    public SettingsModelStringArray getScopesModel() {
        return m_connection.getScopesModel();
    }

    /**
     * Saves settings into a given {@link NodeSettingsWO}.
     *
     * @param settings
     *            The settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_connection.saveSettings(settings);

        for (Entry<AuthProviderType, AbstractAuthProvider> e : m_providers.entrySet()) {
            Config config = settings.addConfig(e.getKey().name());
            e.getValue().saveSettingsTo(config);
        }
    }

    /**
     * Validates settings stored in the given {@link NodeSettingsRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        AuthProviderType type = getProviderType();
        m_providers.get(type).validateSettings(settings.getConfig(type.name()));

        AzureAuthenticationSettings temp = new AzureAuthenticationSettings();
        temp.loadSettingsFrom(settings);
        temp.validate();
    }

    /**
     * Validates current settings consistency.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        m_connection.validate();
    }

    /**
     * Loads settings from the given {@link NodeSettingsRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_connection.loadSettings(settings);

        for (Entry<AuthProviderType, AbstractAuthProvider> e : m_providers.entrySet()) {
            e.getValue().loadSettingsFrom(settings.getConfig(e.getKey().name()));
        }
    }

    /**
     * @return the azure connection object.
     */
    public AzureConnection getConnection() {
        return m_connection;
    }

}
