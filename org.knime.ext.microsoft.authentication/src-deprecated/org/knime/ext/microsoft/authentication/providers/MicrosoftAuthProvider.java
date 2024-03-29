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
 *   2020-06-23 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers;

import java.io.IOException;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.credentials.base.Credential;
import org.knime.ext.microsoft.authentication.node.auth.MicrosoftAuthenticationNodeDialog;

/**
 * Base interface for auth providers implementing different authentication
 * methods.
 *
 * @author Alexander Bondaletov
 */
public interface MicrosoftAuthProvider {

    /**
     * Performs authentication and returns the result in a form of
     * {@link Credential} object.
     *
     * @param credentialsProvider
     *            A provider for workflow credentials. Only required by certain
     *            authentication providers.
     *
     * @return The Microsoft connection object.
     * @throws IOException
     */
    public Credential getCredential(final CredentialsProvider credentialsProvider) throws IOException;

    /**
     * Creates editor component for the provider.
     *
     * @param parent
     *            The node dialog.
     * @param credentialsSupplier
     *            The supplier of {@link CredentialsProvider} (required by flow
     *            variable dialog component to list all credentials flow variables).
     * @return The editor component.
     */
    public MicrosoftAuthProviderEditor createEditor(final MicrosoftAuthenticationNodeDialog parent,
            final Supplier<CredentialsProvider> credentialsSupplier);

    /**
     * Saves provider's settings into a given {@link NodeSettingsWO}.
     *
     * @param settings
     *            The settings.
     */
    public void saveSettingsTo(final NodeSettingsWO settings);

    /**
     * Validates settings stored in a give {@link NodeSettingsRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException;

    /**
     * Loads provider's settings from a given {@link NodeSettingsRO}.
     *
     * @param settings
     *            The settings.
     * @throws InvalidSettingsException
     */
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException;

    /**
     * Clears any tokens that this provider has put into {@link MemoryCredentialCache}.
     */
    default void clearMemoryTokenCache() {
        // default no-op implementation
    }
}
