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
package org.knime.ext.sharepoint.filehandling.node;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
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
abstract class LoadedItemsSelector extends JPanel {
    private static final long serialVersionUID = 1L;

    private final SettingsModelString m_idModel;
    private final SettingsModelString m_titleModel;
    private final SettingsModelBoolean m_checkedModel;
    private final DefaultComboBoxModel<IdComboboxItem> m_comboModel;
    private final JComboBox<IdComboboxItem> m_combobox;
    private final JButton m_fetchBtn;
    private final JButton m_cancelBtn;

    private SwingWorkerWithContext<List<IdComboboxItem>, Void> m_fetchWorker;
    private boolean m_enabled = true;
    private boolean ignoreListeners = false;

    /**
     * @param idModel
     *            Settings model holding id value.
     * @param titleModel
     *            Settings model holding title value.
     * @param caption
     *            The caption label
     * @param checkedModel
     *            Optional settings model holding "checked" value.
     *
     */
    public LoadedItemsSelector(final SettingsModelString idModel, final SettingsModelString titleModel,
            final String caption, final SettingsModelBoolean checkedModel) {
        m_idModel = idModel;
        m_titleModel = titleModel;
        m_checkedModel = checkedModel;

        m_comboModel = new DefaultComboBoxModel<>(new IdComboboxItem[] {});
        m_combobox = new JComboBox<>(m_comboModel);
        m_combobox.addActionListener(e -> onSelectionChanged());
        m_combobox.setRenderer(new IdComboboxCellRenderer());

        m_fetchBtn = new JButton("Refresh");
        m_fetchBtn.addActionListener(e -> onFetch());

        m_cancelBtn = new JButton("Cancel");
        m_cancelBtn.addActionListener(e -> {
            if (m_fetchWorker != null) {
                m_fetchWorker.cancel(true);
            }
        });
        m_cancelBtn.setVisible(false);
        m_cancelBtn.setPreferredSize(m_fetchBtn.getPreferredSize());

        final Component labelOrCheckbox;
        if (m_checkedModel != null) {
            JCheckBox checkedInput = new JCheckBox(caption);
            checkedInput.addActionListener(e -> {
                boolean checked = checkedInput.isSelected();
                m_checkedModel.setBooleanValue(checked);

                if (checked) {
                    onFetch();
                }
            });
            m_checkedModel.addChangeListener(e -> {
                boolean checked = m_checkedModel.getBooleanValue();
                checkedInput.setSelected(checked);
                setEnabled(checked);
            });

            checkedInput.setSelected(m_checkedModel.getBooleanValue());
            setEnabled(m_checkedModel.getBooleanValue());
            labelOrCheckbox = checkedInput;
        } else {
            final Box paddedBox = new Box(BoxLayout.X_AXIS);
            paddedBox.add(Box.createHorizontalStrut(5));
            paddedBox.add(new JLabel(caption));
            labelOrCheckbox = paddedBox;
        }

        GridBagConstraints c = new GridBagConstraints();
        setLayout(new GridBagLayout());
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 5);

        add(labelOrCheckbox, c);
        c.gridx += 1;

        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.5;
        add(m_combobox, c);


        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_END;
        c.gridx += 1;
        c.weightx = 0;
        add(m_fetchBtn, c);

        c.gridx += 1;
        add(m_cancelBtn, c);
    }

    private void onSelectionChanged() {
        if (ignoreListeners) {
            return;
        }

        String id = "";
        String title = "";
        IdComboboxItem selected = (IdComboboxItem) m_comboModel.getSelectedItem();
        if (selected != null) {
            id = selected.getId();
            title = selected.getTitle();
        }

        m_idModel.setStringValue(id);
        m_titleModel.setStringValue(title);
    }

    private void onFetch() {
        m_fetchWorker = new SwingWorkerWithContext<List<IdComboboxItem>, Void>() {

            @Override
            protected List<IdComboboxItem> doInBackgroundWithContext() throws Exception {
                return fetchItems();
            }

            @Override
            protected void doneWithContext() {
                m_combobox.setEnabled(m_enabled);
                m_cancelBtn.setVisible(false);
                m_fetchBtn.setVisible(true);

                if (isCancelled()) {
                    return;
                }

                try {
                    onItemsLoaded(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    if (m_checkedModel != null) {
                        m_checkedModel.setBooleanValue(false);
                    }
                    showError(ex);
                }
            }
        };

        m_combobox.setEnabled(false);
        m_cancelBtn.setVisible(true);
        m_fetchBtn.setVisible(false);

        m_fetchWorker.execute();
    }

    private void onItemsLoaded(final List<IdComboboxItem> sites) {
        ignoreListeners = true;
        m_comboModel.removeAllElements();
        for (IdComboboxItem s : sites) {
            m_comboModel.addElement(s);
        }

        updateComboFromSettings(true);
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
        updateComboFromSettings(false);
    }

    private void updateComboFromSettings(final boolean afterFetch) {
        String id = m_idModel.getStringValue();
        if (id.isEmpty()) {
            m_comboModel.setSelectedItem(null);
            return;
        }

        IdComboboxItem item = new IdComboboxItem(id, m_titleModel.getStringValue());
        if (m_comboModel.getIndexOf(item) < 0) {
            if (!afterFetch) {
                m_comboModel.addElement(item);
            } else {
                item = null;
            }
        }
        m_comboModel.setSelectedItem(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnabled(final boolean enabled) {
        m_enabled = enabled;
        if (!enabled) {
            m_combobox.setSelectedIndex(-1);
        }
        m_combobox.setEnabled(enabled);
        m_fetchBtn.setEnabled(enabled);
    }

    /**
     * Starts items fetching if no fetching was performed yet.
     */
    public void fetchOnce() {
        if (m_fetchWorker == null) {
            onFetch();
        }
    }

    static class IdComboboxItem {

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

    private class IdComboboxCellRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
                final boolean isSelected, final boolean cellHasFocus) {
            if (m_fetchWorker != null && !m_fetchWorker.isDone()) {
                return new JLabel("Loading...");
            }

            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }

    }
}
