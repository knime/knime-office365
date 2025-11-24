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
 *   14 Feb 2022 (Lars Schweikardt, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.lists.node.delete;

import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.ext.sharepoint.parameters.TimeoutParameters;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.updates.ValueReference;

/**
 * Node parameters for Delete SharePoint Online List.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
final class SharepointDeleteListNodeParameters implements NodeParameters {

    @ValueReference(SharepointSiteParameters.Ref.class)
    SharepointSiteParameters m_site = new SharepointSiteParameters();

    @Modification(ListModification.class)
    SharepointListParameters.Basic m_list = new SharepointListParameters.Basic();

    @PersistEmbedded
    TimeoutParameters m_timeout = new TimeoutParameters();

    @Override
    public void validate() throws InvalidSettingsException {
        m_site.validate();
        m_list.validate();
        m_timeout.validate();
    }

    static class ListModification extends SharepointListParameters.SharepointListParametersModification {
        @Override
        protected Optional<String> getExistingStringDescriptionPostfix() {
            return Optional.of("""
                    <br/>If the ID is set to an empty string using flow variables, the internal
                    name will be used instead to find the list.""");
        }
    }
}
