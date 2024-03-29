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

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Consumer;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.credentials.base.CredentialCache;
import org.knime.credentials.base.CredentialPortObject;
import org.knime.credentials.base.oauth.api.AccessTokenCredential;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.ext.microsoft.authentication.credential.AzureStorageSasUrlCredential;
import org.knime.ext.microsoft.authentication.credential.AzureStorageSharedKeyCredential;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObjectSpec;
import org.knime.ext.microsoft.authentication.providers.AuthProviderType;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.InteractiveAuthProvider;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage.StorageType;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;

/**
 * Microsoft authentication node. Performs authentication using one of the
 * different methods and provides {@link CredentialPortObject}.
 *
 * @author Alexander Bondaletov
 */
@SuppressWarnings("deprecation")
class MicrosoftAuthenticationNodeModel extends NodeModel {

    private static final Consumer<StatusMessage> NOOP_STATUS_CONSUMER = s -> {
    };

    private final MicrosoftAuthenticationSettings m_settings;

    private final NodeModelStatusConsumer m_statusConsumer = new NodeModelStatusConsumer(
            EnumSet.of(MessageType.ERROR, MessageType.WARNING));

    private UUID m_credentialCacheKey;

    /**
     * Creates new instance.
     *
     * @param portsConfig
     * @param nodeInstanceId
     */
    MicrosoftAuthenticationNodeModel(final PortsConfiguration portsConfig, final String nodeInstanceId) {
        super(portsConfig.getInputPorts(), portsConfig.getOutputPorts());
        m_settings = new MicrosoftAuthenticationSettings(portsConfig, nodeInstanceId);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {

        var fileStorageSelected = false;
        if (m_settings.getProviderType() == AuthProviderType.INTERACTIVE) {
            final InteractiveAuthProvider provider = (InteractiveAuthProvider) m_settings.getCurrentProvider();
            if (provider.getStorageSettings().getStorageType() == StorageType.FILE) {
                fileStorageSelected = true;
            }
        }

        try {
            final Consumer<StatusMessage> msgConsumer = fileStorageSelected ? m_statusConsumer : NOOP_STATUS_CONSUMER;
            m_settings.configureFileChoosersInModel(inSpecs, msgConsumer);
        } catch (InvalidSettingsException e) {
            // only rethrow the InvalidSettingsException when we are actually using file
            // storage
            if (fileStorageSelected) {
                throw e;
            }
        }

        m_credentialCacheKey = null;
        return new PortObjectSpec[] { createSpec() };
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        var creds = m_settings.getCurrentProvider().getCredential(getCredentialsProvider());
        m_credentialCacheKey = CredentialCache.store(creds);
        return new PortObject[] { new MicrosoftCredentialPortObject(createSpec()) };
    }

    private MicrosoftCredentialPortObjectSpec createSpec() throws InvalidSettingsException {
        AuthProviderType type = m_settings.getProviderType();
        var credentialType = switch (type) {
            case AZURE_STORAGE_SAS -> AzureStorageSasUrlCredential.TYPE;
            case AZURE_STORAGE_SHARED_KEY -> AzureStorageSharedKeyCredential.TYPE;
            case CLIENT_SECRET -> AccessTokenCredential.TYPE;
            case INTERACTIVE -> JWTCredential.TYPE;
            case USERNAME_PASSWORD -> JWTCredential.TYPE;
            default -> throw new InvalidSettingsException("Unexpected provider type: " + type);
        };

        return new MicrosoftCredentialPortObjectSpec(credentialType, m_credentialCacheKey);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {

        final var needResetAndRexecuteMsg = "Credential not available anymore. Please re-execute this node.";

        if (m_settings.getProviderType() == AuthProviderType.INTERACTIVE) {
            final InteractiveAuthProvider provider = (InteractiveAuthProvider) m_settings.getCurrentProvider();
            switch (provider.getStorageSettings().getStorageType()) {
                case MEMORY:
                    setWarningMessage(
                            "Credential not available anymore. Please reset this node and login in the node dialog again.");
                    break;
                case FILE:
                case SETTINGS:
                    setWarningMessage(needResetAndRexecuteMsg);
                    break;
                default:
                    break;
            }
        } else {
            // USERNAME_PASSWORD, AZURE_STORAGE_SHARED_KEY, and AZURE_STORAGE_TOKEN
            setWarningMessage(needResetAndRexecuteMsg);
        }
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // no internals
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettingsTo(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.validateSettings(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.loadSettingsFrom(settings);
    }

    @Override
    protected void reset() {
        if (m_credentialCacheKey != null) {
            CredentialCache.delete(m_credentialCacheKey);
            m_credentialCacheKey = null;
        }
    }

    @Override
    protected final void onDispose() {
        reset();
        m_settings.clearMemoryTokenCache();
    }
}
