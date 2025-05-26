package org.knime.ext.sharepoint.dialog;
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

import static java.util.stream.Collectors.toList;

import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.SharepointSiteResolver;
import org.knime.ext.sharepoint.settings.SiteMode;
import org.knime.ext.sharepoint.settings.SiteSettings;
import org.knime.filehandling.core.connections.base.ui.LoadedItemsSelector;
import org.knime.filehandling.core.connections.base.ui.LoadedItemsSelector.IdComboboxItem;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;
import org.knime.filehandling.core.defaultnodesettings.status.DefaultStatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusView;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.requests.DirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.SiteCollectionPage;

import okhttp3.Request;

/**
 * Editor component for the {@link SiteSettings} class.
 *
 * @author Alexander Bondaletov
 */
@SuppressWarnings({ "java:S1948" })
public class SiteSettingsPanel extends JPanel {

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    private static final long serialVersionUID = 1L;

    private final SiteSettings m_settings;

    private CredentialPortObjectSpec m_credentialPortSpec;

    private JPanel m_cards;

    private final StatusView m_errorLabel = new StatusView(500);

    private LoadedItemsSelector m_groupSelector;

    private LoadedItemsSelector m_subsiteSelector;

    /**
     * @param settings
     */
    public SiteSettingsPanel(final SiteSettings settings) {
        m_settings = settings;
        createLayout();
    }

    private void createLayout() {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        final var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.add(createRadioSelectorPanel());
        panel.add(createCardsPanel());
        panel.add(createSubsitePanel());
        panel.add(m_errorLabel.getPanel());
        panel.setBorder(BorderFactory.createTitledBorder("Sharepoint site"));
        add(panel);
    }

    /**
     * Should be called by the parent dialog after settings are loaded.
     *
     * @param credentialPortSpec
     *            the CredentialPortObjectSpec
     */
    public void settingsLoaded(final CredentialPortObjectSpec credentialPortSpec) {
        m_credentialPortSpec = credentialPortSpec;

        m_subsiteSelector.onSettingsLoaded();
        m_groupSelector.onSettingsLoaded();
        showModePanel(m_settings.getMode());
    }

    private JPanel createRadioSelectorPanel() {
        final var rbRoot = createModeRadiobutton(SiteMode.ROOT);
        final var rbSite = createModeRadiobutton(SiteMode.WEB_URL);
        final var rbGroup = createModeRadiobutton(SiteMode.GROUP);

        final var group = new ButtonGroup();
        group.add(rbRoot);
        group.add(rbSite);
        group.add(rbGroup);

        final var buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonsPanel.add(rbRoot);
        buttonsPanel.add(rbSite);
        buttonsPanel.add(rbGroup);
        buttonsPanel.add(Box.createHorizontalGlue());
        return buttonsPanel;
    }

    private JRadioButton createModeRadiobutton(final SiteMode mode) {
        final var rb = new JRadioButton(mode.getSelectorLabel());
        rb.setSelected(mode == m_settings.getMode());

        rb.addActionListener(e -> {
            m_settings.getModeModel().setStringValue(mode.name());
            m_errorLabel.clearStatus();
            if (mode == SiteMode.GROUP) {
                m_groupSelector.fetch();
            }
            showModePanel(mode);
        });

        m_settings.getModeModel().addChangeListener(e -> rb.setSelected(mode == m_settings.getMode()));
        return rb;
    }

    private void showModePanel(final SiteMode mode) {
        ((CardLayout) m_cards.getLayout()).show(m_cards, mode.name());
    }

    /**
     * Sets the error message in case of any error during
     * {@link LoadedItemsSelector#fetchItems()}
     *
     * @param e
     *            the thrown {@link Exception}
     */
    protected void setErrorMessage(final Exception e) {
        m_errorLabel.setStatus(DefaultStatusMessage.mkError(ExceptionUtil.getDeepestErrorMessage(e, false)));
    }

    /**
     * Clears the error message.
     */
    protected void clearStatusMessage() {
        m_errorLabel.clearStatus();
    }

