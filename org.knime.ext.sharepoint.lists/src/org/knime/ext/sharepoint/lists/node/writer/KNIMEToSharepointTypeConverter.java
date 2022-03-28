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
 *   2022-03-02 (lars.schweikardt): created
 */
package org.knime.ext.sharepoint.lists.node.writer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.util.Pair;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.microsoft.graph.models.extensions.BooleanColumn;
import com.microsoft.graph.models.extensions.ColumnDefinition;
import com.microsoft.graph.models.extensions.NumberColumn;
import com.microsoft.graph.models.extensions.TextColumn;

/**
 * Utility class which handles the conversion from {@link DataType} to
 * Sharepoint types.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class KNIMEToSharepointTypeConverter {

    private KNIMEToSharepointTypeConverter() {
        // utility class
    }

    /**
     * Map which holds a Pair of Functions which can convert data values to
     * {@link JsonPrimitive} and creates named {@link ColumnDefinition} based on a
     * DataType
     */
    static final Map<DataType, Pair<Function<DataCell, JsonElement>, Function<String, ColumnDefinition>>> TYPE_CONVERTER = new HashMap<>();

    /** If no suitable converter is available */
    static final Pair<Function<DataCell, JsonElement>, Function<String, ColumnDefinition>> DEFAULT_CONVERTER = Pair
            .create(s -> new JsonPrimitive(s.toString()), KNIMEToSharepointTypeConverter::createStringColDefiniton);

    static {
        TYPE_CONVERTER.put(StringCell.TYPE, Pair.create(s -> new JsonPrimitive(s.toString()),
                KNIMEToSharepointTypeConverter::createStringColDefiniton));
        TYPE_CONVERTER.put(IntCell.TYPE, Pair.create(s -> new JsonPrimitive(((IntCell) s).getIntValue()),
                KNIMEToSharepointTypeConverter::createNumberColDefiniton));
        TYPE_CONVERTER.put(DoubleCell.TYPE, Pair.create(KNIMEToSharepointTypeConverter::doubleParser,
                KNIMEToSharepointTypeConverter::createNumberColDefiniton));
        TYPE_CONVERTER.put(LongCell.TYPE, Pair.create(s -> new JsonPrimitive(((LongCell) s).getLongValue()),
                KNIMEToSharepointTypeConverter::createNumberColDefiniton));
        TYPE_CONVERTER.put(BooleanCell.TYPE, Pair.create(s -> new JsonPrimitive(((BooleanCell) s).getBooleanValue()),
                KNIMEToSharepointTypeConverter::createBooleanColDefiniton));
    }

    private static JsonElement doubleParser(final DataCell dataCell) {
        final var val = ((DoubleValue) dataCell).getDoubleValue();
        if (Double.isInfinite(val) || Double.isNaN(val)) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(val);
    }

    private static ColumnDefinition createColDefintion(final String name) {
        final var colDef = new ColumnDefinition();
        colDef.name = name;
        return colDef;
    }

    private static ColumnDefinition createStringColDefiniton(final String name) {
        final ColumnDefinition colDef = createColDefintion(name);
        final var textCol = new TextColumn();
        textCol.allowMultipleLines = true;
        colDef.text = textCol;
        return colDef;
    }

    private static ColumnDefinition createBooleanColDefiniton(final String name) {
        final ColumnDefinition colDef = createColDefintion(name);
        colDef.msgraphBoolean = new BooleanColumn();
        return colDef;
    }

    private static ColumnDefinition createNumberColDefiniton(final String name) {
        final ColumnDefinition colDef = createColDefintion(name);
        colDef.number = new NumberColumn();
        return colDef;
    }

}
