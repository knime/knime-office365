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
 *   2021-09-27 (loescher): created
 */
package org.knime.ext.sharepoint.lists.node.reader.framework;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.knime.core.data.DataType;
import org.knime.core.data.collection.ListCell;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.time.localdate.LocalDateCellFactory;
import org.knime.core.data.time.localdatetime.LocalDateTimeCellFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @param <T>
 *            the type of the default/canonical representation
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
abstract class SharepointListColumn<T> {

    private static final DataType TYPE_LIST_STRING = DataType.getType(ListCell.class, StringCell.TYPE);

    protected final JsonObject m_spec;

    protected SharepointFieldType m_type; // not final because we could be unsure and edit later

    private boolean m_typeDetermined;

    private String m_displayName;

    protected final String m_idName;

    /**
     * Constructs a column with a determined type.
     *
     * @param spec
     *            the specification as JSON
     * @param type
     *            the used type
     */
    protected SharepointListColumn(final JsonObject spec, final SharepointFieldType type) {
        this(spec, type, true);
    }

    /**
     * @param spec
     *            the specification as JSON
     * @param type
     *            the used type
     * @param typeDetermined
     *            whether the type has been determined
     */
    protected SharepointListColumn(final JsonObject spec, final SharepointFieldType type,
            final boolean typeDetermined) {
        m_spec = spec;
        m_type = type;
        m_displayName = spec.get("displayName").getAsString();
        m_idName = spec.get("name").getAsString().toLowerCase();
        m_typeDetermined = typeDetermined;
    }

    /**
     * @return the name visually displayed to the user
     */
    public String getDisplayName() {
        return m_displayName;
    }

    /**
     * @return the identifying name used for internal references and fields
     */
    public String getIdName() {
        return m_idName;
    }

    /**
     * @return whether an absolute type already could be determined
     */
    public boolean isTypeDetermined() {
        return m_typeDetermined;
    }

    /**
     * set that an absolute type has been determined
     */
    public void setTypeDetermined() {
        m_typeDetermined = true;
    }

    /**
     * @return the type of the Sharepoint list column
     */
    public SharepointFieldType getType() {
        return m_type;
    }

    public abstract T getCanonicalRepresentation(final JsonElement data);

    public abstract DataType getCanonicalType();

    public static SharepointListColumn<?> of(final JsonObject spec) { // NOSONAR
        JsonObject elem;
        SharepointListColumn<?> result;
        final var name = spec.get("name").getAsString();
        if (name.equalsIgnoreCase("id")) {
            result = new IDTypedColumn(spec);
        } else if (name.equalsIgnoreCase("Title")) {
            result = new StringTypedColumn(spec, SharepointFieldType.SINGLE_LINE_TEXT);
        } else if ((elem = spec.getAsJsonObject("text")) != null) {
            if (elem.get("allowMultipleLines").getAsBoolean()) {
                result = new StringTypedColumn(spec, SharepointFieldType.MULTI_LINE_TEXT);
            } else {
                result = new StringTypedColumn(spec, SharepointFieldType.SINGLE_LINE_TEXT);
            }
        } else if (spec.has("choice")) {
            result = new ChoiceTypedColumn(spec);
        } else if (spec.has("number")) {
            result = new NumberTypedColumn(spec);
        } else if (spec.has("boolean")) {
            result = new BooleanTypedColumn(spec);
        } else if (spec.has("personOrGroup")) {
            result = new PersonTypedColumn(spec);
        } else if (spec.has("lookup")) {
            result = new LookupTypedColumn(spec);
        } else if (spec.has("dateTime")) {
            result = new DateTimeColumn(spec);
        } else if (spec.has("currency")) {
            result = new CurrencyTypedColunm(spec);
        } else if (spec.has("calculated")) { // TODO _maybe_ introduce type recognition later
            result = new StringTypedColumn(spec, SharepointFieldType.CALCULATION);
        } else {
            result = new UnsureTypedColumn(spec);
        }

        return result;
    }

    static final class IDTypedColumn extends SharepointListColumn<String> {

