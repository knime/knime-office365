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
 *   2025-03 (AI Migration): created
 */
package org.knime.ext.sharepoint.lists.node.reader;

import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.DeepCopy;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters;
import org.knime.ext.sharepoint.lists.node.reader.SharepointListReaderNodeParameters.DataParameters.LimitRows.LimitRowsPersistor;
import org.knime.ext.sharepoint.lists.node.reader.SharepointListReaderNodeParameters.DataParameters.SkipRows.SkipRowsPersistor;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.ext.sharepoint.parameters.TimeoutParameters;
import org.knime.ext.sharepoint.parameters.TimeoutsSection;
import org.knime.filehandling.core.node.table.reader.config.DefaultTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.ReaderSpecificConfig;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.OptionalWidget;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;

/**
 * Node parameters for the SharePoint List Reader node. Implements both
 * {@link NodeParameters} for modern UI and {@link ReaderSpecificConfig} for the
 * table reader framework.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public final class SharepointListReaderNodeParameters
        implements NodeParameters, ReaderSpecificConfig<SharepointListReaderNodeParameters> {

    @Advanced
    @Section(title = "Data")
    @Before(TimeoutsSection.class)
    interface DataSection {
    }

    SharepointParameters m_settings = new SharepointParameters();

    static class SharepointParameters implements NodeParameters {

        @ValueReference(SharepointSiteParameters.Ref.class)
        SharepointSiteParameters m_site = new SharepointSiteParameters();

        SharepointListParameters.WithSystemLists m_list = new SharepointListParameters.WithSystemLists();
    }

    @Persist(configKey = "limit_rows")
    DataParameters m_data = new DataParameters();

    @PersistWithin("advanced")
    TimeoutParameters m_timeout = new TimeoutParameters();

    @Override
    public SharepointListReaderNodeParameters copy() {
        final var result = new SharepointListReaderNodeParameters();
        result.m_settings.m_site = m_settings.m_site.copy();
        result.m_settings.m_list = m_settings.m_list.copy();
        result.m_timeout = m_timeout.copy();
        result.m_data = m_data.copy();
        return result;
    }

    void saveTo(final DefaultTableReadConfig<SharepointListReaderNodeParameters> config) {
        final var rsc = config.getReaderSpecificConfig();
        rsc.m_data = m_data;
        rsc.m_settings = m_settings;
        rsc.m_timeout = m_timeout;
        m_data.saveTo(config);
    }

    @Override
    public void validate() throws InvalidSettingsException {
        m_settings.m_site.validate();
        m_settings.m_list.validate();
        m_timeout.validate();
        m_data.validate();
    }

    /**
     * @return parameters containing SharePoint site information
     */
    public SharepointSiteParameters getSiteParameters() {
        return m_settings.m_site;
    }

    /**
     * @return parameters containing list information
     */
    public SharepointListParameters getListParameters() {
        return m_settings.m_list;
    }

    /**
     * Parameters for limiting rows.
     */
    static final class DataParameters implements NodeParameters, DeepCopy<DataParameters> {

        static final class SkipRows implements NodeParameters {

            private static final long DEFAULT = 1;

            @Widget(title = "Skip rows", description = """
                    If enabled, skip the specified number of data rows starting from the beginning
                    of the list.""")
            @ValueReference(SkipRowsRef.class)
            @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
            @OptionalWidget(defaultProvider = SkipRowsDefault.class)
            @Layout(DataSection.class)
            Optional<Long> m_diplayedSkipRows = Optional.empty();

            @ValueReference(LastSkipRowsRef.class)
            @ValueProvider(SkipRowsProvider.class)
            long m_skipRows = DEFAULT;

            static final class LastSkipRowsRef implements ParameterReference<Long> {
            }

            // Set the default so that the value is restored
            static final class SkipRowsDefault implements OptionalWidget.DefaultValueProvider<Long> {

                private Supplier<Long> m_lastSkipRow;

                @Override
                public void init(final StateProviderInitializer initializer) {
                    initializer.computeBeforeOpenDialog();
                    m_lastSkipRow = initializer.computeFromValueSupplier(LastSkipRowsRef.class);
                }

                @Override
                public Long computeState(final NodeParametersInput parametersInput)
                        throws StateComputationFailureException {
                    return m_lastSkipRow.get();
                }
            }

            static final class SkipRowsProvider implements StateProvider<Long> {

                private Supplier<Optional<Long>> m_skipRows;

                @Override
                public void init(final StateProviderInitializer initializer) {
                    initializer.computeBeforeOpenDialog();
                    m_skipRows = initializer.computeFromValueSupplier(SkipRowsRef.class);
                }

                @Override
                public Long computeState(final NodeParametersInput parametersInput)
                        throws StateComputationFailureException {
                    return m_skipRows.get().orElseThrow(StateComputationFailureException::new);
                }
            }

            static final class SkipRowsPersistor implements NodeParametersPersistor<SkipRows> {
                @Override
                public SkipRows load(final NodeSettingsRO settings) throws InvalidSettingsException {
                    final var result = new SkipRows();

                    result.m_skipRows = settings.getLong("number_of_rows_to_skip", DEFAULT);
                    result.m_diplayedSkipRows = Optional.of(result.m_skipRows)
                            .filter(l -> settings.getBoolean("skip_data_rows", false));
                    return result;
                }

                @Override
                public void save(final SkipRows params, final NodeSettingsWO settings) {
                    settings.addBoolean("skip_data_rows", params.m_diplayedSkipRows.isPresent());
                    settings.addLong("number_of_rows_to_skip", params.m_skipRows);
                }

                @Override
                public String[][] getConfigPaths() {
                    return new String[][] { //
                            { "skip_data_rows" }, //
                            { "number_of_rows_to_skip" }, //
                    };
                }
            }

            static final class SkipRowsRef implements ParameterReference<Optional<Long>> {
            }

            @Override
            public void validate() throws InvalidSettingsException {
                CheckUtils.checkSetting(m_diplayedSkipRows.filter(l -> l < 0).isEmpty(),
                        "Number of rows to skip must be non-negative.");
            }

        }

        static final class LimitRows implements NodeParameters {

            private static final long DEFAULT = 50;

            @ValueReference(LimitRowsRef.class)
            @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
            @OptionalWidget(defaultProvider = LimitRowsDefault.class)
            @Widget(title = "Limit data rows", description = """
                    If enabled, only the specified number of data rows will be read
                    after the skipped rows.""")
            @Layout(DataSection.class)
            Optional<Long> m_diplayedLimitRows = Optional.empty();

            @ValueReference(LastLimitRowsRef.class)
            @ValueProvider(LimitRowsProvider.class)
            long m_limitRows = DEFAULT;

            static final class LastLimitRowsRef implements ParameterReference<Long> {
            }

            // Set the default so that the value is restored
            static final class LimitRowsDefault implements OptionalWidget.DefaultValueProvider<Long> {

                private Supplier<Long> m_lastLimitRow;

                @Override
                public void init(final StateProviderInitializer initializer) {
                    initializer.computeBeforeOpenDialog();
                    m_lastLimitRow = initializer.computeFromValueSupplier(LastLimitRowsRef.class);
                }

                @Override
                public Long computeState(final NodeParametersInput parametersInput)
                        throws StateComputationFailureException {
                    return m_lastLimitRow.get();
                }
            }

            static final class LimitRowsProvider implements StateProvider<Long> {

                private Supplier<Optional<Long>> m_limitRows;

                @Override
                public void init(final StateProviderInitializer initializer) {
                    initializer.computeBeforeOpenDialog();
                    m_limitRows = initializer.computeFromValueSupplier(LimitRowsRef.class);
                }

                @Override
                public Long computeState(final NodeParametersInput parametersInput)
                        throws StateComputationFailureException {
                    return m_limitRows.get().orElseThrow(StateComputationFailureException::new);
                }
            }

            static final class LimitRowsPersistor implements NodeParametersPersistor<LimitRows> {
                @Override
                public LimitRows load(final NodeSettingsRO settings) throws InvalidSettingsException {
                    final var result = new LimitRows();

                    result.m_limitRows = settings.getLong("max_data_rows", DEFAULT);
                    result.m_diplayedLimitRows = Optional.of(result.m_limitRows)
                            .filter(l -> settings.getBoolean("limit_data_rows", false));
                    return result;
                }

                @Override
                public void save(final LimitRows params, final NodeSettingsWO settings) {
                    settings.addBoolean("limit_data_rows", params.m_diplayedLimitRows.isPresent());
                    settings.addLong("max_data_rows", params.m_limitRows);
                }

                @Override
                public String[][] getConfigPaths() {
                    return new String[][] { //
                            { "limit_data_rows" }, //
                            { "max_data_rows" }, //
                    };
                }
            }

            static final class LimitRowsRef implements ParameterReference<Optional<Long>> {
            }

            @Override
            public void validate() throws InvalidSettingsException {
                CheckUtils.checkSetting(m_diplayedLimitRows.filter(l -> l < 0).isEmpty(),
                        "Maximum number of rows must be non-negative.");
            }
        }

        @Persistor(SkipRowsPersistor.class)
        @PersistEmbedded
        SkipRows m_skipRows = new SkipRows();

        @Persistor(LimitRowsPersistor.class)
        @PersistEmbedded
        LimitRows m_limitRows = new LimitRows();

        @Override
        public void validate() throws InvalidSettingsException {
            m_skipRows.validate();
            m_limitRows.validate();
        }

        @Override
        public DataParameters copy() {
            final var result = new DataParameters();
            result.m_skipRows.m_diplayedSkipRows = m_skipRows.m_diplayedSkipRows;
            result.m_skipRows.m_skipRows = m_skipRows.m_skipRows;
            result.m_limitRows.m_diplayedLimitRows = m_limitRows.m_diplayedLimitRows;
            result.m_limitRows.m_limitRows = m_limitRows.m_limitRows;
            return result;
        }

        void loadFrom(final DefaultTableReadConfig<SharepointListReaderNodeParameters> config) {
            m_skipRows.m_diplayedSkipRows = config.skipRows() ? Optional.of(config.getNumRowsToSkip())
                    : Optional.empty();
            m_skipRows.m_skipRows = config.getNumRowsToSkip();
            m_limitRows.m_diplayedLimitRows = config.limitRows() ? Optional.of(config.getMaxRows()) : Optional.empty();
            m_limitRows.m_limitRows = config.getMaxRows();
        }

        void saveTo(final DefaultTableReadConfig<SharepointListReaderNodeParameters> config) {
            config.setSkipRows(m_skipRows.m_diplayedSkipRows.isPresent());
            config.setNumRowsToSkip(m_skipRows.m_diplayedSkipRows.orElse(SkipRows.DEFAULT));
            config.setLimitRows(m_limitRows.m_diplayedLimitRows.isPresent());
            config.setMaxRows(m_limitRows.m_diplayedLimitRows.orElse(LimitRows.DEFAULT));
        }

    }
}
