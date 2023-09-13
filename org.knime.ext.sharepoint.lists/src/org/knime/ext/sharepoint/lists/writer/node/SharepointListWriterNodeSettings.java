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

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.After;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.rule.OneOfEnumCondition;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Signal;
import org.knime.core.webui.node.dialog.defaultdialog.rule.TrueCondition;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.lists.SharePointListUtils;
import org.knime.ext.sharepoint.lists.writer.ListOverwritePolicy;
import org.knime.ext.sharepoint.lists.writer.SharepointListWriterClientParameters;
import org.knime.ext.sharepoint.settings.SiteMode;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Settings for the SharePoint List Writer node.
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
@SuppressWarnings("restriction")
public class SharepointListWriterNodeSettings implements DefaultNodeSettings {

    @Section(title = "SharePoint Site")
    interface SharepointSiteSection {
        interface TypeSwitcher {
        }

        @After(TypeSwitcher.class)
        interface TypeSwitcherContent {
        }

        @After(TypeSwitcherContent.class)
        interface UseSubsite {
        }

        @After(UseSubsite.class)
        interface UseSubsiteContent {
        }
    }

    @Section(title = "SharePoint List")
    @After(SharepointSiteSection.class)
    interface SharepointListSection {
        interface List {
        }

        @After(List.class)
        interface OverwritePolicy {
        }
    }

    static class SiteModeIsWebURL extends OneOfEnumCondition<SiteMode> {
        @Override
        public SiteMode[] oneOf() {
            return new SiteMode[] { SiteMode.WEB_URL };
        }
    }

    static class SiteModeIsGroup extends OneOfEnumCondition<SiteMode> {
        @Override
        public SiteMode[] oneOf() {
            return new SiteMode[] { SiteMode.GROUP };
        }
    }

    interface IsUseSubsite {
    }

    @Widget(description = "SharePoint site to use.", hideTitle = true)
    @Layout(SharepointSiteSection.TypeSwitcher.class)
    @Signal(condition = SiteModeIsWebURL.class)
    @Signal(condition = SiteModeIsGroup.class)
    @ValueSwitchWidget
    SiteMode m_siteMode = SiteMode.ROOT;

    @Widget(title = "Site URL", description = "Specify the web URL of a SharePoint site.")
    @Layout(SharepointSiteSection.TypeSwitcherContent.class)
    @Effect(signals = SiteModeIsWebURL.class, type = EffectType.SHOW)
    String m_siteURL;

    GroupSiteSettings m_groupSiteSettings = new GroupSiteSettings();

    @Widget(title = "Use subsite", description = "If checked, "
            + "then connect to a (nested) subsite of the SharePoint site specified above.")
    @Layout(SharepointSiteSection.UseSubsite.class)
    @Signal(id = IsUseSubsite.class, condition = TrueCondition.class)
    boolean m_useSubsite;

    SubsiteSettings m_subsiteSettings = new SubsiteSettings();

    @Widget(title = "List", description = "Enter the name of a new list you want to create.")
    @Layout(SharepointListSection.List.class)
    String m_listName;

    @Widget(title = "If list exists", description = "Overwrite policy if list exists.")
    @Layout(SharepointListSection.OverwritePolicy.class)
    @ValueSwitchWidget
    OverwritePolicy m_overwritePolicy = OverwritePolicy.FAIL;

    enum OverwritePolicy {
        @Label("Fail") //
        FAIL, //
        @Label("Overwrite") //
        OVERWRITE;
    }

    void validate() throws InvalidSettingsException {
        if (m_siteMode == SiteMode.WEB_URL && StringUtils.isEmpty(m_siteURL)) {
            throw new InvalidSettingsException("Web URL is not specified.");
        }
        if (m_siteMode == SiteMode.GROUP) {
            m_groupSiteSettings.validate();
        }
        if (m_useSubsite) {
            m_subsiteSettings.validate();
        }
        if (StringUtils.isEmpty(m_listName)) {
            throw new InvalidSettingsException("No list entered. Please enter a list name.");
        }
    }

    @JsonIgnore
    SharepointListWriterClientParameters getClientParameters(final PortObjectSpec spec)
            throws IOException, NoSuchCredentialException {
        final var credential = ((CredentialPortObjectSpec) spec).resolveCredential(JWTCredential.class);

        final var client = SharePointListUtils.createClient(credential);
        final var siteResolver = new SharepointSiteResolver(client, m_siteMode, m_subsiteSettings.m_id, m_siteURL,
                m_groupSiteSettings.m_id);

        final var overwritePolicy = ListOverwritePolicy.valueOf(m_overwritePolicy.name());
        return new SharepointListWriterClientParameters(overwritePolicy, siteResolver, null, m_listName, client);
    }
}
