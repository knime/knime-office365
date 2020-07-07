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
package org.knime.ext.microsoft.authentication.node.auth;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.ext.microsoft.authentication.providers.AuthProviderType;
import org.knime.ext.microsoft.authentication.providers.MicrosoftAuthProvider;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.InteractiveAuthProvider;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage.MemoryTokenCache;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;


/**
 * Node settings for {@link MicrosoftAuthenticationNodeModel}.
 *
 * @author Alexander Bondaletov
 */
public class MicrosoftAuthenticationSettings {

    private static final String KEY_PROVIDER_TYPE = "providerType";

    private final SettingsModelString m_providerType;
    private final Map<AuthProviderType, MicrosoftAuthProvider> m_providers;

    /**
     * Creates new instance.
     *
     * @param nodeInstanceId
     */
    public MicrosoftAuthenticationSettings(final PortsConfiguration portsConfig, final String nodeInstanceId) {
        m_providerType = new SettingsModelString(KEY_PROVIDER_TYPE, "");

        m_providers = new EnumMap<>(AuthProviderType.class);
        for (AuthProviderType type : AuthProviderType.values()) {
            m_providers.put(type, type.createProvider(portsConfig, nodeInstanceId));
        }
    }

    /**
     * @return the providerType model
     */
    public SettingsModelString getProviderTypeModel() {
        return m_providerType;
    }

    /**
     * @return the providerType
     */
    public AuthProviderType getProviderType() {
        try {
            return AuthProviderType.valueOf(m_providerType.getStringValue());
        } catch (IllegalArgumentException e) {
            return AuthProviderType.INTERACTIVE;
        }
    }

    /**
     * Returns the provider for a given type.
     *
     * @param type
     *            The provider type
     * @return The provider.
     */
    public MicrosoftAuthProvider getProvider(final AuthProviderType type) {
        return m_providers.get(type);
    }

    /**
     * Returns the provider for a currently selected provider type.
     *
     * @return The provider.
     */
    public MicrosoftAuthProvider getCurrentProvider() {
        return getProvider(getProviderType());
    }

    /**
     * Clears any tokens that the providers have put into {@link MemoryTokenCache}.
     * Typically, this should be called when the node is reset or the workflow is
     * disposed.
     */
    public void clearMemoryTokenCache() {
        for (MicrosoftAuthProvider provider : m_providers.values()) {
            provider.clearMemoryTokenCache();
        }
    }

    /**
     * Saves settings into a given {@link NodeSettingsWO}.
     *
     * @param settings
     *            The settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        m_providerType.saveSettingsTo(settings);

        for (Entry<AuthProviderType, MicrosoftAuthProvider> e : m_providers.entrySet()) {
            NodeSettingsWO config = settings.addNodeSettings(e.getKey().name());
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
        m_providerType.validateSettings(settings);

        AuthProviderType type;
        try {
            String typeStr = settings.getString(m_providerType.getKey(), "");
            type = typeStr.isEmpty() ? AuthProviderType.INTERACTIVE : AuthProviderType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingsException(e);
        }

        m_providers.get(type).validateSettings(settings.getNodeSettings(type.name()));
    }

    /**
     * Loads settings from the given {@link NodeSettingsRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_providerType.loadSettingsFrom(settings);

        for (Entry<AuthProviderType, MicrosoftAuthProvider> e : m_providers.entrySet()) {
            e.getValue().loadSettingsFrom(settings.getNodeSettings(e.getKey().name()));
        }
    }

    /**
     * @param inSpecs
     * @param msgConsumer
     * @throws InvalidSettingsException
     */
    public void configureFileChoosersInModel(final PortObjectSpec[] inSpecs,
            final Consumer<StatusMessage> msgConsumer) throws InvalidSettingsException {
        ((InteractiveAuthProvider) getProvider(AuthProviderType.INTERACTIVE))
                .getStorageSettings()
                .configureFileChoosersInModel(inSpecs, msgConsumer);

    }
}
