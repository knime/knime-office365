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

import java.util.Optional;
import java.util.UUID;

import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredentialPortObject;
import org.knime.filehandling.core.port.FileSystemPortObject;

/**
 * Factory class for Microsoft Authentication node.
 *
 * @author Alexander Bondaletov
 */
@SuppressWarnings("deprecation")
public class MicrosoftAuthenticationNodeFactory extends ConfigurableNodeFactory<MicrosoftAuthenticationNodeModel> {

    /**
     * Input port group name for optional file system connection.
     */
    public static final String FILE_SYSTEM_CONNECTION_PORT_NAME = "File System Connection";

    /**
     * This member variable is required to exclusively share a key for a static map
     * between the node model and the node dialog. The key should not be saved in
     * the node settings, as it is meant to be unique to this instance (!) of the
     * node, even if the node (incl. its settings) is copied.
     */
    private final String m_nodeInstanceId = UUID.randomUUID().toString();

    @SuppressWarnings("deprecation")
    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var builder = new PortsConfigurationBuilder();
        builder.addOptionalInputPortGroup(FILE_SYSTEM_CONNECTION_PORT_NAME, FileSystemPortObject.TYPE);
        builder.addFixedOutputPortGroup("Credential", MicrosoftCredentialPortObject.TYPE);
        return Optional.of(builder);
    }

    @Override
    protected MicrosoftAuthenticationNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new MicrosoftAuthenticationNodeModel(
                creationConfig.getPortConfig().orElseThrow(IllegalStateException::new), //
                m_nodeInstanceId);
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<MicrosoftAuthenticationNodeModel> createNodeView(final int viewIndex,
            final MicrosoftAuthenticationNodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return new MicrosoftAuthenticationNodeDialog(
                creationConfig.getPortConfig().orElseThrow(IllegalStateException::new), //
                m_nodeInstanceId);
    }
}
