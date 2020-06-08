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
 *   2020-05-25 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.nodes.connection;

import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.SwingWorkerWithContext;

import com.microsoft.graph.http.GraphServiceException;

/**
 * Editor component for selecting items consist of 2 parts - id and title (like
 * subsites and user groups). Provides an ability to fetch possible options by
 * user's request.
 *
 * @author Alexander Bondaletov
 */
public abstract class LoadedItemsSelector extends JPanel {
    private static final long serialVersionUID = 1L;

    private final SettingsModelString m_idModel;
    private final SettingsModelString m_titleModel;

    private SwingWorkerWithContext<List<IdComboboxItem>, Void> m_fetchWorker;
    private DefaultComboBoxModel<IdComboboxItem> m_comboModel;
    private JProgressBar m_progressBar;
    private JButton m_fetchBtn;
    private JButton m_cancelBtn;
    private boolean ignoreListeners = false;

    /**
     * @param idModel
     *            Settings model holding id value.
     * @param titleModel
     *            Settings model holding title value.
     * @param fetchBtnLabel
     *            The label for the fetch button.
     *
     */
    public LoadedItemsSelector(final SettingsModelString idModel, final SettingsModelString titleModel,
            final String fetchBtnLabel) {
        m_idModel = idModel;
        m_titleModel = titleModel;

        m_comboModel = new DefaultComboBoxModel<>(new IdComboboxItem[] { IdComboboxItem.DEFAULT });
        JComboBox<IdComboboxItem> cbInput = new JComboBox<>(m_comboModel);
        cbInput.addActionListener(e -> onSelectionChanged());

        m_fetchBtn = new JButton(fetchBtnLabel);
        m_fetchBtn.addActionListener(e -> onFetch());

        m_cancelBtn = new JButton("Cancel");
        m_cancelBtn.addActionListener(e -> {
            if (m_fetchWorker != null) {
                m_fetchWorker.cancel(true);
            }
        });
        m_cancelBtn.setVisible(false);

        m_progressBar = new JProgressBar();
        m_progressBar.setIndeterminate(true);
        m_progressBar.setVisible(false);

        setLayout(new FlowLayout(FlowLayout.LEFT));
        add(cbInput);
        add(m_fetchBtn);
        add(m_progressBar);
        add(m_cancelBtn);
    }

    private void onSelectionChanged() {
        if (ignoreListeners) {
            return;
        }

        IdComboboxItem selected = (IdComboboxItem) m_comboModel.getSelectedItem();
        if (selected == null) {
            selected = IdComboboxItem.DEFAULT;
        }

        m_idModel.setStringValue(selected.getId());
        m_titleModel.setStringValue(selected.getTitle());
    }

    private void onFetch() {
        m_fetchWorker = new SwingWorkerWithContext<List<IdComboboxItem>, Void>() {

            @Override
            protected List<IdComboboxItem> doInBackgroundWithContext() throws Exception {
                return fetchItems();
            }

            @Override
            protected void doneWithContext() {
                m_cancelBtn.setVisible(false);
                m_progressBar.setVisible(false);
                m_fetchBtn.setVisible(true);

                if (isCancelled()) {
                    return;
                }

                try {
                    onItemsLoaded(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showError(ex);
                }
            }
        };

        m_cancelBtn.setVisible(true);
        m_progressBar.setVisible(true);
        m_fetchBtn.setVisible(false);

        m_fetchWorker.execute();
    }

    private void onItemsLoaded(final List<IdComboboxItem> sites) {
        ignoreListeners = true;
        m_comboModel.removeAllElements();
        m_comboModel.addElement(IdComboboxItem.DEFAULT);
        for (IdComboboxItem s : sites) {
            m_comboModel.addElement(s);
        }

        IdComboboxItem selected = getComboItemFromSettings();
        if (m_comboModel.getIndexOf(selected) < 0) {
            selected = IdComboboxItem.DEFAULT;
        }
        m_comboModel.setSelectedItem(selected);
        ignoreListeners = false;

        onSelectionChanged();
    }

    private void showError(final Exception ex) {
        String message = ex.getMessage();

        if (ex instanceof GraphServiceException) {
            message = ((GraphServiceException) ex).getServiceError().message;
        } else if (ex.getCause() instanceof GraphServiceException) {
            message = ((GraphServiceException) ex.getCause()).getServiceError().message;
        }

        JOptionPane.showMessageDialog(getRootPane(), message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Fetches available options.
     *
     * @return The list of available options.
     * @throws Exception
     */
    protected abstract List<IdComboboxItem> fetchItems() throws Exception;

    /**
     * @return the comboModel
     */
    public DefaultComboBoxModel<IdComboboxItem> getComboModel() {
        return m_comboModel;
    }

    /**
     * Should be called by the parent dialog after settings are loaded.
     */
    public void onSettingsLoaded() {
        IdComboboxItem selected = getComboItemFromSettings();
        if (m_comboModel.getIndexOf(selected) < 0) {
            m_comboModel.addElement(selected);
        }
        m_comboModel.setSelectedItem(selected);
    }

    private IdComboboxItem getComboItemFromSettings() {
        String id = m_idModel.getStringValue();
        if (id.isEmpty()) {
            return IdComboboxItem.DEFAULT;
        }
        return new IdComboboxItem(id, m_titleModel.getStringValue());
    }

    static class IdComboboxItem {
        static final IdComboboxItem DEFAULT = new IdComboboxItem("", "None");

        private String m_id;
        private String m_title;

        /**
         * @param id
         *            The site id.
         * @param title
         *            The site display name.
         *
         */
        public IdComboboxItem(final String id, final String title) {
            m_id = id;
            m_title = title;
        }

        /**
         * @return the id
         */
        public String getId() {
            return m_id;
        }

        /**
         * @return the title
         */
        public String getTitle() {
            return m_title;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            if (m_title != null) {
                return m_title;
            }
            return m_id;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof IdComboboxItem) {
                IdComboboxItem other = (IdComboboxItem) obj;
                return m_id.equals(other.m_id);
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return m_id.hashCode();
        }
    }
}