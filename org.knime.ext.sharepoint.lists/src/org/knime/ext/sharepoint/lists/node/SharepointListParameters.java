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
 *   2025-01-21 (Jannik Löscher): created
 */
package org.knime.ext.sharepoint.lists.node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.booleanhelpers.DoNotPersistBoolean;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.CustomValidation;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.CustomValidationProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.ValidationCallback;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters.CreateListParameters.ListModeRef;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters.CreateListParameters.NewListRef;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters.ListParameters.ExistingListWRef;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters.ShowSystemLists.ReplaceListChoicesToShowSystemLists;
import org.knime.ext.sharepoint.lists.node.SharepointListParameters.ShowSystemLists.ShowSystemListsRef;
import org.knime.ext.sharepoint.parameters.DebouncedChoicesProvider;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Inside;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.ConfigMigration;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.migration.NodeParametersMigration;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.text.TextInputWidget;
import org.knime.node.parameters.widget.text.TextInputWidgetValidation.PatternValidation.IsNotBlankValidation;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * <p>
 * Reusable Sharepoint List parameters component (if backwards compatibility is
 * a concern, the variable holding this object has to be called "m_list").
 * </p>
 *
 * <p>
 * This component provides different modes. Use the respective nested class
 * depending on the features you need.
 * </p>
 *
 * <p>
 * To use this component you also have to have a
 * {@link org.knime.ext.sharepoint.parameters.SharepointSiteParameters.Ref} in
 * your parameters.
 * </p>
 *
 * <p>
 * To modify some of the contained widgets, extend
 * {@link SharepointListParametersModification} and annotate the field holding
 * the instance of this class with {@link Modification}.
 * </p>
 *
 * @see Basic
 * @see WithCreateLists
 * @see WithSystemLists
 * @see WithCreateListsAndSystemLists
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings({ "restriction" })
public abstract sealed class SharepointListParameters implements NodeParameters {

    // The separator used in the id fields to store multiple data points in it
    // to be able to store both of them (required to be backwards compatibility)
    private static final Pattern SEP = Pattern.compile("\0");

    private static final int DIALOG_CLIENT_TIMEOUT_MILLIS = 30000;

    private static final int DEBOUNCE_TIME = 1500;

    private static final List<Option> SYSLISTS_OPTION = List.of( //
            new QueryOption("select", "system,id,displayName,name"));

    /**
     * Allow only brood to create new instances
     */
    protected SharepointListParameters() {
    }

    @Section(title = "SharePoint List")
    interface SharepointListSection {
        /** Declares a widget to be before all tails. */
        @Before(Tail.class)
        @Inside(SharepointListSection.class)
        interface Head {
        }

        /**
         * Places an item after the head element in the section and wags when happy.
         */
        @Inside(SharepointListSection.class)
        interface Tail {
        }

    }

    abstract ListParameters getListParameters();

    abstract ListExistsPolicy getIfListExistsPolicy();

    /**
     * A basic widget with a list selection box but no option to select system lists
     * or create new lists.
     *
     * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
     */
    public static final class Basic extends SharepointListParameters {

        @PersistEmbedded
        @Migration(ListMigration.class)
        ListParameters m_list = new ListParameters();

        @ValueReference(ShowSystemListsRef.class)
        @Persistor(DoNotPersistBoolean.class)
        boolean m_unused;

        @Override
        ListParameters getListParameters() {
            return m_list;
        }

        @Override
        ListExistsPolicy getIfListExistsPolicy() {
            return null;
        }
    }

    /**
     * A basic widget with a list selection box and the option to select system
     * lists but no ability create new lists.
     *
     * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
     */
    public static final class WithSystemLists extends SharepointListParameters {

        @PersistEmbedded
        @Migration(ListMigration.class)
        ListParameters m_list = new ListParameters();

        @PersistEmbedded
        ShowSystemLists m_showSystemLists = new ShowSystemLists();

        @Override
        ListParameters getListParameters() {
            return m_list;
        }

        @Override
        ListExistsPolicy getIfListExistsPolicy() {
            return null;
        }
    }

    /**
     * A basic widget with a list selection box and the ability create new lists but
     * no option to select system lists.
     *
     * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
     */
    public static final class WithCreateLists extends SharepointListParameters {

        @PersistEmbedded
        @Migration(CreateListMigration.class)
        CreateListParameters m_list = new CreateListParameters();

        @PersistEmbedded
        ListWriteMode m_writeMode = new ListWriteMode();

