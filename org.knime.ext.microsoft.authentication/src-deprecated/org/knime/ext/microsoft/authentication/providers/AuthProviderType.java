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
 *   2020-06-05 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers;

import java.util.function.BiFunction;

import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeFactory;
import org.knime.ext.microsoft.authentication.providers.azure.storage.sas.AzureStorageSasTokenAuthProvider;
import org.knime.ext.microsoft.authentication.providers.azure.storage.sharedkey.AzureSharedKeyAuthProvider;
import org.knime.ext.microsoft.authentication.providers.oauth2.application.ApplicationPermissionsOAuth2Provider;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.InteractiveAuthProvider;
import org.knime.ext.microsoft.authentication.providers.oauth2.userpass.UsernamePasswordAuthProvider;


/**
 * Microsoft auth provider types.
 *
 * @author Alexander Bondaletov
 */
public enum AuthProviderType {
    /**
     * Interactive provider.
     */
    INTERACTIVE("Interactive authentication", InteractiveAuthProvider::new),
    /**
     * Username and password authentication provider.
     */
    USERNAME_PASSWORD("Username/password authentication", UsernamePasswordAuthProvider::new),
    /**
     * Application permissions authentication provider.
     */
    CLIENT_SECRET("Client/Application secret authentication", ApplicationPermissionsOAuth2Provider::new),
    /**
     * Azure Storage shared key authentication provider.
     */
    AZURE_STORAGE_SHARED_KEY("Shared key authentication (Azure Storage only)", AzureSharedKeyAuthProvider::new),
    /**
     * Azure Storage SAS token authentication provider.
     */
    AZURE_STORAGE_SAS("Shared access signature (SAS) authentication (Azure Storage only)",
            AzureStorageSasTokenAuthProvider::new);


    private String m_title;
    private BiFunction<PortsConfiguration, String, MicrosoftAuthProvider> m_createProvider;

    private AuthProviderType(final String title,
            final BiFunction<PortsConfiguration, String, MicrosoftAuthProvider> createProvider) {
        m_title = title;
        m_createProvider = createProvider;

    }

    @Override
    public String toString() {
        return m_title;
    }

    /**
     * Creates {@link MicrosoftAuthProvider} instance of a current type.
     *
     * @param portsConfig
     *            Port configuration of the node.
     * @param nodeInstanceId
     *            Instance id of the node (see
     *            {@link MicrosoftAuthenticationNodeFactory}.
     * @return {@link MicrosoftAuthProvider} instance.
     */
    public MicrosoftAuthProvider createProvider(final PortsConfiguration portsConfig, final String nodeInstanceId) {
        return m_createProvider.apply(portsConfig, nodeInstanceId);
    }
}
