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
 *   Feb 5, 2020 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.ext.sharepoint.lists.node.reader.mapping;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.knime.core.data.DataType;
import org.knime.core.data.blob.BinaryObjectDataCell;
import org.knime.core.data.convert.map.BooleanCellValueProducer;
import org.knime.core.data.convert.map.DoubleCellValueProducer;
import org.knime.core.data.convert.map.IntCellValueProducer;
import org.knime.core.data.convert.map.LongCellValueProducer;
import org.knime.core.data.convert.map.MappingException;
import org.knime.core.data.convert.map.MappingFramework;
import org.knime.core.data.convert.map.PrimitiveCellValueProducer;
import org.knime.core.data.convert.map.ProducerRegistry;
import org.knime.core.data.convert.map.ProductionPath;
import org.knime.core.data.convert.map.SimpleCellValueProducerFactory;
import org.knime.core.data.convert.map.SupplierCellValueProducerFactory;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.time.localdate.LocalDateCellFactory;
import org.knime.core.data.time.localtime.LocalTimeCellFactory;
import org.knime.ext.sharepoint.lists.node.reader.SharepointListReaderConfig;
import org.knime.filehandling.core.node.table.reader.HierarchyAwareProductionPathProvider;
import org.knime.filehandling.core.node.table.reader.ReadAdapter;
import org.knime.filehandling.core.node.table.reader.ReadAdapter.ReadAdapterParams;
import org.knime.filehandling.core.node.table.reader.ReadAdapterFactory;
import org.knime.filehandling.core.node.table.reader.type.hierarchy.TreeTypeHierarchy;
import org.knime.filehandling.core.node.table.reader.type.hierarchy.TypeTester;
import org.knime.filehandling.core.node.table.reader.util.MultiTableUtils;

/**
 * Factory for StringReadAdapter objects.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @noreference non-public API
 */
public enum SharepointListReadAdapterFactory implements ReadAdapterFactory<DataType, String> {
    /**
     * The singleton instance.
     */
    INSTANCE;

    private static final ProducerRegistry<DataType, SharepointListReadAdapter> PRODUCER_REGISTRY = initializeProducerRegistry();

    /**
     * The type hierarchy of the CSV Reader.
     */
    public static final TreeTypeHierarchy<DataType, DataType> TYPE_HIERARCHY = createHierarchy(
            new SharepointListReaderConfig()).createTypeFocusedHierarchy();

    private static ProducerRegistry<DataType, SharepointListReadAdapter> initializeProducerRegistry() {
        final ProducerRegistry<DataType, SharepointListReadAdapter> registry = MappingFramework
                .forSourceType(SharepointListReadAdapter.class);
        registry.register(new SimpleCellValueProducerFactory<>(StringCell.TYPE, String.class,
                SharepointListReadAdapterFactory::readStringFromSource));
        registry.register(new SupplierCellValueProducerFactory<>(BooleanCell.TYPE, Boolean.class,
                StringToBooleanCellValueProducer::new));
        registry.register(
                new SupplierCellValueProducerFactory<>(IntCell.TYPE, Integer.class, StringToIntCellValueProducer::new));
        registry.register(new SupplierCellValueProducerFactory<>(DoubleCell.TYPE, Double.class,
                StringToDoubleCellValueProducer::new));
        registry.register(
                new SupplierCellValueProducerFactory<>(LongCell.TYPE, Long.class, StringToLongCellValueProducer::new));
        registry.register(new SimpleCellValueProducerFactory<>(LocalDateCellFactory.TYPE, LocalDate.class,
                SharepointListReadAdapterFactory::readLocalDateFromSource));
        registry.register(new SimpleCellValueProducerFactory<>(LocalTimeCellFactory.TYPE, LocalTime.class,
                SharepointListReadAdapterFactory::readLocalTimeFromSource));
        return registry;
    }

    private static String readStringFromSource(final SharepointListReadAdapter source,
            final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params) {
        return source.get(params);
    }

    private static LocalDate readLocalDateFromSource(final SharepointListReadAdapter source,
            final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params) {
        final var val = source.get(params);
        return val == null ? null : LocalDate.parse(val);
    }

    private static LocalTime readLocalTimeFromSource(final SharepointListReadAdapter source,
            final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params) {
        final var val = source.get(params);
        return LocalTime.parse(val);
    }

    private static InputStream readByteFieldsFromSource(final SharepointListReadAdapter source,
            final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params) {
        final var val = source.get(params);
        return val == null ? null : new ByteArrayInputStream(val.getBytes());
    }

    private abstract static class AbstractReadAdapterToPrimitiveCellValueProducer<S extends ReadAdapter<?, ?>, T>
            implements PrimitiveCellValueProducer<S, T, ReadAdapterParams<S, SharepointListReaderConfig>> {

        @Override
        public final boolean producesMissingCellValue(final S source,
                final ReadAdapterParams<S, SharepointListReaderConfig> params) throws MappingException {
            return source.get(params) == null;
        }
    }