        @Override
        ListParameters getListParameters() {
            return m_list;
        }

        @Override
        ListExistsPolicy getIfListExistsPolicy() {
            return switch (m_list.m_listMode) {
            case CREATE -> m_writeMode.m_ifListExists;
            case SELECT -> WriteMode.asExistsPolicy(m_writeMode.m_writeMode);
            };
        }
    }

    /**
     * A basic widget with a list selection box and the ability create new lists and
     * an option to select system lists.
     *
     * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
     */
    public static final class WithCreateListsAndSystemLists extends SharepointListParameters {

        @PersistEmbedded
        @Migration(CreateListMigration.class)
        @Modification(ReplaceListChoicesToShowSystemLists.class)
        CreateListParameters m_list = new CreateListParameters();

        @PersistEmbedded
        ShowSystemLists m_showSystemLists = new ShowSystemLists();

        @PersistEmbedded
        ListWriteMode m_writeMode = new ListWriteMode();

        @Override
        ListParameters getListParameters() {
            return m_list;
        }

        @Override
        ListExistsPolicy getIfListExistsPolicy() {
            return switch (m_list.m_listMode) {
            case CREATE -> m_writeMode.m_ifListExists;
            case SELECT -> WriteMode.asExistsPolicy(m_writeMode.m_writeMode);
            };
        }
    }

    @Layout(SharepointListSection.Tail.class)
    static sealed class ListParameters implements NodeParameters {

        private static final String EXISTING_LIST_DESCRIPTION = """
                An existing SharePoint list. The drop down menu shows lists in the format &lt;display-name&gt;
                (&lt;internal-name&gt;). The display name is a user-chosen name that is editable in SharePoint,
                whereas the internal name is fixed and auto-generated by SharePoint.
                The node stores a unique technical ID which is used to find the list.
                """;

        @ValueProvider(SetTrueIfOpenedInDialog.class)
        @Persist(hidden = true)
        boolean m_checkExistenceInDialog = true;

        @Widget(title = "List", description = EXISTING_LIST_DESCRIPTION)
        @Effect(predicate = CreateListSelected.class, type = EffectType.HIDE)
        @ChoicesProvider(ListChoicesProvider.class)
        @Modification.WidgetReference(ExistingListWRef.class)
        @ValueReference(ExistingListRef.class)
        @Persistor(ExistingListPersistor.class)
        String m_existingList = "";

        static final class ExistingListWRef implements Modification.Reference {
        }

        static final class ExistingListRef implements ParameterReference<String> {
        }

        @Override
        public void validate() throws InvalidSettingsException {
            if (m_checkExistenceInDialog) {
                CheckUtils.checkSetting(getExistingListId() != null || getExistingListDisplayName() != null,
                        "Please select a list.");
            }
        }

        private String getExistingListField(final int index) {
            if (m_existingList == null || m_existingList.isEmpty()) {
                return null;
            }
            final var result = SEP.split(m_existingList, 3);
            if (result.length <= index) {
                return null;
            }
            return (result[index].isEmpty()) ? null : result[index];
        }

        String getExistingListId() {
            return getExistingListField(0);
        }

        String getExistingListInternalName() {
            return getExistingListField(1);
        }

        String getExistingListDisplayName() {
            return getExistingListField(2);
        }

    }

    @Layout(SharepointListSection.Tail.class)
    static final class CreateListParameters extends ListParameters {

        @Widget(title = "List mode", description = """
                Specify whether to select an existing list or create a new one.
                """)
        @ValueSwitchWidget
        @ValueReference(ListModeRef.class)
        @Layout(SharepointListSection.Head.class)
        ListMode m_listMode = ListMode.SELECT;

        static final class ListModeRef implements ParameterReference<ListMode> {
        }

        @Widget(title = "List name", description = """
                The display name of the list to use. If no such list exists, it will be created.
                The list will be matched by its display name only. If there are
                multiple lists with the same name, the first one returned by the API will be used, which
                may lead to unexpected results if the list is renamed in SharePoint. Thus it is best
                to use “select” mode if the list exists or was created by a previous execution of
                this node.
                """)
        @ValueReference(NewListRef.class)
        @TextInputWidget(patternValidation = IsNotBlankValidation.class)
        @Effect(predicate = CreateListSelected.class, type = EffectType.SHOW)
        @CustomValidation(ListNameValidator.class)
        String m_newListName = "";

        static final class NewListRef implements ParameterReference<String> {
        }

        public ListMode getListMode() {
            return m_listMode;
        }

