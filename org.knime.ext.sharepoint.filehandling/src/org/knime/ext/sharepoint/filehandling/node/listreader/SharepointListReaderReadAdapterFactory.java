package org.knime.ext.sharepoint.filehandling.node.listreader;

import org.knime.core.data.DataType;
import org.knime.core.data.convert.map.MappingFramework;
import org.knime.core.data.convert.map.ProducerRegistry;
import org.knime.core.data.convert.map.SimpleCellValueProducerFactory;
import org.knime.core.data.def.StringCell;
import org.knime.filehandling.core.node.table.reader.ReadAdapter;
import org.knime.filehandling.core.node.table.reader.ReadAdapter.ReadAdapterParams;
import org.knime.filehandling.core.node.table.reader.ReadAdapterFactory;

/**
 * Factory for {@link SharepointListReaderReadAdapter} objects.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 *
 */
public enum SharepointListReaderReadAdapterFactory implements ReadAdapterFactory<Class<?>, String> {
    /**
     * The singleton instance.
     */
    INSTANCE;

    private static final ProducerRegistry<Class<?>, SharepointListReaderReadAdapter> PRODUCER_REGISTRY = initializeProducerRegistry();

    private static ProducerRegistry<Class<?>, SharepointListReaderReadAdapter> initializeProducerRegistry() {
        final ProducerRegistry<Class<?>, SharepointListReaderReadAdapter> registry = MappingFramework
                .forSourceType(SharepointListReaderReadAdapter.class);
        registry.register(new SimpleCellValueProducerFactory<>(String.class, String.class,
                SharepointListReaderReadAdapterFactory::readStringFromSource));
        return registry;
    }

    private static String readStringFromSource(final SharepointListReaderReadAdapter source,
            final ReadAdapterParams<SharepointListReaderReadAdapter, SharepointListReaderConfig> params) {
        return source.get(params);
    }

    @Override
    public ReadAdapter<Class<?>, String> createReadAdapter() {
        return new SharepointListReaderReadAdapter();
    }

    @Override
    public ProducerRegistry<Class<?>, ? extends ReadAdapter<Class<?>, String>> getProducerRegistry() {
        return PRODUCER_REGISTRY;
    }

    @Override
    public DataType getDefaultType(final Class<?> type) {
        return StringCell.TYPE;
    }

}
