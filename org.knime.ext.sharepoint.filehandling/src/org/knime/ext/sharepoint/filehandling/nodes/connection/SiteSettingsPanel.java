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
 *   2020-05-17 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.nodes.connection;

import static java.util.stream.Collectors.toList;

import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.ext.sharepoint.filehandling.GraphApiUtil;
import org.knime.ext.sharepoint.filehandling.nodes.connection.LoadedItemsSelector.IdComboboxItem;
import org.knime.ext.sharepoint.filehandling.nodes.connection.SharepointConnectionSettings.SiteMode;
import org.knime.ext.sharepoint.filehandling.nodes.connection.SharepointConnectionSettings.SiteSettings;

import com.microsoft.graph.models.extensions.DirectoryObject;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.Site;
import com.microsoft.graph.requests.extensions.IDirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.extensions.ISiteCollectionPage;

/**
 * Editor component for the {@link SiteSettings} class.
 *
 * @author Alexander Bondaletov
 */
public class SiteSettingsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final SiteSettings m_settings;
    private IGraphServiceClient m_client;

    private DialogComponentString m_siteInput;
    private LoadedItemsSelector m_groupSelector;
    private LoadedItemsSelector m_subsiteSelector;


    /**
     * @param settings
     *            The settings.
     *
     */
    public SiteSettingsPanel(final SiteSettings settings) {
        m_settings = settings;

        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        add(createSitePanel());
        add(createSubsitePanel());
        setBorder(BorderFactory.createTitledBorder("Sharepoint site"));
    }

    /**
     * Should be called by the parent dialog after settings are loaded.
     *
     * @param client
     *            The client.
     */
    public void settingsLoaded(final IGraphServiceClient client) {
        m_client = client;

        m_subsiteSelector.onSettingsLoaded();
        m_groupSelector.onSettingsLoaded();

        SiteMode mode = m_settings.getMode();
        m_siteInput.getComponentPanel().setVisible(mode == SiteMode.WEB_URL);
        m_groupSelector.setVisible(mode == SiteMode.GROUP);
    }

    private JPanel createSitePanel() {
        JRadioButton rbRoot = createModeRadiobutton(SiteMode.ROOT);
        JRadioButton rbSite = createModeRadiobutton(SiteMode.WEB_URL);
        JRadioButton rbGroup = createModeRadiobutton(SiteMode.GROUP);
        ButtonGroup group = new ButtonGroup();
        group.add(rbRoot);
        group.add(rbSite);
        group.add(rbGroup);
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonsPanel.add(rbRoot);
        buttonsPanel.add(rbSite);
        buttonsPanel.add(rbGroup);

        m_siteInput = new DialogComponentString(m_settings.getWebURLModel(), "URL:", false, 40);

        m_groupSelector = new LoadedItemsSelector(m_settings.getGroupModel(), m_settings.getGroupNameModel(),
                "Fetch Groups", "Group:") {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<IdComboboxItem> fetchItems() throws Exception {
                return fetchGroups();
            }
        };
        m_groupSelector.setVisible(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.add(buttonsPanel);
        panel.add(m_siteInput.getComponentPanel());
        panel.add(m_groupSelector);
        return panel;
    }

    private JRadioButton createModeRadiobutton(final SiteMode mode) {
        JRadioButton rb = new JRadioButton(mode.getSelectorLabel());
        rb.setSelected(mode == m_settings.getMode());

        rb.addActionListener(e -> {
            m_settings.getModeModel().setStringValue(mode.name());

            m_siteInput.getComponentPanel().setVisible(mode == SiteMode.WEB_URL);
            m_groupSelector.setVisible(mode == SiteMode.GROUP);
            m_subsiteSelector.getComboModel().setSelectedItem(IdComboboxItem.DEFAULT);
        });

        m_settings.getModeModel().addChangeListener(e -> {
            rb.setSelected(mode == m_settings.getMode());
        });
        return rb;
    }

    private JPanel createSubsitePanel() {
        m_subsiteSelector = new LoadedItemsSelector(m_settings.getSubsiteModel(), m_settings.getSubsiteNameModel(),
                "Fetch Subsites", "Subsite:") {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<IdComboboxItem> fetchItems() throws Exception {
                return fetchSubsites();

            }
        };

        DialogComponentBoolean checkbox = new DialogComponentBoolean(m_settings.getConnectToSubsiteModel(), "");

        JPanel panel = new JPanel(new FlowLayout());
        panel.add(checkbox.getComponentPanel());
        panel.add(m_subsiteSelector);
        return panel;
    }

    private List<IdComboboxItem> fetchGroups() throws InvalidSettingsException {
        if (m_client == null) {
            throw new InvalidSettingsException("Settings isn't loaded");
        }

        List<DirectoryObject> objects = new ArrayList<>();
        IDirectoryObjectCollectionWithReferencesPage resp = m_client.me().transitiveMemberOf().buildRequest().get();
        objects.addAll(resp.getCurrentPage());
        while (resp.getNextPage() != null) {
            resp = resp.getNextPage().buildRequest().get();
            objects.addAll(resp.getCurrentPage());
        }

        return objects.stream().filter(o -> GraphApiUtil.GROUP_DATA_TYPE.equals(o.oDataType))
                .map(g -> new IdComboboxItem(g.id, GraphApiUtil.getDisplayName(g))).collect(toList());
    }

    private List<IdComboboxItem> fetchSubsites() throws InvalidSettingsException, IOException {
        if (m_client == null) {
            throw new InvalidSettingsException("Settings isn't loaded");
        }

        m_settings.validate();

        return listSubsites(m_settings.getParentSiteId(m_client)).stream().map(s -> new IdComboboxItem(s.id, s.name))
                .collect(toList());
    }

    private List<Site> listSubsites(final String siteId) {
        List<Site> result = new ArrayList<>();

        ISiteCollectionPage resp = m_client.sites(siteId).sites().buildRequest().get();
        result.addAll(resp.getCurrentPage());

        while (resp.getNextPage() != null) {
            resp = resp.getNextPage().buildRequest().get();
            result.addAll(resp.getCurrentPage());
        }

        List<String> ids = result.stream().map(s -> s.id).collect(toList());
        for (String id : ids) {
            result.addAll(listSubsites(id));
        }

        return result;
    }

}