    private JPanel createCardsPanel() {
        final var siteInput = new DialogComponentString(m_settings.getWebURLModel(), "URL:", false, 50);
        siteInput.getComponentPanel().setLayout(new FlowLayout(FlowLayout.LEFT));

        m_groupSelector = new LoadedItemsSelector(m_settings.getGroupModel(), m_settings.getGroupNameModel(), "Group:",
                null) {
            private static final long serialVersionUID = 1L;

            @Override
            public List<IdComboboxItem> fetchItems() throws Exception {
                return fetchGroups();
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

        m_cards = new JPanel(new CardLayout());
        m_cards.add(new JLabel(), SiteMode.ROOT.name());
        m_cards.add(siteInput.getComponentPanel(), SiteMode.WEB_URL.name());
        m_cards.add(m_groupSelector, SiteMode.GROUP.name());
        return m_cards;
    }

    private JPanel createSubsitePanel() {
        m_subsiteSelector = new LoadedItemsSelector(m_settings.getSubsiteModel(), m_settings.getSubsiteNameModel(),
                "Subsite:", m_settings.getConnectToSubsiteModel()) {
            private static final long serialVersionUID = 1L;

            @Override
            public List<IdComboboxItem> fetchItems() throws Exception {
                return fetchSubsites();
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

        return m_subsiteSelector;
    }

    private List<IdComboboxItem> fetchGroups() throws IOException {
        final GraphServiceClient<Request> client = createClient();

        List<DirectoryObject> objects = new ArrayList<>();
        DirectoryObjectCollectionWithReferencesPage resp = client.me().transitiveMemberOf().buildRequest().get();
        objects.addAll(resp.getCurrentPage());
        while (resp.getNextPage() != null) {
            resp = resp.getNextPage().buildRequest().get();
            objects.addAll(resp.getCurrentPage());
        }

        return objects.stream().filter(o -> GraphApiUtil.GROUP_DATA_TYPE.equals(o.oDataType))
                .map(Group.class::cast)
                .map(g -> new IdComboboxItem(g.id, Optional.ofNullable(g.displayName).orElse(g.id)))
                .collect(toList());
    }

    /**
     * Creates a {@link GraphServiceClient} with a {@link Credential}.
     *
     * @return the {@link GraphServiceClient}
     * @throws IOException
     */
    protected GraphServiceClient<Request> createClient() throws IOException {
        if (m_credentialPortSpec == null) {
            throw new IOException("Settings aren't loaded");
        }

        try {
            final var authProvider = GraphCredentialUtil.createAuthenticationProvider(m_credentialPortSpec);
            return GraphApiUtil.createClient(authProvider, DIALOG_CLIENT_TIMEOUT_MILLIS, DIALOG_CLIENT_TIMEOUT_MILLIS);
        } catch (Exception ex) {
            throw ExceptionUtil.wrapAsIOException(ex);
        }
    }

    private List<IdComboboxItem> fetchSubsites() throws InvalidSettingsException, IOException {
        m_settings.validateParentSiteSettings();
        final GraphServiceClient<Request> client = createClient();
        final var siteResolver = new SharepointSiteResolver(client, m_settings.getMode(),
                m_settings.getSubsiteModel().getStringValue(), m_settings.getWebURLModel().getStringValue(),
                m_settings.getGroupModel().getStringValue());
        return listSubsites(siteResolver.getParentSiteId(), client, "");
    }

    private List<IdComboboxItem> listSubsites(final String siteId, final GraphServiceClient<?> client,
            final String prefix) {
        final List<IdComboboxItem> result = new ArrayList<>();

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

    private List<IdComboboxItem> processSite(final Site site, final GraphServiceClient<?> client, final String prefix) {
        final List<IdComboboxItem> result = new ArrayList<>();
        final String name = prefix + site.name;

        result.add(new IdComboboxItem(site.id, name));
        result.addAll(listSubsites(site.id, client, prefix + site.name + " > "));
        return result;
    }

    /**
     * Clears the status when opening the dialog.
     */
    public void onOpen() {
        clearStatusMessage();
    }

}
