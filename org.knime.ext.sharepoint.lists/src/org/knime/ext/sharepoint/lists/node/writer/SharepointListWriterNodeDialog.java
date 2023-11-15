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
 *   14 Feb 2022 (Lars Schweikardt, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.lists.node.writer;

import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.credentials.base.oauth.api.JWTCredential;
import org.knime.ext.sharepoint.dialog.TimeoutPanel;
import org.knime.ext.sharepoint.lists.node.SharepointListSettingsPanel;
import org.knime.filehandling.core.util.GBCBuilder;

/**
 * “SharePoint List Writer” implementation of a {@link NodeDialogPane}.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public class SharepointListWriterNodeDialog extends NodeDialogPane {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListWriterNodeDialog.class);

    private final SharepointListWriterConfig m_config;

    private final SharepointListSettingsPanel m_listSettingsPanel;

    private final TimeoutPanel m_timeoutPanel;

    SharepointListWriterNodeDialog() {
        m_config = new SharepointListWriterConfig();
        m_listSettingsPanel = new SharepointListSettingsPanel(m_config.getSharepointListSettings(), true, true);
        m_timeoutPanel = new TimeoutPanel(m_config.getSharepointListSettings().getTimeoutSettings());

        addTab("Settings", createSettingsPanel());
        addTab("Advanced", createAdvancedTab());
    }

    private JPanel createSettingsPanel() {
        final var panel = new JPanel(new GridBagLayout());
        final var gbc = new GBCBuilder().resetPos().weight(1, 0).anchorLineStart().fillHorizontal();
        panel.add(m_listSettingsPanel, gbc.build());
        panel.add(Box.createVerticalGlue(), gbc.setWeightY(1).build());

        return panel;
    }

    private JPanel createAdvancedTab() {
        final var panel = new JPanel(new GridBagLayout());

        final var gbc = new GBCBuilder().resetPos().weight(1, 0).insets(5, 0, 5, 5).anchorFirstLineStart()
                .fillHorizontal();
        panel.add(m_timeoutPanel, gbc.build());
        panel.add(Box.createHorizontalGlue(), gbc.setWeightY(1).build());

        return panel;
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_config.saveSettings(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {

        try {
            m_config.loadSettings(settings);
        } catch (InvalidSettingsException ex) {
            LOGGER.error("An unexpected error occured during the loading of the settings.", ex);
        }

        try {
            final var credential = ((CredentialPortObjectSpec) specs[0]).resolveCredential(JWTCredential.class);
            m_listSettingsPanel.settingsLoaded(credential);
        } catch (NoSuchCredentialException ex) {
            throw new NotConfigurableException(ex.getMessage(), ex);
        }
    }

    @Override
    public void onOpen() {
        m_listSettingsPanel.triggerFetching(false);
    }

    @Override
    public void onClose() {
        m_listSettingsPanel.onClose();
    }
}