        protected IDTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.ID);
        }

        @Override
        public String getCanonicalRepresentation(final JsonElement data) {
            return data.getAsString();
        }

        @Override
        public DataType getCanonicalType() {
            return StringCell.TYPE;
        }

    }

    static final class StringTypedColumn extends SharepointListColumn<String> {

        protected StringTypedColumn(final JsonObject spec, final SharepointFieldType type) {
            super(spec, type);
        }

        @Override
        public String getCanonicalRepresentation(final JsonElement data) {
            return data.getAsString();
        }

        @Override
        public DataType getCanonicalType() {
            return StringCell.TYPE;
        }
    }

    static final class NumberTypedColumn extends SharepointListColumn<Number> {

        private final String m_decimalPlaces;
        private final String m_displayAs;

        protected NumberTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.NUMBER);
            final var settings = spec.getAsJsonObject("number");
            m_decimalPlaces = settings.get("decimalPlaces").getAsString();
            m_displayAs = settings.get("displayAs").getAsString();
        }

        @Override
        public Number getCanonicalRepresentation(final JsonElement data) {
            if (m_decimalPlaces.equals("none")) {
                return data.getAsLong();
            } else if (m_decimalPlaces.equals("auto")) {
                return data.getAsNumber();
            } else {
                return data.getAsDouble();
            }
        }

        @Override
        public DataType getCanonicalType() {
            if (m_decimalPlaces.equals("none")) {
                return LongCell.TYPE;
            } else if (m_decimalPlaces.equals("auto")) {
                return DoubleCell.TYPE;
            } else {
                return DoubleCell.TYPE;
            }
        }
    }

    static final class PersonTypedColumn extends SharepointListColumn<Object> {

        final String m_displayAs;
        final boolean m_multiple;
        final String m_idNameLookup;

        protected PersonTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.PERSON);
            final var settings = spec.getAsJsonObject("personOrGroup");
            m_displayAs = settings.get("displayAs").getAsString();
            m_multiple = settings.get("allowMultipleSelection").getAsBoolean();
            m_idNameLookup = m_idName + "lookupid";
        }

        @Override
        public String getIdName() {
            if (m_multiple) {
                return m_idName;
            } else {
                return m_idNameLookup;
            }
        }

        @Override
        public Object getCanonicalRepresentation(final JsonElement data) {
            if (m_multiple) {
                return StreamSupport.stream(data.getAsJsonArray().spliterator(), false)//
                        .map(JsonElement::getAsJsonObject)//
                        .map(o -> o.get("LookupId"))//
                        .map(JsonElement::getAsString)//
                        .toArray(String[]::new);
            } else {
                return Long.valueOf(data.getAsString());
            }
        }

        @Override
        public DataType getCanonicalType() {
            if (m_multiple) {
                return TYPE_LIST_STRING;
            } else {
                return StringCell.TYPE;
            }
        }
    }

    static final class LookupTypedColumn extends SharepointListColumn<Object> {

        final boolean m_multiple;
        final String m_idNameLookup;

        final String m_listID;
        final String m_columnName;
        final Optional<String> m_primaryColumn;

        protected LookupTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.LOOKUP);
            final var settings = spec.getAsJsonObject("lookup");
            m_multiple = settings.get("allowMultipleValues").getAsBoolean();
            m_listID = settings.get("listId").getAsString();
            m_columnName = settings.get("columnName").getAsString();
            m_primaryColumn = Optional.ofNullable(settings.get("primaryLookupColumnId")).map(JsonElement::getAsString);
            m_idNameLookup = m_idName + "lookupid";
        }

        @Override
        public String getIdName() {
            if (m_multiple) {
                return m_idName;
            } else {
                return m_idNameLookup;
            }
        }

        @Override
        public Object getCanonicalRepresentation(final JsonElement data) {
            if (m_multiple) {
                final var vals = StreamSupport.stream(data.getAsJsonArray().spliterator(), false)//
                        .map(JsonElement::getAsJsonObject)//
                        .map(o -> o.get("LookupId"))//
                        .map(JsonElement::getAsString)//
                        .toArray(String[]::new);
                // emulate behavior of person column
                if (vals.length == 0) {
                    return null; // missing value
                } else {
                    return vals;
                }
            } else {
                return data.getAsString();
            }
        }

        @Override
        public DataType getCanonicalType() {
            if (m_multiple) {
                return TYPE_LIST_STRING;
            } else {
                return StringCell.TYPE;
            }
        }

        /**
         * @return the primary column ID, if empty this column is a primary column
         */
        public Optional<String> getPrimaryColumn() {
            return m_primaryColumn;
        }

    }

    static final class BooleanTypedColumn extends SharepointListColumn<Boolean> {

        protected BooleanTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.YES_NO);
        }

        @Override
        public Boolean getCanonicalRepresentation(final JsonElement data) {
            return data.getAsBoolean();
        }

        @Override
        public DataType getCanonicalType() {
            return BooleanCell.TYPE;
        }
    }

    static final class ChoiceTypedColumn extends SharepointListColumn<Object> {

        private final boolean m_multiple;

        protected ChoiceTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.CHOICE);
            m_multiple = spec.getAsJsonObject("choice")//
                    .getAsJsonPrimitive("displayAs").getAsString()//
                    .equals("checkBoxes");
        }

        @Override
        public Object getCanonicalRepresentation(final JsonElement data) {
            if (m_multiple) {
                return StreamSupport.stream(data.getAsJsonArray().spliterator(), false)//
                        .map(JsonElement::getAsString)//
                        .toArray(String[]::new);
            } else {
                return data.getAsString();
            }
        }

        @Override
        public DataType getCanonicalType() {
            if (m_multiple) {
                return TYPE_LIST_STRING;
            } else {
                return StringCell.TYPE;
            }
        }

    }

    static final class DateTimeColumn extends SharepointListColumn<Object> {

        private final boolean m_dateOnly;

        protected DateTimeColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.DATE_AND_TIME);
            m_dateOnly = "dateOnly".equals(spec.getAsJsonObject("dateTime").get("format").getAsString());
        }

        @Override
        public Object getCanonicalRepresentation(final JsonElement data) {

            if (m_dateOnly) {
                return LocalDate.parse(data.getAsString(), DateTimeFormatter.ISO_DATE_TIME);
            } else {
                return LocalDateTime.parse(data.getAsString(), DateTimeFormatter.ISO_DATE_TIME);
            }
        }

        @Override
        public DataType getCanonicalType() {
            if (m_dateOnly) {
                return LocalDateCellFactory.TYPE;
            } else {
                return LocalDateTimeCellFactory.TYPE;
            }
        }

    }

    static final class CurrencyTypedColunm extends SharepointListColumn<Object> {

        private final Currency m_currency;

        protected CurrencyTypedColunm(final JsonObject spec) {
            super(spec, SharepointFieldType.CURRENCY);
            final var locale = Locale.forLanguageTag(spec.getAsJsonObject("currency").get("locale").getAsString());
            m_currency = Currency.getInstance(locale);
        }

        @Override
        public Double getCanonicalRepresentation(final JsonElement data) {
            return data.getAsDouble();
        }

        @Override
        public DataType getCanonicalType() {
            return DoubleCell.TYPE;
        }

    }

    @SuppressWarnings("squid:S125")
    enum SharepointFieldType {
        ID, // "name": "ID" // special column that should be parsed as a long (typeless)
        SINGLE_LINE_TEXT, // "text": { "allowMultipleLines": false }
        MULTI_LINE_TEXT, // "text": { "allowMultipleLines": true }
        LOCATION, // null | { …, "dispayName": ""}
        NUMBER, // "number": {"decimalPlaces": "", "displayAs":""}
        YES_NO, // "boolean": {}
        PERSON, // "personOrGroup": {"displayAs": ""} |
                // "<name>LookupId": "" -> "users" list index
        DATE_AND_TIME, // "dateTime": {"displayAs": "", "format":{}}
        CHOICE, // "choice":{}
        HYPERLINK, // null | { "Description": "", "Url": ""}
        CURRENCY, // "currency": { "locale"}
        IMAGE, // null | "" (JSON)
        LOOKUP, // "lookup": { "listId": "", "columnName": ""}
        CALCULATION, // "calculated": { "outpuType": ""}
        TASK_OUTCOME, // "defaultValue": {}
        // EXTERNAL_DATA, // ???
        MANAGED_METADATA, // "defaultValue": {}

        ATTACHMENTS, // null | boolean

        LOCATION_OR_IMAGE_OR_HYPERLINK, TASK_OUTCOME_OR_MANAGED_METADATA,
    }

    static final class UnsureTypedColumn extends SharepointListColumn<String> {

        protected UnsureTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.LOCATION_OR_IMAGE_OR_HYPERLINK, false);
        }

        @Override
        public String getCanonicalRepresentation(final JsonElement data) {
            return data.toString(); // JSONCells seem not to be available //TODO
        }

        @Override
        public DataType getCanonicalType() {
            return StringCell.TYPE;
        }

    }
}
