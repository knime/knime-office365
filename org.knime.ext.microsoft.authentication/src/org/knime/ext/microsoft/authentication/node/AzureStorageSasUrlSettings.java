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
 *   2023-10-01 (Zkriya Rakhimberdiyev, Redfield SE): created
 */
package org.knime.ext.microsoft.authentication.node;

import java.net.MalformedURLException;
import java.net.URL;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.core.node.workflow.ICredentials;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.credentials.base.node.CredentialsSettings;

/**
 * Implementation of {@link DefaultNodeSettings} to specify Azure SAS URL
 * Credential.
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
@SuppressWarnings("restriction")
public class AzureStorageSasUrlSettings implements CredentialsSettings {

    /**
     * The name of the Credentials flow variable.
     */
    @Widget(title = "Service SAS URL (flow variable)", //
            description = """
                    Specifies the credentials flow variable with the Azure Service SAS URL.
                    Note that only <a href="https://learn.microsoft.com/en-us/azure/storage/common/storage-sas-overview#service-sas">
                    Service SAS</a> is supported. The SAS URL must delegate access to the Blob storage
                    service, or an object within.
                    """)
    @ChoicesWidget(choices = CredentialsFlowVarChoicesProvider.class, showNoneColumn = false)
    public String m_flowVariable;

    @Override
    public String flowVariableName() {
        return m_flowVariable;
    }

    /**
     * If a flow variable has been specified, this method validates that a password
     * is present in the flow variable. This method should be used during the
     * configure phase, to reduce logspam if the credentials flow variable is not
     * there yet.
     *
     * @param credsProvider
     *            Used to access the flow variable.
     * @throws InvalidSettingsException
     *             when a password was not present in the flow variable.
     */
    public void validateOnConfigure(final CredentialsProvider credsProvider) throws InvalidSettingsException {
        validateFlowVariableIsSet();
        if (retrieve(credsProvider).isPresent()) {
            validateSecret(credsProvider, "The selected credentials flow variable does not provide a password");
            validateSasUrl(credsProvider);
        }
    }

    private void validateSasUrl(final CredentialsProvider credsProvider) throws InvalidSettingsException {
        try {
            final var sasUrl = retrieve(credsProvider).map(ICredentials::getPassword).get(); // NOSONAR
            var url = new URL(sasUrl);

            if (!url.getProtocol().equals("https")) {
                throw new InvalidSettingsException("Invalid protocol: " + url.getProtocol() + ". Expected 'https'.");
            }

            String query = url.getQuery();
            if (query == null || query.isEmpty()) {
                throw new InvalidSettingsException("Query part of the URL is missing");
            }
        } catch (MalformedURLException ex) {
            throw new InvalidSettingsException(ex.getMessage(), ex);
        }
    }

    /**
     * This method validates both presence and validity of a credentials flow
     * variable. This method should be used during the execute phase.
     *
     * @param credsProvider
     *            Used to access the flow variable.
     * @throws InvalidSettingsException
     *             when flow variable was not present or invalid.
     */
    public void validateOnExecute(final CredentialsProvider credsProvider) throws InvalidSettingsException {
        validateFlowVariable(credsProvider);
        validateSecret(credsProvider, "The selected credentials flow variable does not provide a password");
        validateSasUrl(credsProvider);
    }
}
