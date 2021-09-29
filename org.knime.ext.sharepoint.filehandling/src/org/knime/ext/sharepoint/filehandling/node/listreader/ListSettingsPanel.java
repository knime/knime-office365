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
package org.knime.ext.sharepoint.filehandling.node.listreader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.ext.microsoft.authentication.port.MicrosoftCredential;
import org.knime.ext.microsoft.authentication.port.oauth2.OAuth2Credential;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFileSystem;
import org.knime.ext.sharepoint.filehandling.node.LoadedItemsSelector;
import org.knime.ext.sharepoint.filehandling.node.LoadedItemsSelector.IdComboboxItem;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.util.CheckedExceptionSupplier;
import org.knime.filehandling.core.util.IOESupplier;

import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;

/**
 * Editor component for the {@link ListSettings} class.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class ListSettingsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final ListSettings m_settings;
    private MicrosoftCredential m_connection;

    private LoadedItemsSelector m_listSelector;

    private CheckedExceptionSupplier<FSConnection, IOException> m_fsConnectionSupplier;

    /**
     * @param settings
     * @param connectionSupplier
     */
    public ListSettingsPanel(final ListSettings settings, final IOESupplier<FSConnection> connectionSupplier) {
        m_settings = settings;
        m_fsConnectionSupplier = connectionSupplier;
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        add(createListPanel());
        setBorder(BorderFactory.createTitledBorder("Sharepoint site"));
    }

    private JPanel createListPanel() {
        m_listSelector = new LoadedItemsSelector(m_settings.getListModel(), m_settings.getListNameModel(), "Lists:",
                new SettingsModelBoolean("blib", true)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<IdComboboxItem> fetchItems() throws Exception {
                return fetchLists();

            }
        };

        return m_listSelector;
    }

    private List<IdComboboxItem> fetchLists() throws InvalidSettingsException, IOException {
        final IGraphServiceClient client = createClient();
        FSConnection connection = null;
        SharepointFileSystem fileSystem = null;
        try {
            connection = m_fsConnectionSupplier.get();
            fileSystem = (SharepointFileSystem) connection.getFileSystem();
            return listLists(fileSystem.getParentSiteId(client), client);
        } finally {
            client.shutdown();
            if (fileSystem != null) {
                fileSystem.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static List<IdComboboxItem> listLists(final String siteId, final IGraphServiceClient client) {
        final List<IdComboboxItem> result = new ArrayList<>();

        var lists = client.sites(siteId).lists().buildRequest().get();

        for (final var list : lists.getCurrentPage()) {
            result.add(new IdComboboxItem(list.id, list.displayName));
        }

        while (lists.getNextPage() != null) {
            lists = lists.getNextPage().buildRequest().get();
            for (final var list : lists.getCurrentPage()) {
                result.add(new IdComboboxItem(list.id, list.displayName));
            }
        }

        return result;
    }

    // TODO check this method
    /**
     * Should be called by the parent dialog after settings are loaded.
     *
     * @param credentials
     *            The Microsoft Credential object.
     */
    public void settingsLoaded(final MicrosoftCredential credentials) {
        m_connection = credentials;

        m_listSelector.onSettingsLoaded();
    }

    private IGraphServiceClient createClient() throws InvalidSettingsException {
        if (m_connection == null) {
            throw new InvalidSettingsException("Settings isn't loaded");
        }

        try {
            final String accessToken = ((OAuth2Credential) m_connection).getAccessToken().getToken();
            return GraphServiceClient.builder()
                    .authenticationProvider(a -> a.addHeader("Authorization", "Bearer " + accessToken)).buildClient();
        } catch (IOException ex) {
            // FIXME: this means we are doing IO in the UI thread...
            throw new InvalidSettingsException(ex);
        }
    }
}
