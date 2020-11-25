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
package org.knime.ext.microsoft.authentication.providers.oauth2.interactive;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.ext.microsoft.authentication.providers.oauth2.MSALAuthProviderEditor;
import org.knime.ext.microsoft.authentication.providers.oauth2.ScopesEditComponent;
import org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage.StorageEditor;
import org.knime.filehandling.core.defaultnodesettings.ExceptionUtil;

import com.microsoft.aad.msal4j.MsalException;

/**
 * Editor component for the {@link InteractiveAuthProvider}.
 *
 * @author Alexander Bondaletov
 */
public class InteractiveAuthProviderEditor extends MSALAuthProviderEditor<InteractiveAuthProvider> {

    private static final NodeLogger LOG = NodeLogger.getLogger(InteractiveAuthProviderEditor.class);

    private final NodeDialogPane m_parentNodeDialog;

    private JButton m_loginBtn;
    private JButton m_cancelBtn;
    private JLabel m_statusLabel;
    private StorageEditor m_storageEditor;
    private SwingWorkerWithContext<?, ?> m_worker;

    /**
     * Creates new instance.
     *
     * @param provider
     *            The auth provider.
     * @param nodeDialog
     *
     */
    public InteractiveAuthProviderEditor(final InteractiveAuthProvider provider, final NodeDialogPane nodeDialog) {
        super(provider);
        m_parentNodeDialog = nodeDialog;
    }

    @Override
    public void onShown() {
        triggerLoginStatusWorker();
        m_storageEditor.onShown();
    }

    private void triggerLoginStatusWorker() {
        cancelLogin();
        m_worker = new LoginStatusWorker();
        m_worker.execute();
    }

    @Override
    protected JComponent createContentPane() {
        Box box = new Box(BoxLayout.PAGE_AXIS);
        box.add(createButtonsPanel());
        box.add(Box.createVerticalStrut(10));

        m_storageEditor = new StorageEditor(m_provider.getStorageSettings(), m_parentNodeDialog,
                this::triggerLoginStatusWorker);
        box.add(m_storageEditor);
        box.add(Box.createVerticalStrut(10));

        box.add(new ScopesEditComponent(m_provider.getScopesModel(), m_provider.getBlobStorageAccountModel()));
        box.add(Box.createVerticalGlue());
        return box;
    }

    private JPanel createButtonsPanel() {
        m_loginBtn = new JButton("Login");
        m_loginBtn.addActionListener(e -> onLogin());

        m_cancelBtn = new JButton("Cancel");
        m_cancelBtn.addActionListener(e -> cancelLogin());

        m_statusLabel = new JLabel();
        m_statusLabel.setMinimumSize(new Dimension(700, 0));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(m_loginBtn);
        panel.add(m_cancelBtn);
        panel.add(m_statusLabel);

        updateLoginStatus(LoginStatus.NOT_LOGGED_IN, null);
        return panel;
    }

    private void cancelLogin() {
        if (m_worker != null) {
            m_worker.cancel(true);
            m_worker = null;
        }
    }

    private void onLogin() {
        m_worker = new LoginWorker();
        m_worker.execute();
    }

    private static MsalException extractMsalException(final Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof MsalException) {
                return (MsalException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void updateLoggingIn() {
        m_loginBtn.setVisible(false);
        m_cancelBtn.setVisible(true);
        m_statusLabel.setText("Logging in...");
    }

    private void updateGettingLoginStatus() {
        m_loginBtn.setVisible(false);
        m_cancelBtn.setVisible(true);
        m_statusLabel.setText("Getting login status...");
    }

    private void updateLoginStatus(final LoginStatus loginStatus, final Throwable error) {
        if (loginStatus == LoginStatus.NOT_LOGGED_IN) {
            m_loginBtn.setVisible(true);
            m_cancelBtn.setVisible(false);

            final String msg;
            if (error == null) {
                msg = "Not logged in";
            } else {
                msg = formatException(error);
            }
            m_statusLabel.setText(String.format("<html><font color=%s>%s</font></html>", "#CC0000", msg));
        } else {
            m_loginBtn.setVisible(true);
            m_cancelBtn.setVisible(false);
            m_statusLabel.setText(String.format("<html><font color=%s>Logged in as %s</font></html>",
                    "#007F0E",
                    loginStatus.getUsername()));
        }
    }

    private static String formatException(final Throwable error) {
        String message = null;

        MsalException msalEx = extractMsalException(error);
        if (msalEx != null) {
            message = msalEx.getMessage();
        }

        if (message == null) {
            message = ExceptionUtil.getDeepestNIOErrorMessage(error);
        }

        if (message == null) {
            message = ExceptionUtil.getDeepestErrorMessage(error, true);
        }

        if (message == null) {
            message = String.format("An error occured (%s)", error.getClass().getSimpleName());
        }

        return message;
    }

    private class LoginWorker extends SwingWorkerWithContext<LoginStatus, Void> {

        LoginWorker() {
            updateLoggingIn();
        }

        @Override
        protected LoginStatus doInBackgroundWithContext() throws Exception {
            m_provider.validate();
            return m_provider.performLogin();
        }

        @Override
        protected void doneWithContext() {
            try {
                final LoginStatus loginStatus = get();
                updateLoginStatus(loginStatus, null);
            } catch (InterruptedException | CancellationException ex) {
                updateLoginStatus(LoginStatus.NOT_LOGGED_IN, null);
            } catch (ExecutionException ex) {
                updateLoginStatus(LoginStatus.NOT_LOGGED_IN, ex.getCause());
                LOG.error(ex.getCause().getMessage(), ex);
            }
        }

    }

    private class LoginStatusWorker extends SwingWorkerWithContext<LoginStatus, Void> {

        LoginStatusWorker() {
            updateGettingLoginStatus();
        }

        @Override
        protected LoginStatus doInBackgroundWithContext() throws Exception {
            return m_provider.getLoginStatus();
        }

        @Override
        protected void doneWithContext() {
            try {
                final LoginStatus loginStatus = get();
                updateLoginStatus(loginStatus, null);
            } catch (InterruptedException ex) {
                updateLoginStatus(LoginStatus.NOT_LOGGED_IN, null);
            } catch (ExecutionException ex) {
                updateLoginStatus(LoginStatus.NOT_LOGGED_IN, ex.getCause());
                LOG.error(ex.getCause().getMessage(), ex);
            }
        }
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        m_storageEditor.loadSettingsFrom(settings, specs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCancel() {
        cancelLogin();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose() {
        m_storageEditor.onClose();
    }

}
