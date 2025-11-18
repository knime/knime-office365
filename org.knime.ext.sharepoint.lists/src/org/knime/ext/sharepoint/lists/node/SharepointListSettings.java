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
 *   2021-09-29 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint.lists.node;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.ext.sharepoint.lists.node.SharepointListSettingsPanel.ListSettings;
import org.knime.ext.sharepoint.settings.AbstractSharePointSettings;

/**
 * Settings for nodes which make use of Sharepoint lists.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointListSettings extends AbstractSharePointSettings<SharepointListSettings> {

    /** Config key for the overwrite policy. */
    private static final String CFG_OVERWRITE_POLICY = "if_list_exists";

    private final ListSettings m_listSettings;

    private final SettingsModelString m_overwritePolicy;

    private boolean m_hasOverwriteOptions = false;

    /**
     * Constructor.
     */
    public SharepointListSettings() {
        this(true, false);
    }

    /**
     * Constructor.
     *
     * @param useSystemListsSettings
     *            whether to show the showSystemListSettings or hide them
     * @param hasOverwriteOptions
     *            flag whether or not the settings will have overwrite options
     */
    public SharepointListSettings(final boolean useSystemListsSettings, final boolean hasOverwriteOptions) {
        super();
        m_listSettings = new ListSettings(useSystemListsSettings);
        m_hasOverwriteOptions = hasOverwriteOptions;
        m_overwritePolicy = new SettingsModelString(CFG_OVERWRITE_POLICY, ListOverwritePolicy.FAIL.name());
    }

    private SharepointListSettings(final SharepointListSettings toCopy) {
        super(toCopy);
        m_listSettings = toCopy.getListSettings();
        m_overwritePolicy = toCopy.getOverwritePolicyModel();
    }

    @Override
    public SharepointListSettings copy(final SharepointListSettings settings) {
        return new SharepointListSettings(settings);
    }

    /**
     * Returns the settings model storing the selected {@link ListOverwritePolicy}.
     *
     * @return the settings model storing the selected {@link ListOverwritePolicy}
     */
    public SettingsModelString getOverwritePolicyModel() {
        return m_overwritePolicy;
    }

    /**
     * Sets the {@link ListOverwritePolicy} to the provided value.
     *
     * @param overwritePolicy
     *            the {@link ListOverwritePolicy} to set
     */
    public final void setOverwritePolicy(final ListOverwritePolicy overwritePolicy) {
        m_overwritePolicy.setStringValue(overwritePolicy.name());
    }

    /**
     * Returns the selected {@link ListOverwritePolicy}.
     *
     * @return the selected {@link ListOverwritePolicy}
     */
    public final ListOverwritePolicy getOverwritePolicy() {
        return ListOverwritePolicy.valueOf(m_overwritePolicy.getStringValue());
    }

    /**
     * @return the {@link ListSettings}
     */
    public ListSettings getListSettings() {
        return m_listSettings;
    }

    @Override
    public void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.loadSettingsFrom(settings);
        m_listSettings.loadSettingsFrom(settings);
        if (m_hasOverwriteOptions) {
            m_overwritePolicy.loadSettingsFrom(settings);
        }
    }

    @Override
    public void saveSettingsTo(final NodeSettingsWO settings) {
        super.saveSettingsTo(settings);
        m_listSettings.saveSettingsTo(settings);
        if (m_hasOverwriteOptions) {
            m_overwritePolicy.saveSettingsTo(settings);
        }
    }

    @Override
    public void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.validateSettings(settings);
        m_listSettings.validateSettings(settings);
        if (m_hasOverwriteOptions) {
            m_overwritePolicy.validateSettings(settings);
        }
    }
}
