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
 *   2023-09-11 (Zkriya Rakhimberdiyev, Redfield SE): created
 */
package org.knime.ext.sharepoint.lists.writer.node;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.LayoutGroup;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.widget.AsyncChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.ChoicesUpdateHandler;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.IdAndText;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.DeclaringDefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.WidgetHandlerException;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.lists.SharePointListUtils;
import org.knime.ext.sharepoint.lists.writer.node.SharepointListWriterNodeSettings.IsUseSubsite;
import org.knime.ext.sharepoint.lists.writer.node.SharepointListWriterNodeSettings.SharepointSiteSection;
import org.knime.ext.sharepoint.settings.SiteMode;

import com.microsoft.graph.models.Site;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SiteCollectionPage;

/**
 * Subsite settings.
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
@SuppressWarnings("restriction")
class SubsiteSettings implements LayoutGroup, DefaultNodeSettings {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SubsiteSettings.class);

    @Widget(title = "Subsite", description = "Select subsite of the chosen SharePoint site.")
    @Layout(SharepointSiteSection.UseSubsiteContent.class)
    @Effect(signals = IsUseSubsite.class, type = EffectType.SHOW)
    @ChoicesWidget(choicesUpdateHandler = SubsiteUpdateHandler.class)
    String m_id;

    void validate() throws InvalidSettingsException {
        if (StringUtils.isBlank(m_id)) {
            throw new InvalidSettingsException("Subsite is not selected.");
        }
    }

    static class SubsiteUpdateHandler implements ChoicesUpdateHandler<SubsiteChoicesDependency>, AsyncChoicesProvider {

        @Override
        public IdAndText[] update(final SubsiteChoicesDependency settings,
                final DefaultNodeSettingsContext context) throws WidgetHandlerException {
            try {
                final var groupId = settings.m_groupSiteSettings.m_id;

                if (!settings.m_useSubsite
                        || settings.m_siteMode == SiteMode.WEB_URL && StringUtils.isEmpty(settings.m_siteURL)
                        || settings.m_siteMode == SiteMode.GROUP && StringUtils.isEmpty(groupId)) {
                    return new IdAndText[] {};
                }

                final var credential = ((CredentialPortObjectSpec) context.getPortObjectSpecs()[0])
                        .resolveCredential(JWTCredential.class);
                final var client = SharePointListUtils.createClient(credential);
                final var siteResolver = new SharepointSiteResolver(client, settings.m_siteMode, null,
                        settings.m_siteURL, groupId);

                return listSubsites(siteResolver.getParentSiteId(), client, "").stream().toArray(IdAndText[]::new);
            } catch (final Exception e) { // NOSONAR catch all exceptions here
                LOGGER.debug("Subsite fetch failed: " + e.getMessage(), e);
                throw new WidgetHandlerException(e.getMessage());
            }
        }
    }

    static class SubsiteChoicesDependency {

        @DeclaringDefaultNodeSettings(SharepointListWriterNodeSettings.class)
        GroupSiteSettings m_groupSiteSettings;

        @DeclaringDefaultNodeSettings(SharepointListWriterNodeSettings.class)
        boolean m_useSubsite;

        @DeclaringDefaultNodeSettings(SharepointListWriterNodeSettings.class)
        SiteMode m_siteMode;

        @DeclaringDefaultNodeSettings(SharepointListWriterNodeSettings.class)
        String m_siteURL;
    }

    private static List<IdAndText> listSubsites(final String siteId, final GraphServiceClient<?> client,
            final String prefix) {
        final var result = new ArrayList<IdAndText>();

        SiteCollectionPage resp = client.sites(siteId).sites().buildRequest().get();
        for (Site site : resp.getCurrentPage()) {
            result.addAll(processSite(site, client, prefix));
        }

        while (resp.getNextPage() != null) {
            resp = resp.getNextPage().buildRequest().get();
            for (Site site : resp.getCurrentPage()) {
                result.addAll(processSite(site, client, prefix));
            }
        }
        return result;
    }

    private static List<IdAndText> processSite(final Site site, final GraphServiceClient<?> client,
            final String prefix) {
        final var result = new ArrayList<IdAndText>();
        final var name = prefix + site.name;

        result.add(new IdAndText(site.id, name));
        result.addAll(listSubsites(site.id, client, prefix + site.name + " > "));
        return result;
    }
}
