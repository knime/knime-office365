package org.knime.ext.sharepoint.lists.node;
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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentButtonGroup;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.dialog.SiteSettingsPanel;
import org.knime.ext.sharepoint.lists.node.writer.ListOverwritePolicy;
import org.knime.ext.sharepoint.settings.SiteMode;
import org.knime.ext.sharepoint.settings.SiteSettings;
import org.knime.filehandling.core.connections.base.ui.LoadedItemsSelector;
import org.knime.filehandling.core.connections.base.ui.LoadedItemsSelector.IdComboboxItem;
import org.knime.filehandling.core.util.GBCBuilder;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;

/**
 * Editor component for the {@link ListSettings} class.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings({ "java:S1948" })
public final class SharepointListSettingsPanel extends SiteSettingsPanel {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListSettingsPanel.class);

    private static final long serialVersionUID = 1L;

    private static final List<Option> OPTIONS_ITEMS = Collections
            .singletonList(new QueryOption("select", "system,id,displayName,name"));

    private final SiteSettings m_siteSettings;

    private final ListSettings m_listSettings;

    private final LoadedItemsSelector m_listSelector;

    private final DialogComponentBoolean m_showSystemLists;

    private final DialogComponentButtonGroup m_overwriteOptions;

    private SwingWorkerWithContext<Void, Void> m_urlSwingWorker;

    private boolean m_ignoreEvents = true;

    /**
     * Constructor.
     *
     * @param listSettings
     *            the {@link SharepointListSettings}
     */
    public SharepointListSettingsPanel(final SharepointListSettings listSettings) {
        this(listSettings, false, false);
    }

    /**
     * Constructor.
     *
     * @param listSettings
     *            the {@link SharepointListSettings}
     * @param editableListSelection
     *            flag to make the list selection editable
     * @param hasOverwriteOptions
     *            flag whether or not the panel will have overwrite options
     */
    public SharepointListSettingsPanel(final SharepointListSettings listSettings, final boolean editableListSelection,
            final boolean hasOverwriteOptions) {
        super(listSettings.getSiteSettings());
        m_siteSettings = listSettings.getSiteSettings();
        m_listSettings = listSettings.getListSettings();
        m_listSelector = new LoadedItemsSelector(m_listSettings.getListModel(), m_listSettings.getListNameModel(),
                "List:", null, editableListSelection) {

            private static final long serialVersionUID = 1L;

            @Override
            public List<IdComboboxItem> fetchItems() throws Exception {
                return fetchListPossible() ? fetchLists() : Collections.emptyList();
            }

            @Override
            public String fetchExceptionMessage(final Exception ex) {
                String message = ex.getMessage();

                if (ex instanceof GraphServiceException) {
                    message = ((GraphServiceException) ex).getServiceError().message;
                } else if (ex.getCause() instanceof GraphServiceException) {
                    message = ((GraphServiceException) ex.getCause()).getServiceError().message;
                }
                return message;
            }
        };

        m_showSystemLists = new DialogComponentBoolean(m_listSettings.getShowSystemListsModel(), "Show system lists");

        m_overwriteOptions = new DialogComponentButtonGroup(listSettings.getOverwritePolicyModel(), null, false,
                ListOverwritePolicy.values());

        if (!m_listSettings.showSystemListSettings()) {
            m_showSystemLists.getComponentPanel().setVisible(false);
        }

        addListener();

        add(createListPanel(hasOverwriteOptions));
    }

    /**
     * Checks if fetching the lists is possible.
     *
     * @return flag whether fetch is possible or not
     */
    private boolean fetchListPossible() {
        final var subsiteActiveAndNotEmpty = m_siteSettings.getConnectToSubsiteModel()
                .getBooleanValue() == !m_siteSettings.getSubsiteModel().getStringValue().isEmpty();
        switch (SiteMode.valueOf(m_siteSettings.getModeModel().getStringValue())) {
        case ROOT:
            m_listSelector.setEnabled(subsiteActiveAndNotEmpty);
            return subsiteActiveAndNotEmpty;
        case WEB_URL:
            final boolean urlModelIsEmpty = m_siteSettings.getWebURLModel().getStringValue().isEmpty();
            m_listSelector.setEnabled(subsiteActiveAndNotEmpty);
            return !urlModelIsEmpty && subsiteActiveAndNotEmpty;
        case GROUP:
            final boolean groupModelIsEmpty = m_siteSettings.getGroupModel().getStringValue().isEmpty();
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
        clearStatusMessage();
        try {
            m_urlSwingWorker = new SwingWorkerWithContext<Void, Void>() {

                @Override
                protected Void doInBackgroundWithContext() throws Exception {
                    Thread.sleep(300);
                    try {
                        m_listSelector.fetchItems();
                    } catch (Exception e) {
                        // Catching the exception here otherwise we get popups on every key stroke
                        setErrorMessage(e);
                    }
                    return null;
                }
            };
            m_urlSwingWorker.execute();
        } catch (Exception ex) { // NOSONAR we want to communicate any exception
            LOGGER.debug("Unexcpected exception occured during the fetching of the Sharepoint lists.", ex);
        }
    }

    /**
     * Adds an external listener on the {@link SettingsModel}s.
     *
     * @param listener
     *            a {@link ChangeListener}
     */
    public void addExternalListener(final ChangeListener listener) {
        m_listSettings.getListModel().addChangeListener(listener);
        m_listSettings.getShowSystemListsModel().addChangeListener(listener);
        m_siteSettings.getModeModel().addChangeListener(listener);
        m_siteSettings.getGroupModel().addChangeListener(listener);
        m_siteSettings.getSubsiteModel().addChangeListener(listener);
        m_siteSettings.getConnectToSubsiteModel().addChangeListener(listener);
        m_siteSettings.getWebURLModel().addChangeListener(listener);
    }

    private void addListener() {
        m_listSettings.getShowSystemListsModel().addChangeListener(l -> triggerFetching(false));
        final ChangeListener listener = l -> triggerFetching(true);
        m_siteSettings.getModeModel().addChangeListener(listener);
        m_siteSettings.getGroupModel().addChangeListener(listener);
        m_siteSettings.getSubsiteModel().addChangeListener(listener);
        m_siteSettings.getConnectToSubsiteModel().addChangeListener(listener);
        m_siteSettings.getWebURLModel()
                .addChangeListener(l -> ViewUtils.runOrInvokeLaterInEDT(this::webUrlChangeListener));
    }

    /**
     * Triggers the fetching of the {@link LoadedItemsSelector} for the lists.
     *
     * @param resetListModel
     *            whether or not to reset the list {@link SettingsModelString}
     */
    public void triggerFetching(final boolean resetListModel) {
        clearStatusMessage();
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

    private JPanel createListPanel(final boolean hasOverwriteOptions) {
        final var panel = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();

        panel.setBorder(BorderFactory.createTitledBorder("Sharepoint list"));

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridwidth = 2;
        panel.add(m_listSelector, gbc);

        gbc.gridy++;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 22, 0, 0);
        panel.add(m_showSystemLists.getComponentPanel(), gbc);

        gbc.gridx++;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(Box.createHorizontalBox(), gbc);

        if (hasOverwriteOptions) {
            gbc.gridx = 0;
            gbc.gridwidth = 2;
            gbc.gridy++;
            gbc.anchor = GridBagConstraints.EAST;
            gbc.fill = GridBagConstraints.FIRST_LINE_END;
            gbc.insets = new Insets(0, 0, 0, 75);

            panel.add(createIfExistsPanel(), gbc);
        }

        return panel;
    }

    private JPanel createIfExistsPanel() {
        final var panel = new JPanel(new GridBagLayout());
        final var gbc = new GBCBuilder().fillHorizontal().resetPos();
        panel.add(new JLabel("If exists:"), gbc.insetLeft(30).build());
        panel.add(m_overwriteOptions.getComponentPanel(), gbc.incX().insetLeft(-5).build());
        panel.add(Box.createHorizontalBox(), gbc.incX().setWeightX(1).build());

        return panel;
    }

    private List<IdComboboxItem> fetchLists() throws IOException, InvalidSettingsException {
        m_siteSettings.validateParentSiteSettings();

        final IGraphServiceClient client = super.createClient();
        final var siteResolver = new SharepointSiteResolver(client, m_siteSettings.getMode(),
                m_siteSettings.getSubsiteModel().getStringValue(), m_siteSettings.getWebURLModel().getStringValue(),
                m_siteSettings.getGroupModel().getStringValue());
        return listLists(siteResolver.getTargetSiteId(), client,
                m_listSettings.getShowSystemListsModel().getBooleanValue());
    }

    private static List<IdComboboxItem> listLists(final String siteId, final IGraphServiceClient client,
            final boolean showSystemLists) {

        final var result = new ArrayList<IdComboboxItem>();
        var resp = client.sites(siteId).lists() //
                .buildRequest(showSystemLists ? OPTIONS_ITEMS : Collections.emptyList()) //
                .get();

        for (final var list : resp.getCurrentPage()) {
            result.add(new IdComboboxItem(list.id, String.format("%s (%s)", list.displayName, list.name)));
        }

        while (resp.getNextPage() != null) {
            resp = resp.getNextPage().buildRequest().get();
            for (final var list : resp.getCurrentPage()) {
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
    public static class ListSettings {

        private static final String KEY_LIST = "list";

        private static final String KEY_LIST_NAME = "listName";

        private static final String KEY_SHOW_SYSTEM_LISTS = "showSystemLists";

        private final SettingsModelString m_list;

        private final SettingsModelString m_listName;

        private final SettingsModelBoolean m_showSystemLists;

        private final boolean m_showSystemListSettings;

        /**
         * Creates new instance.
         *
         */
        public ListSettings() {
            this(true);
        }

        /**
         * Creates new instance.
         *
         * @param showSystemListSettings
         *            whether to show the showSystemListSettings or hide them
         *
         */
        public ListSettings(final boolean showSystemListSettings) {
            m_showSystemListSettings = showSystemListSettings;
            m_list = new SettingsModelString(KEY_LIST, "");
            m_listName = new SettingsModelString(KEY_LIST_NAME, "");
            m_showSystemLists = new SettingsModelBoolean(updateSystemListSettingsKey(), false);
        }

        private String updateSystemListSettingsKey() {
            return m_showSystemListSettings ? KEY_SHOW_SYSTEM_LISTS
                    : (KEY_SHOW_SYSTEM_LISTS + SettingsModel.CFGKEY_INTERNAL);
        }

        /**
         * Creates new instance from {@link ListSettings}.
         *
         * @param toCopy
         *            {@link ListSettings} to be copied
         */
        public ListSettings(final ListSettings toCopy) {
            m_showSystemListSettings = toCopy.showSystemListSettings();
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
        public void saveSettingsTo(final NodeSettingsWO settings) {
            final NodeSettingsWO listSettings = settings.addNodeSettings(KEY_LIST);
            m_list.saveSettingsTo(listSettings);
            m_listName.saveSettingsTo(listSettings);
            m_showSystemLists.saveSettingsTo(listSettings);
        }

        /**
         * Validates the settings in a given {@link NodeSettingsRO}
         *
         * @param settings
         *            Node settings.
         * @throws InvalidSettingsException
         */
        public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
            final NodeSettingsRO listSettings = settings.getNodeSettings(KEY_LIST);
            m_list.validateSettings(listSettings);
            m_listName.validateSettings(listSettings);
            m_showSystemLists.validateSettings(listSettings);
        }

        /**
         * Loads settings from the given {@link NodeSettingsRO}
         *
         * @param settings
         *            Node settings.
         * @throws InvalidSettingsException
         */
        public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
            final NodeSettingsRO listSettings = settings.getNodeSettings(KEY_LIST);
            m_list.loadSettingsFrom(listSettings);
            m_listName.loadSettingsFrom(listSettings);
            m_showSystemLists.loadSettingsFrom(listSettings);
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

        /**
         * @return whether or not to show the system list settings
         */
        public boolean showSystemListSettings() {
            return m_showSystemListSettings;
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
