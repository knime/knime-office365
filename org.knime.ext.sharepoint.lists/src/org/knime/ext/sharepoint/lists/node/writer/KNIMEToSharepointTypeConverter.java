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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.StringValue;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.time.duration.DurationCell;
import org.knime.core.data.time.duration.DurationValue;
import org.knime.core.data.time.localdate.LocalDateCell;
import org.knime.core.data.time.localdate.LocalDateValue;
import org.knime.core.data.time.localdatetime.LocalDateTimeCell;
import org.knime.core.data.time.localdatetime.LocalDateTimeValue;
import org.knime.core.data.time.localtime.LocalTimeCell;
import org.knime.core.data.time.localtime.LocalTimeValue;
import org.knime.core.data.time.period.PeriodCell;
import org.knime.core.data.time.period.PeriodValue;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCell;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeValue;
import org.knime.core.util.Pair;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.microsoft.graph.models.BooleanColumn;
import com.microsoft.graph.models.ColumnDefinition;
import com.microsoft.graph.models.DateTimeColumn;
import com.microsoft.graph.models.NumberColumn;
import com.microsoft.graph.models.TextColumn;

/**
 * Utility class which handles the conversion from {@link DataType} to
 * Sharepoint types.
 *
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
final class KNIMEToSharepointTypeConverter {

    // Upper threshold for double values
    private static final double DOUBLE_MAX_VALUE = 1.79E308;

    // Lower threshold for double values
    private static final double DOUBLE_MIN_VALUE = -1.79E308;

    // Max date which is supported by SharePoint
    private static final Instant MAX_INSTANT = Instant.parse("8900-12-31T23:59:59Z");

    // Min date which is supported by SharePoint
    private static final Instant MIN_INSTANT = Instant.parse("1900-01-01T00:00:00Z");

    // Used for generating new internal names from existing ones
    private static final Pattern INTERNAL_NAME = Pattern.compile("^(.*?)(\\d*)$"); // NOSONAR

    private KNIMEToSharepointTypeConverter() {
        // utility class
    }

    /**
     * Map which holds a Pair of Functions which can convert data values to
     * {@link JsonPrimitive} and creates named {@link ColumnDefinition} based on a
     * DataType. The function which creates a new column expects to be given a set
     * which contains previously given internal names in lower case and will update
     * that set with a new lowercase name accordingly.
     */
    static final Map<DataType, Pair<Function<DataCell, JsonElement>, //
            BiFunction<Set<String>, String, ColumnDefinition>>> TYPE_CONVERTER = new HashMap<>();

    /** If no suitable converter is available */
    static final Pair<Function<DataCell, JsonElement>, //
            BiFunction<Set<String>, String, ColumnDefinition>> DEFAULT_CONVERTER = Pair.create( //
                    KNIMEToSharepointTypeConverter::defaultStringConverter, //
                    KNIMEToSharepointTypeConverter::createStringColDefiniton);

    private static JsonPrimitive defaultStringConverter(final DataCell dataCell) {
        if (dataCell instanceof StringValue strCell) {
            return new JsonPrimitive(strCell.getStringValue());
        } else {
            // should never happen since we check in configure, just to be sure
            throw new IllegalArgumentException("DataCell does not implement StringValue");
        }
    }

    static {
        TYPE_CONVERTER.put(StringCell.TYPE, Pair.create(s -> new JsonPrimitive(s.toString()),
                KNIMEToSharepointTypeConverter::createStringColDefiniton));
        TYPE_CONVERTER.put(IntCell.TYPE, Pair.create(s -> new JsonPrimitive(((IntCell) s).getIntValue()),
                KNIMEToSharepointTypeConverter::createIntegerNumberColDefiniton));
        TYPE_CONVERTER.put(DoubleCell.TYPE, Pair.create(KNIMEToSharepointTypeConverter::doubleParser,
                KNIMEToSharepointTypeConverter::createDoubleNumberColDefiniton));
        TYPE_CONVERTER.put(LongCell.TYPE, Pair.create(KNIMEToSharepointTypeConverter::longParser,
                KNIMEToSharepointTypeConverter::createIntegerNumberColDefiniton));
        TYPE_CONVERTER.put(BooleanCell.TYPE, Pair.create(s -> new JsonPrimitive(((BooleanCell) s).getBooleanValue()),
                KNIMEToSharepointTypeConverter::createBooleanColDefiniton));
        TYPE_CONVERTER.put(DataType.getType(ZonedDateTimeCell.class),
                Pair.create(KNIMEToSharepointTypeConverter::zonedDateTimeParser,
                        KNIMEToSharepointTypeConverter::createDateTimeDefinition));
        TYPE_CONVERTER.put(DataType.getType(LocalDateTimeCell.class),
                Pair.create(KNIMEToSharepointTypeConverter::dateTimeParser,
                        KNIMEToSharepointTypeConverter::createDateTimeDefinition));
        TYPE_CONVERTER.put(DataType.getType(LocalTimeCell.class),
                Pair.create(KNIMEToSharepointTypeConverter::localTimeParser,
                        KNIMEToSharepointTypeConverter::createStringColDefiniton));
        TYPE_CONVERTER.put(DataType.getType(LocalDateCell.class), Pair.create(
                KNIMEToSharepointTypeConverter::localDateParser, KNIMEToSharepointTypeConverter::createDateDefinition));
        TYPE_CONVERTER.put(DataType.getType(DurationCell.class),
                Pair.create(KNIMEToSharepointTypeConverter::durationParser,
                        KNIMEToSharepointTypeConverter::createStringColDefiniton));
        TYPE_CONVERTER.put(DataType.getType(PeriodCell.class), Pair.create(KNIMEToSharepointTypeConverter::periodParser,
                KNIMEToSharepointTypeConverter::createStringColDefiniton));
    }

    private static JsonElement longParser(final DataCell dataCell) {
        final var val = ((LongCell) dataCell).getLongValue();
        if (BigDecimal.valueOf(val).stripTrailingZeros().precision() > 15) {
            throw new IllegalArgumentException(
                    "Long values with more than 15 significant digits are not supported. " + val);
        }
        return new JsonPrimitive(val);
    }

    private static JsonElement doubleParser(final DataCell dataCell) {
        final var val = ((DoubleValue) dataCell).getDoubleValue();
        checkDoubleValues(val);
        if (BigDecimal.valueOf(val).stripTrailingZeros().precision() > 15) {
            throw new IllegalArgumentException(
                    "Double values with more than 15 significant digits  are not supported. " + val);
        }
        return new JsonPrimitive(val);
    }

    private static boolean isASCIIAlphaNumeric(final int i) {
        return (i >= 'a' && i <= 'z') || (i >= 'A' && i <= 'Z') || (i >= '0' && i <= '9'); // NOSONAR
    }

    private static void checkDoubleValues(final double val) {
        if (Double.isInfinite(val) || Double.isNaN(val)) {
            throw new IllegalArgumentException("SharePoint does not support non-finite values. " + val);
        } else if (val < DOUBLE_MIN_VALUE) {
            throw new IllegalArgumentException(
                    "Double value is smaller than the supported smallest value of SharePoint. " + val);
        } else if (val > DOUBLE_MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Double value is bigger than the supported biggest value of SharePoint. " + val);
        }
    }

    private static JsonElement periodParser(final DataCell dataCell) {
        final var val = ((PeriodValue) dataCell).getPeriod().toString();
        return new JsonPrimitive(val);
    }

    private static JsonElement durationParser(final DataCell dataCell) {
        final var val = ((DurationValue) dataCell).getDuration().toString();
        return new JsonPrimitive(val);
    }

    private static JsonElement localDateParser(final DataCell dataCell) {
        final var val = ((LocalDateValue) dataCell).getLocalDate();
        final var instant = val.atStartOfDay(ZoneId.of("UTC")).toInstant();
        checkInstant(instant);
        return new JsonPrimitive(instant.toString());
    }

    private static JsonElement localTimeParser(final DataCell dataCell) {
        final var val = ((LocalTimeValue) dataCell).getLocalTime().toString();
        return new JsonPrimitive(val);
    }

    private static JsonElement zonedDateTimeParser(final DataCell dataCell) {
        final var val = ((ZonedDateTimeValue) dataCell).getZonedDateTime().toInstant().truncatedTo(ChronoUnit.SECONDS);
        checkInstant(val);
        return new JsonPrimitive(val.toString());
    }

    private static JsonElement dateTimeParser(final DataCell dataCell) {
        final var val = ((LocalDateTimeValue) dataCell).getLocalDateTime().toInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        checkInstant(val);
        return new JsonPrimitive(val.toString());
    }

    private static void checkInstant(final Instant val) {
        if (val.isBefore(MIN_INSTANT) || val.isAfter(MAX_INSTANT)) {
            throw new IllegalArgumentException(String.format(
                    "Local Date, Local Date Time or Zoned Date Time before %s or after %s are not supported. %s",
                    MIN_INSTANT.toString(), MAX_INSTANT.toString(), val.toString()));
        }
    }

    /**
     * Generate an internal name from a display name. The internal name will only
     * contain [A-Za-z0-9] and will be append by a number if that name already
     * exists
     *
     * @param existingLowerCase
     *            a set containing already existing internal, lower-case names. The
     *            newly generated name will be added to this set.
     * @param displayName
     *            the display name from which to generate the internal name
     * @return the newly generate internal name (case preserved)
     */
    private static String generateInternalName(final Set<String> existingLowerCase, final String displayName) {
        final var desiredName = displayName.chars() //
                .filter(KNIMEToSharepointTypeConverter::isASCIIAlphaNumeric) //
                .limit(250) // leave room for numbers
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        // because it's MS, use case insensitive names just to be sure
        if (existingLowerCase.add(desiredName.toLowerCase(Locale.ROOT))) {
            return desiredName;
        }

        final var m = INTERNAL_NAME.matcher(desiredName);
        m.find();

        final var prefix = m.group(1).isEmpty() ? "Column" : m.group(1);
        final var prefixLC = prefix.toLowerCase(Locale.ROOT);
        var num = m.group(2).isEmpty() ? BigInteger.ZERO : new BigInteger(m.group(2));

        while (!existingLowerCase.add(prefixLC + num)) {
            num = num.add(BigInteger.ONE);
        }

        final var result = prefix + num;
        if (result.length() > 255) {
            // F
            throw new IllegalArgumentException("Could not generate a sufficiently short internal name. "
                    + "Please reduce the number of columns and the length of their names.");
        }

        return result;
    }

    private static ColumnDefinition createColDefintion(final Set<String> existingLowerCase, final String name) {
        final var colDef = new ColumnDefinition();
        colDef.displayName = name;
        colDef.name = generateInternalName(existingLowerCase, name);
        return colDef;
    }

    private static ColumnDefinition createStringColDefiniton(final Set<String> existingLowerCase, final String name) {
        final var colDef = createColDefintion(existingLowerCase, name);
        final var textCol = new TextColumn();
        textCol.allowMultipleLines = true;
        colDef.text = textCol;
        return colDef;
    }

    private static ColumnDefinition createBooleanColDefiniton(final Set<String> existingLowerCase, final String name) {
        final var colDef = createColDefintion(existingLowerCase, name);
        colDef.msgraphBoolean = new BooleanColumn();
        return colDef;
    }

    private static ColumnDefinition createIntegerNumberColDefiniton(final Set<String> existingLowerCase,
            final String name) {
        final var colDef = createColDefintion(existingLowerCase, name);
        final var numberCol = new NumberColumn();
        numberCol.decimalPlaces = "none";
        colDef.number = numberCol;
        return colDef;
    }

    private static ColumnDefinition createDoubleNumberColDefiniton(final Set<String> existingLowerCase,
            final String name) {
        final var colDef = createColDefintion(existingLowerCase, name);
        colDef.number = new NumberColumn();
        return colDef;
    }

    private static ColumnDefinition createDateTimeDefinition(final Set<String> existingLowerCase, final String name) {
        final var colDef = createColDefintion(existingLowerCase, name);
        final var dateTimeCol = new DateTimeColumn();
        dateTimeCol.format = "dateTime";
        colDef.dateTime = dateTimeCol;
        return colDef;
    }

    private static ColumnDefinition createDateDefinition(final Set<String> existingLowerCase, final String name) {
        final var colDef = createColDefintion(existingLowerCase, name);
        final var dateTimeCol = new DateTimeColumn();
        dateTimeCol.format = "dateOnly";
        colDef.dateTime = dateTimeCol;
        return colDef;
    }
}
