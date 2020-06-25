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
 *   2020-06-11 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.providers.ui;

import java.awt.FlowLayout;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.knime.core.util.SwingWorkerWithContext;
import org.knime.ext.microsoft.authentication.providers.InteractiveAuthProvider;

import com.microsoft.aad.msal4j.MsalInteractionRequiredException;

/**
 * Editor component for the {@link InteractiveAuthProvider}.
 *
 * @author Alexander Bondaletov
 */
public class InteractiveProviderEditor extends MSALAuthProviderEditor<InteractiveAuthProvider> {
    private static final long serialVersionUID = 1L;

    private JButton m_loginBtn;
    private JButton m_cancelBtn;
    private JLabel m_statusLabel;
    private SwingWorkerWithContext<Void, Void> m_worker;

    /**
     * Creates new instance.
     *
     * @param provider
     *            The auth provider.
     *
     */
    public InteractiveProviderEditor(final InteractiveAuthProvider provider) {
        super(provider);

        m_provider.getTokenCacheModel().addChangeListener(e -> updateStatusLabel(false));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JComponent createContentPane() {
        Box box = new Box(BoxLayout.PAGE_AXIS);
        box.add(createButtonsPanel());
        box.add(m_provider.getStorageLocation().createEditor());
        return box;
    }

    private JPanel createButtonsPanel() {
        m_loginBtn = new JButton("Login");
        m_loginBtn.addActionListener(e -> onLogin());

        m_cancelBtn = new JButton("Cancel");
        m_cancelBtn.addActionListener(e -> {
            if (m_worker != null) {
                m_worker.cancel(true);
            }
        });

        m_statusLabel = new JLabel();

        updateControls(false);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(m_loginBtn);
        panel.add(m_cancelBtn);
        panel.add(m_statusLabel);
        return panel;
    }

    private void onLogin() {
        m_worker = new SwingWorkerWithContext<Void, Void>() {

            @Override
            protected Void doInBackgroundWithContext() throws Exception {
                m_provider.validate();
                m_provider.performLogin();
                return null;
            }

            @Override
            protected void doneWithContext() {
                updateControls(false);

                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showError(ex);
                }
            }
        };

        updateControls(true);
        m_worker.execute();
    }

    private void showError(final Exception ex) {
        String message = ex.getMessage();

        MsalInteractionRequiredException msalEx = extractMsalException(ex);
        if (msalEx != null) {
            message = msalEx.getMessage();
        }

        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static MsalInteractionRequiredException extractMsalException(final Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof MsalInteractionRequiredException) {
                return (MsalInteractionRequiredException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void updateControls(final boolean inProgress) {
        m_loginBtn.setVisible(!inProgress);
        m_cancelBtn.setVisible(inProgress);
        updateStatusLabel(inProgress);
    }

    private void updateStatusLabel(final boolean inProgress) {
        if (inProgress) {
            m_statusLabel.setText("Connecting...");
        } else {
            boolean loggedIn = m_provider.isLoggedIn();
            String color = loggedIn ? "#007F0E" : "#CC0000";
            String connected = loggedIn ? "Connected" : "Not connected";
            String status = String.format("<html>Status: <font color=%s>%s</font></html>", color, connected);

            m_statusLabel.setText(status);
        }
    }
}