        public void setListMode(final ListMode mode) {
            m_listMode = mode;
        }

        public String getNewListName() {
            return m_newListName;
        }

        public void setNewListName(final String name) {
            m_newListName = name;
        }

        @Override
        public void validate() throws InvalidSettingsException {
            if (ListMode.CREATE == m_listMode) {
                CheckUtils.checkSetting(!m_newListName.isEmpty(), "Please specify a name for the list.");
            } else {
                super.validate();
            }
        }
    }

    static final class ShowSystemLists implements NodeParameters {
        @Widget(title = "Show system lists", description = """
                Whether to show system lists (e.g., internal SharePoint lists) in the list
                selection. These are typically hidden as they are for internal SharePoint use.""")
        @Advanced
        @Effect(predicate = CreateListSelected.class, type = EffectType.HIDE)
        @Layout(SharepointListSection.Tail.class)
        @ValueReference(ShowSystemListsRef.class)
        boolean m_showSystemLists;

        static final class ShowSystemListsRef implements ParameterReference<Boolean> {
        }

        static final class ReplaceListChoicesToShowSystemLists extends ReplaceListChoicesModifier {
            ReplaceListChoicesToShowSystemLists() {
                super(ListWithSystemListsChoicesProvider.class);
            }
        }

    }

    static final class ListWriteMode implements NodeParameters {
        @Widget(title = "List write mode", //
                description = "How to write to the selected list.")
        @ValueSwitchWidget
        @Layout(SharepointListSection.Tail.class)
        @Migration(WriteModeMigration.class)
        @Effect(predicate = CreateListSelected.class, type = EffectType.HIDE)
        WriteMode m_writeMode = WriteMode.APPEND;

        @Widget(title = "If list already exists", //
                description = "How to handle the situation when a list with the same name already exists.")
        @ValueSwitchWidget
        @Layout(SharepointListSection.Tail.class)
        @Migration(IfListExistsMigration.class)
        @Effect(predicate = CreateListSelected.class, type = EffectType.SHOW)
        ListExistsPolicy m_ifListExists = ListExistsPolicy.FAIL;
    }

    enum WriteMode {
        @Label(value = "Append", description = """
                Append the data at the bottom of the list.
                There are limitations to appending. Please check the note in the node introduction
                for more information.""")
        APPEND,

        /** Overwrite existing list. */
        @Label(value = "Overwrite", //
                description = "Overwrite the list by removing all columns and items beforehand.")
        OVERWRITE;

        static ListExistsPolicy asExistsPolicy(final WriteMode mode) {
            if (mode == null) {
                return ListExistsPolicy.FAIL;
            }

            return switch (mode) {
            case APPEND -> ListExistsPolicy.APPEND;
            case OVERWRITE -> ListExistsPolicy.OVERWRITE;
            };
        }
    }

    @Override
    public void validate() throws InvalidSettingsException {
        getListParameters().validate();
    }

    /**
     * @return the internal ID of the specified list, may be null
     */
    public String getExistingListId() {
        return getListParameters().getExistingListField(0);
    }

    /**
     * @return the internal name of the specified list, may be null
     */
    public String getExistingListInternalName() {
        return getListParameters().getExistingListField(1);
    }

    /**
     * @return the internal name of the specified list, may be null
     */
    public String getExistingListDisplayName() {
        return getListParameters().getExistingListField(2);
    }

    /**
     * @return whether the node was created with a legacy dialog and the webui
     *         dialog was never opened. If so the existing list was not checked to
     *         be non-empty and this has to be checked manually.
     */
    public boolean isLegacyAndWebUIDialogNeverOpened() {
        return !getListParameters().m_checkExistenceInDialog;
    }

    /**
     * @return the mode of this settings, i.e. whether to select an existing list or
     *         create a new one, if supported by these settings
     */
    public Optional<ListMode> getListMode() {
        final var listParams = getListParameters();
        if (listParams instanceof CreateListParameters scl) {
            return Optional.ofNullable(scl.getListMode());
        }
        return Optional.empty();
    }

    /**
     * @return what to do if a list already exists, if these settings support
     *         creating lists
     */
    public Optional<ListExistsPolicy> getExistingListPolicy() {
        return Optional.ofNullable(getIfListExistsPolicy());
    }

    /**
     * @return the name of the list to create, if supported by these settings
     */
    public Optional<String> getListNameToCreate() {
        final var list = getListParameters();
        if (list instanceof CreateListParameters scl) {
            return Optional.ofNullable(scl.getNewListName());
        }
        return Optional.empty();
    }

