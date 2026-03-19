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
 *   2020-05-02 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.node;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FSConnectionProvider;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelectionWidget;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.SingleFileSelectionMode;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.WithCustomFileSystem;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.CustomValidation;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.SimpleValidation;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnection;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFSConnectionConfig;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFileSystem;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.ext.sharepoint.parameters.TimeoutParameters;
import org.knime.ext.sharepoint.parameters.TimeoutsSection;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueReference;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Node parameters for the SharePoint Connector node.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
final class SharepointConnectionNodeParameters implements NodeParameters {

    @Section(title = "File System")
    @Before(TimeoutsSection.class)
    interface FileSystemSection {
    }

    @ValueReference(SharepointSiteParameters.Ref.class)
    SharepointSiteParameters m_site = new SharepointSiteParameters();

    @Widget(title = "Working directory", description = """
            The working directory of the resulting file system connection. The working
            directory must be specified as an absolute path that starts with '/'. A working directory allows
            downstream nodes to access files/folders using relative paths. The default working directory
            is the virtual root "/", under which all the document libraries are located.
            """)
    @Layout(FileSystemSection.class)
    @ValueReference(WorkingDirectoryRef.class)
    @FileSelectionWidget(value = SingleFileSelectionMode.FOLDER)
    @WithCustomFileSystem(connectionProvider = SharepointFSConnectionProvider.class)
    @CustomValidation(WorkingDirectoryValidator.class)
    @Persist(configKey = "workingDirectory")
    String m_workingDirectory = SharepointFileSystem.PATH_SEPARATOR;

    interface WorkingDirectoryRef extends ParameterReference<String> {
    }

    static class WorkingDirectoryValidator extends SimpleValidation<String> {

        @Override
        public void validate(final String currentValue) throws InvalidSettingsException {
            validateWorkingDirectory(currentValue);
        }
    }

    @PersistEmbedded
    @ValueReference(TimeoutParameters.Ref.class)
    TimeoutParameters m_timeout = new TimeoutParameters();

    @Override
    public void validate() throws InvalidSettingsException {
        m_site.validate();
        validateWorkingDirectory(m_workingDirectory);
        m_timeout.validate();
    }

    /**
     * Create the file system connection config from these parameters.
     *
     * @param authProvider
     *            The authentication provider.
     * @return The SharepointFSConnectionConfig.
     */
    SharepointFSConnectionConfig toFSConnectionConfig(final IAuthenticationProvider authProvider) {
        final var config = new SharepointFSConnectionConfig(m_workingDirectory, authProvider);
        config.setReadTimeOut(Duration.ofMillis(m_timeout.getReadTimeoutMillis()));
        config.setConnectionTimeOut(Duration.ofMillis(m_timeout.getConnectionTimeoutMillis()));
        config.setMode(m_site.m_mode);
        config.setGroup(m_site.getGroupSite());
        config.setWebURL(m_site.m_webUrl);
        final var subsite = m_site.getSubSite();
        if (!subsite.isEmpty()) {
            config.setSubsite(subsite);
        }
        return config;
    }

    static final class SharepointFSConnectionProvider implements StateProvider<FSConnectionProvider> {

        private Supplier<SharepointSiteParameters> m_site;
        private Supplier<TimeoutParameters> m_timeouts;
        private Supplier<String> m_workingDirectory;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_site = initializer.computeFromValueSupplier(SharepointSiteParameters.Ref.class);
            m_workingDirectory = initializer.computeFromValueSupplier(WorkingDirectoryRef.class);
            m_timeouts = initializer.computeFromValueSupplier(TimeoutParameters.Ref.class);
        }

        @Override
        public FSConnectionProvider computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {
            return () -> { // NOSONAR longer lambda acceptable
                try {
                    final var credSpec = (CredentialPortObjectSpec) parametersInput.getInPortSpec(0)
                            .orElseThrow(() -> new IOException("Credential port not connected"));

                    final var authProvider = GraphCredentialUtil.createAuthenticationProvider(credSpec);

                    var workingDir = m_workingDirectory.get();
                    if (workingDir == null || workingDir.isBlank()) {
                        workingDir = SharepointFileSystem.PATH_SEPARATOR;
                    }

                    final var params = new SharepointConnectionNodeParameters();
                    params.m_site = m_site.get();
                    params.m_timeout = m_timeouts.get();
                    params.m_workingDirectory = workingDir;

                    params.validate();
                    return new SharepointFSConnection(params.toFSConnectionConfig(authProvider));
                } catch (NoSuchCredentialException | IOException ex) {
                    throw ExceptionUtil.wrapAsIOException(ex);
                }
            };
        }
    }

    @SuppressWarnings("null")
    private static void validateWorkingDirectory(final String workingDirectory) throws InvalidSettingsException {
        CheckUtils.checkSetting(workingDirectory != null && !workingDirectory.isBlank(),
                "Working directory must be specified.");
        CheckUtils.checkSetting(workingDirectory.startsWith(SharepointFileSystem.PATH_SEPARATOR),
                "Working directory must be an absolute path that starts with '%s'.",
                SharepointFileSystem.PATH_SEPARATOR);
    }

}
