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
 * ------------------------------------------------------------------------
 */

package org.knime.ext.sharepoint.lists.node.updater;

import java.util.Optional;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.StringValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.RowIDChoice;
import org.knime.core.webui.node.dialog.defaultdialog.setting.singleselection.StringOrEnum;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.ext.sharepoint.parameters.TimeoutParameters;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.persistence.legacy.SettingsModelColumnNameMigration;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.util.CompatibleColumnsProvider;

/**
 * Node parameters for SharePoint Online List Updater.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
@LoadDefaultsForAbsentFields
final class SharepointListUpdaterNodeParameters implements NodeParameters {

    @ValueReference(SharepointSiteParameters.Ref.class)
    SharepointSiteParameters m_site = new SharepointSiteParameters();

    SharepointListParameters.Basic m_list = new SharepointListParameters.Basic();

    @Widget(title = "ID column", description = """
            A string column from the input table that specifies the list item IDs to update.
            The list item IDs can be retrieved using the <a href="https://hub.knime.com/n/vGN09MfWXdqsgbXo">SharePoint
            Online List Reader</a>, which outputs a string column called "ID".""")
    @ChoicesProvider(IdColumnProvider.class)
    @Migration(ColumnSelectionMigration.class)
    @ValueProvider(DefaultIdColumnProvider.class)
    StringOrEnum<RowIDChoice> m_idColumn = new StringOrEnum<>("ID");

    @PersistEmbedded
    TimeoutParameters m_timeout = new TimeoutParameters();

    static final class IdColumnProvider extends CompatibleColumnsProvider.StringColumnsProvider {
        @Override
        public int getInputTableIndex(final NodeParametersInput parametersInput) {
            return 1;
        }
    }

    static final class DefaultIdColumnProvider implements StateProvider<StringOrEnum<RowIDChoice>> {

        private static final String PREFERRED = "ID";

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public StringOrEnum<RowIDChoice> computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {
            final var table = parametersInput.getInTable(1);

            if (table.isEmpty()) {
                return new StringOrEnum<>(PREFERRED);
            }

            final var spec = table.get().getDataTableSpec();

            final var pref = Optional.ofNullable(spec.getColumnSpec(PREFERRED)) //
                    .map(DataColumnSpec::getType) //
                    .filter(t -> t.isCompatible(StringValue.class));

            if (pref.isPresent()) {
                return new StringOrEnum<>(PREFERRED);
            }

            // return first compatible column or row id
            return spec.stream() //
                    .filter(s -> s.getType().isCompatible(StringValue.class)) //
                    .map(DataColumnSpec::getName) //
                    .map(StringOrEnum<RowIDChoice>::new) //
                    .findFirst() //
                    .orElse(new StringOrEnum<>(RowIDChoice.ROW_ID));
        }
    }

    static final class ColumnSelectionMigration extends SettingsModelColumnNameMigration {
        protected ColumnSelectionMigration() {
            super("id_column");
        }
    }

    @Override
    public void validate() throws InvalidSettingsException {
        m_site.validate();
        m_list.validate();
        m_timeout.validate();
    }
}