    @SuppressWarnings("javadoc")
    public enum ListMode {
        @Label(value = "Select", //
                description = "Select an already existing list.")
        SELECT,

        @Label(value = "Create", //
                description = """
                        Use the first list with the specified display name. If
                        no such list exists, create one.""")
        CREATE
    }

    static final class CreateListSelected implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            if (i.isMissing(ListModeRef.class)) {
                return i.never();
            } else {
                return i.getEnum(ListModeRef.class).isOneOf(ListMode.CREATE);
            }
        }
    }

    /**
     * Choices provider for SharePoint lists.
     */
    static class ListChoicesProvider extends DebouncedChoicesProvider<List<StringChoice>>
            implements StringChoicesProvider {

        private static final AtomicReference<Thread> DEBOUNCE_LOCK = new AtomicReference<>();
        private static final AtomicLong LAST_TIME_CALLED = new AtomicLong();

        private Supplier<SharepointSiteParameters> m_siteParams;

        ListChoicesProvider() {
            super(DEBOUNCE_TIME, DEBOUNCE_LOCK, LAST_TIME_CALLED);
        }

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_siteParams = initializer.computeFromValueSupplier(SharepointSiteParameters.Ref.class);
        }

        protected boolean showSystemLists() {
            return false;
        }

        @Override
        protected List<StringChoice> computeStateDebounced(final NodeParametersInput context) {
            try {
                final var site = m_siteParams.get();
                final var client = createClient(context);
                final var siteId = site.getSiteId(client);

                return listLists(client, siteId, showSystemLists());
            } catch (IOException | IllegalStateException | GraphServiceException ignored) { // NOSONAR
                // avoid log spam in dialog
                return List.of();
            }
        }

        @Override
        protected List<StringChoice> bounced() {
            return List.of();
        }

        private static List<StringChoice> listLists(final GraphServiceClient<Request> client, final String siteId,
                final boolean showSystemLists) {
            final var result = new ArrayList<StringChoice>();
            final var opt = showSystemLists ? SYSLISTS_OPTION : Collections.<Option>emptyList();

            var nextRequest = client.sites(siteId).lists();

            while (nextRequest != null) {
                final var resp = nextRequest.buildRequest(opt).get();
                for (final var list : resp.getCurrentPage()) {
                    final var displayText = list.displayName + " (" + list.name + ")"
                            + (list.system != null ? " (system)" : "");
                    final var id = String.join("\0", list.id, list.name, list.displayName);
                    result.add(new StringChoice(id, displayText));
                }
                nextRequest = resp.getNextPage();
            }

            result.sort(Comparator.comparing(StringChoice::text));
            return result;
        }
    }

    static final class ListWithSystemListsChoicesProvider extends ListChoicesProvider {
        private Supplier<Boolean> m_showSystemLists;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_showSystemLists = initializer.computeFromValueSupplier(ShowSystemListsRef.class);
            super.init(initializer);
        }

        @Override
        protected boolean showSystemLists() {
            return m_showSystemLists.get();
        }
    }

    static class ReplaceListChoicesModifier implements Modification.Modifier {

        private final Class<? extends ListChoicesProvider> m_providerClass;

        ReplaceListChoicesModifier(final Class<? extends ListChoicesProvider> providerClass) {
            m_providerClass = providerClass;

        }

        @Override
        public void modify(final WidgetGroupModifier group) {
            group.find(ExistingListWRef.class).modifyAnnotation(ChoicesProvider.class).withValue(m_providerClass)
                    .modify();
        }

    }

    private static GraphServiceClient<Request> createClient(final NodeParametersInput context) throws IOException {
        final var credSpec = (CredentialPortObjectSpec) context.getInPortSpec(0)
                .orElseThrow(() -> new IOException("Credential port not connected"));

        try {
            final var authProvider = GraphCredentialUtil.createAuthenticationProvider(credSpec);
            return GraphApiUtil.createClient(authProvider, DIALOG_CLIENT_TIMEOUT_MILLIS, DIALOG_CLIENT_TIMEOUT_MILLIS);
        } catch (NoSuchCredentialException | IOException ex) {
            throw new IOException("Failed to create client: " + ex.getMessage(), ex);
        }
    }

    static final class ListNameChecker extends DebouncedChoicesProvider<String> implements StateProvider<String> {

        private static final AtomicReference<Thread> DEBOUNCE_LOCK = new AtomicReference<>();
        private static final AtomicLong LAST_TIME_CALLED = new AtomicLong();

        private Supplier<SharepointSiteParameters> m_siteParams;
        private Supplier<String> m_newName;

        ListNameChecker() {
            super(DEBOUNCE_TIME, DEBOUNCE_LOCK, LAST_TIME_CALLED);
        }

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_siteParams = initializer.computeFromValueSupplier(SharepointSiteParameters.Ref.class);
            m_newName = initializer.computeFromValueSupplier(NewListRef.class);
        }

        @Override
        protected Optional<String> preDebounceCheck(final NodeParametersInput context) {
            final var value = m_newName.get();
            if (value.isEmpty()) {
                return Optional.of(""); // special won't check value as NotBlankValidation already present
            } else {
                return Optional.empty();
            }
        }

        @Override
        protected String computeStateDebounced(final NodeParametersInput context) {
            try {
                final var site = m_siteParams.get();
                final var client = createClient(context);
                final var siteId = site.getSiteId(client);

                final var page = client.sites(siteId).lists().buildRequest(getFilter(m_newName.get())) //
                        .get().getCurrentPage();

                CheckUtils.checkSetting(page.isEmpty() || page.stream().anyMatch(l -> l.system == null),
                        "Name cannot be used because a read-only system list with that name already exists.");
            } catch (InvalidSettingsException e) { // NOSONAR thrown by us
                return e.getMessage();
            } catch (IOException | IllegalStateException | GraphServiceException ignore) { // NOSONAR
                // do not spam dialog
            }
            return null;
        }

        private static List<Option> getFilter(final String name) {
            return List.of( //
                    new QueryOption("select", "system,displayName"), //
                    new QueryOption("filter", "displayName eq '%s'".formatted(name.replace("'", "''"))));
        }

        @Override
        protected String bounced() {
            return null;
        }
    }

    // Copy the previously computed validation
    static final class ListNameValidator implements CustomValidationProvider<String> {
        private Supplier<String> m_newNameError;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_newNameError = initializer.computeFromProvidedState(ListNameChecker.class);
        }

        @Override
        public ValidationCallback<String> computeValidationCallback(final NodeParametersInput context) {
            final var message = m_newNameError.get();
            if (message == null || message.isEmpty()) {
                return null;
            } else {
                return v -> {
                    throw new InvalidSettingsException(message);
                };
            }
        }
    }

    abstract static class AbstractListMigration<T extends ListParameters> implements NodeParametersMigration<T> {

        @Override
        public List<ConfigMigration<T>> getConfigMigrations() {
            return List.of(ConfigMigration.builder(this::loadCombinedSettings) //
                    .withDeprecatedConfigPath("list") //
                    .withDeprecatedConfigPath("listName") //
                    .build());
        }

        private T loadCombinedSettings(final NodeSettingsRO settings) {
            final var id = settings.getString("list", "");
            final var dispAndInternName = settings.getString("listName", "");
            return loadCombinedSettings(id, dispAndInternName);
        }

        protected abstract T loadCombinedSettings(final String id, final String dispAndInternName);

        static void loadExistingList(final ListParameters result, final String id, final String dispAndInternName) {
            final var pair = SharePointListUtils.getDisplayAndInternalListName(dispAndInternName);

            if (pair != null) {
                result.m_existingList = String.join("\0", id, pair.getSecond(), pair.getFirst());
            } else {
                // should not happen; set display name to everything
                result.m_existingList = String.join("\0", id, "", dispAndInternName);
            }
        }

    }

    static final class ListMigration extends AbstractListMigration<ListParameters> {

        @Override
        protected ListParameters loadCombinedSettings(final String id, final String dispAndInternName) {
            final var result = new ListParameters();
            result.m_checkExistenceInDialog = false; // we want legacy behavior
            loadExistingList(result, id, dispAndInternName);
            return result;
        }
    }

    static final class CreateListMigration extends AbstractListMigration<CreateListParameters> {

        @Override
        protected CreateListParameters loadCombinedSettings(final String id, final String dispAndInternName) {
            final var result = new CreateListParameters();
            result.m_checkExistenceInDialog = false; // we want legacy behavior
            if (id.isEmpty()) { // empty id means that the list was not selected from existing lists
                result.setListMode(ListMode.CREATE);
                result.m_existingList = "";
                result.setNewListName(dispAndInternName);
            } else {
                loadExistingList(result, id, dispAndInternName);
            }
            return result;

        }
    }

    static class ExistingListPersistor implements NodeParametersPersistor<String> {

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {

            final var displayName = settings.getString("listDisplayName");
            final var internalName = settings.getString("listInternalName");
            final var id = settings.getString("listId");
            if (id.isEmpty() && displayName.isEmpty() && internalName.isEmpty()) {
                return "";
            } else {
                return String.join("\0", id, internalName, displayName);
            }
        }

        @Override
        public void save(final String params, final NodeSettingsWO settings) {
            final var fields = SEP.split(params, 3);

            settings.addString("listId", fields.length > 0 ? fields[0] : "");
            settings.addString("listInternalName", fields.length > 1 ? fields[1] : "");
            settings.addString("listDisplayName", fields.length > 2 ? fields[2] : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { "listId" }, //
                    { "listDisplayName" }, //
                    { "listInternalName" }, //
            };
        }
    }

    private static final String LIST_INTERNALS_KEY = "list_Internals";

    static class ListNameValidationPersistor implements NodeParametersPersistor<String> {

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (settings.containsKey(LIST_INTERNALS_KEY)) {
                return settings.getConfig(LIST_INTERNALS_KEY).getString("_listNameValidation", null);
            } else {
                return null;
            }
        }

        @Override
        public void save(final String param, final NodeSettingsWO settings) {
            if (param != null && settings instanceof NodeSettingsRO ro
                    && ro.getString("listMode", "").equals(ListMode.CREATE.name())) {
                settings.addConfig(LIST_INTERNALS_KEY).addString("_listNameValidation", param);
            }
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[0][];
        }
    }

    static final class WriteModeMigration implements NodeParametersMigration<WriteMode> {

        @Override
        public List<ConfigMigration<WriteMode>> getConfigMigrations() {
            return List.of(ConfigMigration.builder(WriteModeMigration::loadOutside)
                    .withMatcher(WriteModeMigration::matchOutside).build());
        }

        private static boolean matchOutside(final NodeSettingsRO settings) {
            return (settings.getParent() instanceof NodeSettingsRO ro) && ro.containsKey("if_list_exists");
        }

        private static WriteMode loadOutside(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (settings.getParent() instanceof NodeSettingsRO ro) {
                final var value = ro.getString("if_list_exists");
                try {
                    return switch (ListExistsPolicy.valueOf(value)) {
                    case APPEND -> WriteMode.APPEND;
                    case FAIL -> null; // deselect which is mapped to FAIL later
                    case OVERWRITE -> WriteMode.OVERWRITE;
                    };
                } catch (IllegalArgumentException e) {
                    throw new InvalidSettingsException(e.getMessage());
                }
            }
            return WriteMode.APPEND;
        }
    }

    static final class IfListExistsMigration implements NodeParametersMigration<ListExistsPolicy> {

        @Override
        public List<ConfigMigration<ListExistsPolicy>> getConfigMigrations() {
            return List.of(ConfigMigration.builder(IfListExistsMigration::loadOutside)
                    .withMatcher(IfListExistsMigration::matchOutside).build());
        }

        private static boolean matchOutside(final NodeSettingsRO settings) {
            return (settings.getParent() instanceof NodeSettingsRO ro) && ro.containsKey("if_list_exists");
        }

        private static ListExistsPolicy loadOutside(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (settings.getParent() instanceof NodeSettingsRO ro) {
                final var value = ro.getString("if_list_exists");
                try {
                    return ListExistsPolicy.valueOf(value);
                } catch (IllegalArgumentException e) {
                    throw new InvalidSettingsException(e.getMessage());
                }
            }
            return ListExistsPolicy.FAIL;
        }
    }

    static class SetTrueIfOpenedInDialog implements StateProvider<Boolean> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();

        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            return true;
        }

    }

    /**
     * Can be used to change some widgets of this component by overwriting the
     * relevant methods and providing own values.
     */
    public static class SharepointListParametersModification implements Modification.Modifier {

        @Override
        public final void modify(final WidgetGroupModifier group) {
            final var description = getExistingStringDescriptionPostfix();
            if (description.isPresent()) {
                final var appended = ListParameters.EXISTING_LIST_DESCRIPTION + description.get();
                group.find(ExistingListWRef.class).modifyAnnotation(Widget.class) //
                        .withProperty("description", appended) //
                        .modify();
            }
        }

        /**
         * Overwrite this method to return a string which will be appended to the
         * description of the existing list setting.
         *
         * @return a present string if the value shall be modified
         */
        protected Optional<String> getExistingStringDescriptionPostfix() {
            return Optional.empty();
        }
    }

}