    private static class StringToBooleanCellValueProducer
            extends AbstractReadAdapterToPrimitiveCellValueProducer<SharepointListReadAdapter, Boolean> implements
            BooleanCellValueProducer<SharepointListReadAdapter, ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig>> {

        @Override
        public boolean produceBooleanCellValue(final SharepointListReadAdapter source,
                final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params)
                throws MappingException {
            return Boolean.parseBoolean(source.get(params));
        }
    }

    private static class StringToIntCellValueProducer
            extends AbstractReadAdapterToPrimitiveCellValueProducer<SharepointListReadAdapter, Integer> implements
            IntCellValueProducer<SharepointListReadAdapter, ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig>> {

        @Override
        public int produceIntCellValue(final SharepointListReadAdapter source,
                final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params)
                throws MappingException {
            return Integer.parseInt(source.get(params));
        }
    }

    private static class StringToDoubleCellValueProducer
            extends AbstractReadAdapterToPrimitiveCellValueProducer<SharepointListReadAdapter, Double> implements
            DoubleCellValueProducer<SharepointListReadAdapter, ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig>> {

        @Override
        public double produceDoubleCellValue(final SharepointListReadAdapter source,
                final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params)
                throws MappingException {
            return Double.parseDouble(source.get(params));
        }
    }

    private static class StringToLongCellValueProducer
            extends AbstractReadAdapterToPrimitiveCellValueProducer<SharepointListReadAdapter, Long> implements
            LongCellValueProducer<SharepointListReadAdapter, ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig>> {

        @Override
        public long produceLongCellValue(final SharepointListReadAdapter source,
                final ReadAdapterParams<SharepointListReadAdapter, SharepointListReaderConfig> params)
                throws MappingException {
            return Long.parseLong(source.get(params));
        }
    }


    @Override
    public ReadAdapter<DataType, String> createReadAdapter() {
        return new SharepointListReadAdapter();
    }

    @Override
    public ProducerRegistry<DataType, SharepointListReadAdapter> getProducerRegistry() {
        return PRODUCER_REGISTRY;
    }

    @Override
    public DataType getDefaultType(final DataType type) {
        return type;
    }

    /**
     * @return a {@link HierarchyAwareProductionPathProvider}
     */
    public HierarchyAwareProductionPathProvider<DataType> createProductionPathProvider() {
        final Set<DataType> reachableDataTypes = new HashSet<>(
                MultiTableUtils.extractReachableKnimeTypes(PRODUCER_REGISTRY));
        // the binary object type can't be read with the CSV Reader
        // it is only in the registry because the SAP Theobald Reader needs it
        reachableDataTypes.remove(BinaryObjectDataCell.TYPE);
        return new HierarchyAwareProductionPathProvider<>(getProducerRegistry(), TYPE_HIERARCHY, this::getDefaultType,
                SharepointListReadAdapterFactory::isValidPathFor, reachableDataTypes);
    }

    private static boolean isValidPathFor(final DataType type, final ProductionPath path) {
        if (type == StringCell.TYPE) {
            final DataType knimeType = path.getDestinationType();
            // exclude numeric types for String because
            // a) The default String -> Number converters don't use the user-specified
            // decimal and thousands separators
            // b) the conversion is likely to fail because otherwise the type of the column
            // would be numeric
            return !isNumeric(knimeType);
        } else {
            return true;
        }
    }

    private static boolean isNumeric(final DataType knimeType) {
        return knimeType.equals(DoubleCell.TYPE)//
                || knimeType.equals(LongCell.TYPE)//
                || knimeType.equals(IntCell.TYPE);
    }

    static TreeTypeHierarchy<DataType, String> createHierarchy(final SharepointListReaderConfig config) {
        return TreeTypeHierarchy.builder(createTypeTester(StringCell.TYPE, t -> {
        })).addType(StringCell.TYPE, createTypeTester(DoubleCell.TYPE, Double::parseDouble))
                .addType(DoubleCell.TYPE, createTypeTester(LongCell.TYPE, Long::parseLong))
                .addType(LongCell.TYPE, createTypeTester(IntCell.TYPE, Integer::parseInt))
                .addType(StringCell.TYPE,
                        TypeTester.createTypeTester(BooleanCell.TYPE,
                                s -> s.strip().equalsIgnoreCase("false") || s.strip().equalsIgnoreCase("true")))
                .build();
    }

    private static TypeTester<DataType, String> createTypeTester(final DataType type, final Consumer<String> tester) {
        return TypeTester.createTypeTester(type, consumerToPredicate(tester));
    }

    private static Predicate<String> consumerToPredicate(final Consumer<String> tester) {
        return s -> {
            try {
                tester.accept(s);
                return true;
            } catch (NumberFormatException ex) {
                return false;
            }
        };
    }

}
