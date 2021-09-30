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
package org.knime.ext.sharepoint.filehandling.node.listreader.framework;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.knime.core.data.DataType;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;

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

    private static final String TITLE = "Title";
    private static final String LINK_TITLE = "LinkTitle";

    protected final JsonObject m_spec;

    protected SharepointFieldType m_type; // not final because we could be usure and edit later

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
        m_idName = spec.get("name").getAsString();
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

    public abstract T getCannonicalRepresentation(final JsonElement data);

    public abstract DataType getCannonicalType();

    public static SharepointListColumn<?> of(final JsonObject spec) { // NOSONAR
        JsonObject elem;
        SharepointListColumn<?> result;
        if ((elem = spec.getAsJsonObject("text")) != null) {
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
        } else {
            result = new UnsureTypedColumn(spec);
        }

        return result;
    }

    /**
     * In the SharePoint API the display name of the “Title” column seems to be
     * fixed. To be still able to rename the it a second column with the renamed
     * title and the ID name “LinkTitle” is created which is the be used for getting
     * the display name.<br>
     *
     * To ensure that the user sees the name they have set in the GUI this method
     * maps the renamed display name to the original title column. It should be
     * called directly after the columns have been read into a list and before this
     * list is used.
     *
     * @param columns
     *            the list containing the columns to be edited. It must contain
     *            columns with the name id {@value #TITLE} and {@value #LINK_TITLE}.
     */
    static void fixTitleColumnLink(final List<SharepointListColumn<?>> columns) {
        SharepointListColumn<?> title = null;
        SharepointListColumn<?> linkTitle = null;
        for (final var col : columns) {
            if (col.getIdName().equals(TITLE)) {
                title = col;
            } else if (col.getIdName().equals(LINK_TITLE)) {
                linkTitle = col;
            }
            if (null != title && null != linkTitle) {
                break;
            }
        }
        assert null != title && null != linkTitle;

        title.m_displayName = linkTitle.m_displayName;
    }

    static final class StringTypedColumn extends SharepointListColumn<String> {

        /**
         * @param spec
         *            the specification as JSON
         * @param type
         *            the used type
         */
        protected StringTypedColumn(final JsonObject spec, final SharepointFieldType type) {
            super(spec, type);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getCannonicalRepresentation(final JsonElement data) {
            return data.getAsString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataType getCannonicalType() {
            return StringCell.TYPE;
        }
    }

    static final class NumberTypedColumn extends SharepointListColumn<Number> {

        private final String m_decimalPlaces;
        private final String m_displayAs;

        /**
         * @param spec
         *            the specification as JSON
         */
        protected NumberTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.NUMBER);
            final var settings = spec.getAsJsonObject("number");
            m_decimalPlaces = settings.get("decimalPlaces").getAsString();
            m_displayAs = settings.get("displayAs").getAsString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Number getCannonicalRepresentation(final JsonElement data) {
            if (m_decimalPlaces.equals("none")) {
                return data.getAsLong();
            } else if (m_decimalPlaces.equals("auto")) {
                return data.getAsNumber();
            } else {
                return data.getAsDouble();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataType getCannonicalType() {
            if (m_decimalPlaces.equals("none")) {
                return LongCell.TYPE;
            } else if (m_decimalPlaces.equals("auto")) {
                return DoubleCell.TYPE;
            } else {
                return DoubleCell.TYPE;
            }
        }
    }

    static final class PersonTypedColumn extends SharepointListColumn<String> {

        final String m_displayAs; // TODO can this even be changed by the user?: yes, it can in the old UI
        final boolean m_multiple;
        final String m_idNameLookup;

        /**
         * @param spec
         *            the specification as JSON
         */
        protected PersonTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.PERSON);
            final var settings = spec.getAsJsonObject("personOrGroup");
            m_displayAs = settings.get("displayAs").getAsString();
            m_multiple = settings.get("allowMultipleSelection").getAsBoolean();
            m_idNameLookup = m_idName + "LookupId";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIdName() {
            if (m_multiple) {
                return m_idName;
            } else {
                return m_idNameLookup;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getCannonicalRepresentation(final JsonElement data) {
            if (m_multiple) {
                return StreamSupport.stream(data.getAsJsonArray().spliterator(), false)//
                        .map(JsonElement::getAsJsonObject)//
                        .map(o -> o.get("LookupId"))//
                        .map(JsonElement::getAsString)//
                        .collect(Collectors.joining(","));
            } else {
                return data.getAsString();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataType getCannonicalType() {
            return StringCell.TYPE;
        }
    }

    static final class BooleanTypedColumn extends SharepointListColumn<Boolean> {

        /**
         * @param spec
         *            the specification as JSON
         */
        protected BooleanTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.YES_NO);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Boolean getCannonicalRepresentation(final JsonElement data) {
            return data.getAsBoolean();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataType getCannonicalType() {
            return BooleanCell.TYPE;
        }
    }

    static final class ChoiceTypedColumn extends SharepointListColumn<String> {

        private final boolean m_multiple;

        /**
         * @param spec
         *            the specification as JSON
         */
        protected ChoiceTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.CHOICE);
            m_multiple = spec.getAsJsonObject("choice")//
                    .getAsJsonPrimitive("displayAs").getAsString()//
                    .equals("checkBoxes");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getCannonicalRepresentation(final JsonElement data) {
            if (m_multiple) {
                return StreamSupport.stream(data.getAsJsonArray().spliterator(), false)//
                        .map(JsonElement::getAsString)//
                        .collect(Collectors.joining(","));
            } else {
                return data.getAsString();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataType getCannonicalType() {
            return StringCell.TYPE;
        }

    }

    enum SharepointFieldType {
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
        CURRENCY, // "currency": { "locale": // TODO do some magic here java.util.Currency }
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

        /**
         * @param spec
         *            the specification as JSON
         * @param type
         *            the used type
         */
        protected UnsureTypedColumn(final JsonObject spec) {
            super(spec, SharepointFieldType.LOCATION_OR_IMAGE_OR_HYPERLINK, false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getCannonicalRepresentation(final JsonElement data) {
            return data.toString(); // JSONCells seem not to be available //TODO
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DataType getCannonicalType() {
            return StringCell.TYPE;
        }

    }
}
