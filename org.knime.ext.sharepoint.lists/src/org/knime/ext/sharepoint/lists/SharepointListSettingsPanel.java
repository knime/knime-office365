package org.knime.ext.sharepoint.lists;
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

import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.dialog.LoadedItemsSelector;
import org.knime.ext.sharepoint.dialog.LoadedItemsSelector.IdComboboxItem;
import org.knime.ext.sharepoint.dialog.SiteSettingsPanel;
import org.knime.ext.sharepoint.lists.node.reader.SharepointListSettings;
import org.knime.ext.sharepoint.settings.SiteMode;
import org.knime.ext.sharepoint.settings.SiteSettings;
import org.knime.filehandling.core.util.GBCBuilder;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;

/**
 * Editor component for the {@link ListSettings} class.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointListSettingsPanel extends SiteSettingsPanel {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListSettingsPanel.class);

    private static final long serialVersionUID = 1L;

    private static final List<Option> OPTIONS_ITEMS = Collections
            .singletonList(new QueryOption("select", "system,id,displayName,name"));

    private final ListSettings m_listSettings;

    private final LoadedItemsSelector m_listSelector;

    private final DialogComponentBoolean m_showSystemLists;

    private SwingWorkerWithContext<Void, Void> m_urlSwingWorker;

    private boolean m_ignoreEvents = true;

    /**
     * Constructor.
     *
     * @param listSettings
     *            the {@link SharepointListSettings}
     */
    public SharepointListSettingsPanel(final SharepointListSettings listSettings) {
        super(listSettings.getSiteSettings());
        m_listSettings = listSettings.getSiteSettings();
        m_listSelector = new LoadedItemsSelector(m_listSettings.getListModel(), m_listSettings.getListNameModel(),
                "List:", null) {
            private static final long serialVersionUID = 1L;

            @Override
            public List<IdComboboxItem> fetchItems() throws Exception {
                clearStatusMessage();
                try {
                    return fetchListPossible() ? fetchLists() : Collections.emptyList();
                } catch (Exception e) { // NOSONAR we want to catch all exceptions
                    LOGGER.debug("An error occured while fetching the lists", e);
                    setErrorMessage(e);
                    return Collections.emptyList();
                }
            }
        };

        m_showSystemLists = new DialogComponentBoolean(m_listSettings.getShowSystemListsModel(), "Show system lists");

        add(createListPanel());
    }

    /**
     * Checks if fetching the lists is possible.
     *
     * @return flag whether fetch is possible or not
     */
    private boolean fetchListPossible() {
        final var subsiteActiveAndNotEmpty = m_listSettings.getConnectToSubsiteModel()
                .getBooleanValue() == !m_listSettings.getSubsiteModel().getStringValue().isEmpty();
        switch (SiteMode.valueOf(m_listSettings.getModeModel().getStringValue())) {
        case ROOT:
            m_listSelector.setEnabled(subsiteActiveAndNotEmpty);
            return subsiteActiveAndNotEmpty;
        case WEB_URL:
            final boolean urlModelIsEmpty = m_listSettings.getWebURLModel().getStringValue().isEmpty();
            m_listSelector.setEnabled(subsiteActiveAndNotEmpty);
            return !urlModelIsEmpty && subsiteActiveAndNotEmpty;
        case GROUP:
            final boolean groupModelIsEmpty = m_listSettings.getGroupModel().getStringValue().isEmpty();
            m_listSelector.setEnabled(!groupModelIsEmpty == subsiteActiveAndNotEmpty);
            return !groupModelIsEmpty && subsiteActiveAndNotEmpty;
        default:
            return false;
        }
    }

    private void webUrlChangeListener() {
        if (m_urlSwingWorker != null) {
            m_urlSwingWorker.cancel(true);
        }
        try {
            m_urlSwingWorker = new SwingWorkerWithContext<Void, Void>() {

                @Override
                protected Void doInBackgroundWithContext() throws Exception {
                    Thread.sleep(300);
                    clearStatusMessage();
                    m_listSelector.fetch();
                    return null;
                }
            };
            m_urlSwingWorker.execute();
        } catch (Exception ex) { // NOSONAR we want to communicate any exception
            LOGGER.debug("Unexcpected exception occured during the fetching of the Sharepoint lists.", ex);
        }
    }

    /**
     * Adds {@link ChangeListener} to the {@link SettingsModel}.
     *
     * @param listener
     *            the {@link ChangeListener}
     */
    public void addListener(final ChangeListener listener) {
        m_listSettings.getListModel().addChangeListener(listener);

        m_listSettings.getShowSystemListsModel().addChangeListener(createChangelistener(listener, false));

        final var l1 = createChangelistener(listener, true);
        m_listSettings.getModeModel().addChangeListener(l1);
        m_listSettings.getGroupModel().addChangeListener(l1);
        m_listSettings.getSubsiteModel().addChangeListener(l1);
        m_listSettings.getConnectToSubsiteModel().addChangeListener(l1);

        m_listSettings.getWebURLModel().addChangeListener(l -> {
            ViewUtils.runOrInvokeLaterInEDT(this::webUrlChangeListener);
            listener.stateChanged(l);
        });
    }

    private ChangeListener createChangelistener(final ChangeListener listener, final boolean resetListModel) {
        return l -> {
            triggerFetching(resetListModel);
            listener.stateChanged(l);
        };
    }

    /**
     * Triggers the fetching of the {@link LoadedItemsSelector} for the lists.
     *
     * @param resetListModel
     *            whether or not to reset the list {@link SettingsModelString}
     */
    public void triggerFetching(final boolean resetListModel) {
        if (!m_ignoreEvents) {
            if (resetListModel) {
                m_listSettings.getListModel().setStringValue("");
            }
            m_listSelector.fetch();
        }
    }

    /**
     * Should be called by the parent dialog after settings are loaded.
     *
     * @param credentials
     *            The Microsoft Credential object.
     */
    @Override
    public void settingsLoaded(final MicrosoftCredential credentials) {
        m_ignoreEvents = true;
        super.settingsLoaded(credentials);
        m_listSelector.onSettingsLoaded();
        m_ignoreEvents = false;
    }

    private JPanel createListPanel() {
        final var panel = new JPanel(new GridBagLayout());
        final var gbc = new GBCBuilder().fillHorizontal().resetPos();
        panel.setBorder(BorderFactory.createTitledBorder("Sharepoint list"));
        panel.add(m_listSelector, gbc.setWeightX(1).setWidth(2).build());
        panel.add(m_showSystemLists.getComponentPanel(), gbc.incY().setWeightX(0).setWidth(1).insetLeft(-2).build());
        panel.add(Box.createHorizontalBox(), gbc.setWeightX(1).incX().build());

        return panel;
    }

    private List<IdComboboxItem> fetchLists() throws InvalidSettingsException, IOException {
        final IGraphServiceClient client = super.createClient();
        m_listSettings.validateParentSiteSettings();
        final var siteResolver = new SharepointSiteResolver(client, m_listSettings.getMode(),
                m_listSettings.getSubsiteModel().getStringValue(), m_listSettings.getWebURLModel().getStringValue(),
                m_listSettings.getGroupModel().getStringValue());
        return listLists(siteResolver.getTargetSiteId(), client,
                m_listSettings.getShowSystemListsModel().getBooleanValue());
    }

    private static List<IdComboboxItem> listLists(final String siteId, final IGraphServiceClient client,
            final boolean showSystemLists) {
        final List<IdComboboxItem> result = new ArrayList<>();
        var resp = client.sites(siteId).lists().buildRequest(showSystemLists ? OPTIONS_ITEMS : Collections.emptyList())
                .get();
        for (final com.microsoft.graph.models.extensions.List list : resp.getCurrentPage()) {
            result.add(new IdComboboxItem(list.id, String.format("%s (%s)", list.displayName, list.name)));
        }

        while (resp.getNextPage() != null) {
            resp = resp.getNextPage().buildRequest().get();
            for (final com.microsoft.graph.models.extensions.List list : resp.getCurrentPage()) {
                result.add(new IdComboboxItem(list.id, String.format("%s (%s)", list.displayName, list.name)));
            }
        }
        return result;
    }

    /**
     * Class represents chosen list settings.
     *
     * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
     */
    public static class ListSettings extends SiteSettings {

        private static final String KEY_LIST = "list";

        private static final String KEY_LIST_NAME = "listName";

        private static final String KEY_SHOW_SYSTEM_LISTS = "showSystemLists";

        private final SettingsModelString m_list;

        private final SettingsModelString m_listName;

        private final SettingsModelBoolean m_showSystemLists;

        /**
         * Creates new instance.
         */
        public ListSettings() {
            super();
            m_list = new SettingsModelString(KEY_LIST, "");
            m_listName = new SettingsModelString(KEY_LIST_NAME, "");
            m_showSystemLists = new SettingsModelBoolean(KEY_SHOW_SYSTEM_LISTS, false);
        }

        /**
         * Creates new instance from {@link ListSettings}.
         *
         * @param toCopy
         *            {@link ListSettings} to be copied
         */
        public ListSettings(final ListSettings toCopy) {
            super(toCopy);
            m_list = toCopy.getListModel();
            m_listName = toCopy.getListNameModel();
            m_showSystemLists = toCopy.getShowSystemListsModel();
        }

        /**
         * Saves the settings in this instance to the given {@link NodeSettingsWO}
         *
         * @param settings
         *            Node settings.
         */
        @Override
        public void saveSettingsTo(final NodeSettingsWO settings) {
            super.saveSettingsTo(settings);
            m_list.saveSettingsTo(settings);
            m_listName.saveSettingsTo(settings);
            m_showSystemLists.saveSettingsTo(settings);
        }

        /**
         * Validates the settings in a given {@link NodeSettingsRO}
         *
         * @param settings
         *            Node settings.
         * @throws InvalidSettingsException
         */
        @Override
        public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
            super.validateSettings(settings);
            m_list.validateSettings(settings);
            m_listName.validateSettings(settings);
            m_showSystemLists.validateSettings(settings);
        }

        /**
         * Loads settings from the given {@link NodeSettingsRO}
         *
         * @param settings
         *            Node settings.
         * @throws InvalidSettingsException
         */
        @Override
        public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
            super.loadSettingsFrom(settings);
            m_list.loadSettingsFrom(settings);
            m_listName.loadSettingsFrom(settings);
            m_showSystemLists.loadSettingsFrom(settings);
        }

        /**
         * @return the list model
         */
        public SettingsModelString getListModel() {
            return m_list;
        }

        /**
         * @return the list name model
         */
        public SettingsModelString getListNameModel() {
            return m_listName;
        }

        /**
         * @return the show system lists model
         */
        public SettingsModelBoolean getShowSystemListsModel() {
            return m_showSystemLists;
        }

        @Override
        public int hashCode() {
            final var prime = 31;
            int result = super.hashCode();
            result = prime * result + Objects.hash(m_list.getStringValue(), m_listName.getStringValue(),
                    m_showSystemLists.getBooleanValue());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!super.equals(obj)) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ListSettings other = (ListSettings) obj;
            return Objects.equals(m_list.getStringValue(), other.m_list.getStringValue())
                    && Objects.equals(m_listName.getStringValue(), other.m_listName.getStringValue())
                    && Objects.equals(m_showSystemLists.getBooleanValue(), other.m_showSystemLists.getBooleanValue());
        }
    }

    /**
     * Cancels running {@link SwingWorkerWithContext}.
     */
    public void onClose() {
        if (m_urlSwingWorker != null) {
            m_urlSwingWorker.cancel(true);
            m_urlSwingWorker = null;
        }
    }

}
