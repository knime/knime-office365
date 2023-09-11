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
 *   2023-09-09 (Zkriya Rakhimberdiyev, Redfield SE): created
 */
package org.knime.ext.sharepoint.lists.writer.node;

import java.util.ArrayList;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.LayoutGroup;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.widget.AsyncChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.IdAndText;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.WidgetHandlerException;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.lists.SharePointListUtils;
import org.knime.ext.sharepoint.lists.writer.node.SharepointListWriterNodeSettings.SharepointSiteSection;
import org.knime.ext.sharepoint.lists.writer.node.SharepointListWriterNodeSettings.SiteModeIsGroup;

import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.requests.DirectoryObjectCollectionWithReferencesPage;

/**
 * Group Site settings.
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
@SuppressWarnings("restriction")
class GroupSiteSettings implements LayoutGroup, DefaultNodeSettings {

    @Widget(title = "Group site", description = "Connect to the team site of a particular Office 365 group.")
    @Layout(SharepointSiteSection.TypeSwitcherContent.class)
    @Effect(signals = SiteModeIsGroup.class, type = EffectType.SHOW)
    @ChoicesWidget(choices = GroupSiteChoicesProvider.class)
    String m_id;

    void validate() throws InvalidSettingsException {
        if (StringUtils.isBlank(m_id)) {
            throw new InvalidSettingsException("Group is not selected.");
        }
    }

    static final class GroupSiteChoicesProvider implements ChoicesProvider, AsyncChoicesProvider {

        @Override
        public IdAndText[] choicesWithIdAndText(final DefaultNodeSettingsContext context) {
            try {
                final var credential = ((CredentialPortObjectSpec) context.getPortObjectSpecs()[0])
                        .resolveCredential(JWTCredential.class);
                final var client = SharePointListUtils.createClient(credential);

                final var objects = new ArrayList<DirectoryObject>();
                DirectoryObjectCollectionWithReferencesPage resp = client.me().transitiveMemberOf().buildRequest()
                        .get();
                objects.addAll(resp.getCurrentPage());
                while (resp.getNextPage() != null) {
                    resp = resp.getNextPage().buildRequest().get();
                    objects.addAll(resp.getCurrentPage());
                }

                return objects.stream().filter(o -> GraphApiUtil.GROUP_DATA_TYPE.equals(o.oDataType))
                        .map(Group.class::cast)
                        .map(g -> new IdAndText(g.id, Optional.ofNullable(g.displayName).orElse(g.id)))
                        .toArray(IdAndText[]::new);
            } catch (final Exception e) { // NOSONAR catch all exceptions here
                throw new WidgetHandlerException("Unable to retrieve group sites: " + e.getMessage());
            }
        }
    }
}
