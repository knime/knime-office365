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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.booleanhelpers.DoNotPersistBoolean;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.CustomValidation;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.CustomValidationProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.custom.ValidationCallback;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.NoSuchCredentialException;
import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.GraphCredentialUtil;
import org.knime.ext.sharepoint.parameters.SharepointSiteParameters;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.ConfigMigration;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.migration.NodeParametersMigration;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;

import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * <p>
 * Reusable Sharepoint List parameters component (which saves to a settings node
 * called "list).
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
 * @see WithNewListsAndSystemLists
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
    }

    abstract AbstractListParameters getListParameters();

    abstract ListExistsPolicy getIfListExistsPolicy();

    /**
     * A basic widget with a list selection box but no option to select system lists
     * or create new lists.
     *
     * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
     */
    @PersistWithin({ "..", "list" })
    public static final class Basic extends SharepointListParameters {

        @PersistEmbedded
        @Migration(BaseListMigration.class)
        @Persistor(BaseListPersistor.class)
        BaseListParameters m_list = new BaseListParameters();

        @ValueReference(ShowSystemListsRef.class)
        @Persistor(DoNotPersistBoolean.class)
        boolean m_unused;

        @Override
        AbstractListParameters getListParameters() {
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
    @PersistWithin({ "..", "list" })
    public static final class WithSystemLists extends SharepointListParameters {

        @PersistEmbedded
        @Migration(BaseListMigration.class)
        @Persistor(BaseListPersistor.class)
        BaseListParameters m_list = new BaseListParameters();

        @PersistEmbedded
        ShowSystemLists m_showSystemLists = new ShowSystemLists();

        @Override
        AbstractListParameters getListParameters() {
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
    @PersistWithin({ "..", "list" })
    public static final class WithCreateLists extends SharepointListParameters {

        @PersistEmbedded
        @Migration(CreateListMigration.class)
        @Persistor(CreateListPersistor.class)
        CreateListParameters m_list = new CreateListParameters();

        @PersistEmbedded
        DontShowSystemLists m_unused = new DontShowSystemLists();

        @PersistEmbedded
        IfListExists m_listExists = new IfListExists();

        @Override
        AbstractListParameters getListParameters() {
            return m_list;
        }

        @Override
        ListExistsPolicy getIfListExistsPolicy() {
            return m_listExists.m_ifListExists;
        }
    }

    /**
     * A basic widget with a list selection box and the ability create new lists and
     * an option to select system lists.
     *
     * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
     */
    @PersistWithin({ "..", "list" })
    public static final class WithNewListsAndSystemLists extends SharepointListParameters {

        @PersistEmbedded
        @Migration(CreateListMigration.class)
        @Persistor(CreateListPersistor.class)
        CreateListParameters m_list = new CreateListParameters();

        @PersistEmbedded
        ShowSystemLists m_showSystemLists = new ShowSystemLists();

        @PersistEmbedded
        IfListExists m_listExists = new IfListExists();

        @Override
        AbstractListParameters getListParameters() {
            return m_list;
        }

        @Override
        ListExistsPolicy getIfListExistsPolicy() {
            return m_listExists.m_ifListExists;
        }
    }

    private abstract static sealed class AbstractListParameters implements NodeParameters {

        private boolean m_legacyCombined;

        abstract ExistingList getExistingList();

        abstract ListMode getListMode();

        abstract void setListMode(ListMode mode);

        abstract String getNewListName();

        abstract void setNewListName(String name);

        void setLoadedLegacyCombined() {
            m_legacyCombined = true;
        }
    }

    static final class BaseListParameters extends AbstractListParameters {

        @ValueReference(ListModeRef.class)
        ListMode m_listMode;

        ExistingList m_existingList = new ExistingList();

        @Override
        public ListMode getListMode() {
            return null;
        }

        @Override
        public ExistingList getExistingList() {
            return m_existingList;
        }

        @Override
        public void setListMode(final ListMode mode) {
            // noop
        }

        @Override
        public String getNewListName() {
            return null;
        }

        @Override
        public void setNewListName(final String name) {
            // noop
        }
    }

    static final class CreateListParameters extends AbstractListParameters {

        @Widget(title = "List mode", description = """
                Specify whether to select an existing list or create a new one.
                """)
        @ValueSwitchWidget
        @ValueReference(ListModeRef.class)
        @Layout(SharepointListSection.class)
        ListMode m_listMode = ListMode.SELECT;

        ExistingList m_existingList = new ExistingList();

        @Widget(title = "Name", description = """
                Specify the display name of the list to create. If you previously created a list with
                this node and reopened the dialog, you have to select it from the list of existing lists. As long as
                you do not reopen the dialog, the list will be matched by its display name. If there are multiple lists
                with the same name, the first one returned by the API will be chosen.
                This may lead to unexpected results if the list is renamed on SharePoint.
                """)
        @ValueReference(NewListRef.class)
        @Effect(predicate = CreateListSelected.class, type = EffectType.SHOW)
        @CustomValidation(ListNameValidator.class)
        @Layout(SharepointListSection.class)
        String m_newListName = "";

        @Override
        public ListMode getListMode() {
            return m_listMode;
        }

        @Override
        public ExistingList getExistingList() {
            return m_existingList;
        }

        @Override
        public void setListMode(final ListMode mode) {
            m_listMode = mode;
        }

        @Override
        public String getNewListName() {
            return m_newListName;
        }

        @Override
        public void setNewListName(final String name) {
            m_newListName = name;
        }
    }

    // exists to keep order
    static final class ExistingList implements NodeParameters {
        private static final String DESCRIPTION = """
                Select an existing SharePoint list. The drop down menu shows lists in the format &lt;display-name&gt;
                (&lt;internal-name&gt;). The display name is a user-chosen name, which is editable in SharePoint,
                whereas the internal name is fixed and auto-generated by SharePoint.
                In addition to that the node also stores a unique technical ID which is used to find the list.
                """;

        @Widget(title = "List", description = DESCRIPTION)
        @Effect(predicate = CreateListSelected.class, type = EffectType.HIDE)
        @ChoicesProvider(ListChoicesProvider.class)
        @Modification.WidgetReference(ExistingListWRef.class)
        @ValueReference(ExistingListRef.class)
        @Layout(SharepointListSection.class)
        String m_value = "";
    }

    static final class ShowSystemLists implements NodeParameters {
        @Widget(title = "Show system lists", description = """
                If checked, system lists (e.g., internal SharePoint lists) will be shown in the list
                selection.""")
        @Advanced
        @Effect(predicate = CreateListSelected.class, type = EffectType.HIDE)
        @Layout(SharepointListSection.class)
        @ValueReference(ShowSystemListsRef.class)
        boolean m_showSystemLists;
    }

    static final class DontShowSystemLists implements NodeParameters {
        @ValueReference(ShowSystemListsRef.class)
        @Persistor(DoNotPersistBoolean.class)
        boolean m_unused;
    }

    static final class IfListExists implements NodeParameters {
        @Widget(title = "If list exists", description = "Specify how to handle an already existing list.")
        @ValueSwitchWidget
        @Layout(SharepointListSection.class)
        @Migration(IfListExistsMigration.class)
        ListExistsPolicy m_ifListExists = ListExistsPolicy.FAIL;
    }

    @Override
    public void validate() throws InvalidSettingsException {
        if (getListParameters().getListMode() != ListMode.CREATE) {
            CheckUtils.checkSetting(getExistingListId() != null, "Please select a list.");
        } else {
            CheckUtils.checkSetting(!getListParameters().getNewListName().isBlank(),
                    "Please specify a name for the list.");
        }
    }

    /**
     * @return the internal ID of the specified list, may be null
     */
    public String getExistingListId() {
        return getExistingListField(0);
    }

    /**
     * @return the internal name of the specified list, may be null
     */
    public String getExistingListInternalName() {
        return getExistingListField(1);
    }

    /**
     * @return the internal name of the specified list, may be null
     */
    public String getExistingListDisplayName() {
        return getExistingListField(2);
    }

    /**
     * @return whether this component was loaded from the settings of a previous
     *         SharepointListSettingsPanel
     * @deprecated this is for internal backwards compatibility use and is not API
     */
    @Deprecated(forRemoval = false)
    public boolean hasLoadedLegacyCombinedExistingList() { // NOSONAR will not be removed
        return getListParameters().m_legacyCombined;
    }

    /**
     * @return the mode of this settings, i.e. whether to select an existing list or
     *         create a new one, if supported by these settings
     */
    public Optional<ListMode> getListMode() {
        return Optional.ofNullable(getListParameters().getListMode());
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
        if (list.getListMode() == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(list.getNewListName());
        }
    }

    private String getExistingListField(final int index) {
        final var list = getListParameters().getExistingList();
        if (list.m_value == null || list.m_value.isEmpty()) {
            return null;
        }
        final var result = SEP.split(list.m_value, 3);
        if (result.length <= index) {
            return null;
        }
        return (result[index].isBlank()) ? null : result[index];
    }

    @SuppressWarnings("javadoc")
    public enum ListMode {
        @Label(value = "Select", //
                description = "Select an already existing list.")
        SELECT,

        @Label(value = "Create", //
                description = "Create a new list with a specified display name.")
        CREATE
    }

    static final class ListModeRef implements ParameterReference<ListMode> {
    }

    static final class ExistingListRef implements ParameterReference<String> {
    }

    static final class NewListRef implements ParameterReference<String> {
    }

    static final class ShowSystemListsRef implements ParameterReference<Boolean> {
    }

    static final class CreateListSelected implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            if (i.isMissing(ListModeRef.class)) {
                return i.always();
            } else {
                return i.getEnum(ListModeRef.class).isOneOf(ListMode.CREATE);
            }
        }
    }

    /**
     * Choices provider for SharePoint lists.
     */
    static final class ListChoicesProvider implements StringChoicesProvider {

        private static final AtomicReference<Thread> REBOUCE_LOCK = new AtomicReference<>();

        private Supplier<SharepointSiteParameters> m_siteParams;

        private Supplier<Boolean> m_showSystemLists;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_siteParams = initializer.computeFromValueSupplier(SharepointSiteParameters.Ref.class);
            m_showSystemLists = initializer.computeFromValueSupplier(ShowSystemListsRef.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            synchronized (REBOUCE_LOCK) {
                final var current = Thread.currentThread();
                try {
                    final var past = REBOUCE_LOCK.getAndSet(current);

                    if (past != null) {
                        past.interrupt();
                    }

                    REBOUCE_LOCK.wait(DEBOUNCE_TIME); // NOSONAR acts like a sleep

                    final var site = m_siteParams.get();
                    final var client = createClient(context);
                    final var siteId = site.getSiteId(client);

                    return listLists(client, siteId, m_showSystemLists.get());
                } catch (IOException | IllegalStateException | GraphServiceException ignored) { // NOSONAR
                    // avoid log spam in dialog
                    return List.of();
                } catch (InterruptedException ignored) { // NOSONAR bounce
                    return List.of();
                } finally {
                    REBOUCE_LOCK.compareAndExchange(current, null);
                }
            }
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

    static final class ListNameValidator implements CustomValidationProvider<String> {

        private static final AtomicReference<Thread> REBOUCE_LOCK = new AtomicReference<>();

        private Supplier<SharepointSiteParameters> m_siteParams;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            m_siteParams = initializer.computeFromValueSupplier(SharepointSiteParameters.Ref.class);
        }

        @Override
        public ValidationCallback<String> computeValidationCallback(final NodeParametersInput context) {
            return value -> validate(value, context);
        }

        private void validate(final String value, final NodeParametersInput context) throws InvalidSettingsException {

            CheckUtils.checkSetting(!value.isBlank(), "Please specify a name.");

            synchronized (REBOUCE_LOCK) {
                final var current = Thread.currentThread();
                try {
                    final var past = REBOUCE_LOCK.getAndSet(current);

                    if (past != null) {
                        past.interrupt();
                    }

                    REBOUCE_LOCK.wait(DEBOUNCE_TIME); // NOSONAR acts like a sleep

                    final var site = m_siteParams.get();
                    final var client = createClient(context);
                    final var siteId = site.getSiteId(client);

                    CheckUtils.checkSetting(client.sites(siteId).lists().buildRequest(getFilter(value)) //
                            .get().getCurrentPage().isEmpty(),
                            "List already exists. Choose a different name or select it in the existing lists.");
                } catch (IOException | IllegalStateException | GraphServiceException ignored) { // NOSONAR
                    // avoid log spam in dialog
                } catch (InterruptedException ignored) { // NOSONAR
                    // bounce
                } finally {
                    REBOUCE_LOCK.compareAndExchange(current, null);
                }
            }
        }

        private static List<Option> getFilter(final String name) {
            return List.of( //
                    new QueryOption("select", "system"), //
                    new QueryOption("filter", "displayName eq '%s'".formatted(name.replace("'", "''"))));
        }
    }

    abstract static class AbstractListMigration<T extends AbstractListParameters>
            implements NodeParametersMigration<T> {

        @Override
        public List<ConfigMigration<T>> getConfigMigrations() {
            return List.of(ConfigMigration.builder(this::loadCombinedSettings) //
                    .withDeprecatedConfigPath("list") //
                    .withDeprecatedConfigPath("listName") //
                    .build());
        }

        private T loadCombinedSettings(final NodeSettingsRO settings) {
            final var result = create();
            result.setLoadedLegacyCombined();

            final var id = settings.getString("list", "");
            final var dispAndInternName = settings.getString("listName", "");

            result.setListMode(supportsCreateList() ? ListMode.SELECT : null);
            var existing = result.getExistingList();

            if (!id.isEmpty() || !supportsCreateList()) { // nothing selected or no custom name possible
                final var pair = SharePointListUtils.getDisplayAndInternalListName(dispAndInternName);

                if (pair != null) {
                    existing.m_value = String.join("\0", id, pair.getSecond(), pair.getFirst());
                } else {
                    // should not happen; set display name to everything
                    existing.m_value = String.join("\0", id, "", dispAndInternName);
                }
                result.setNewListName("");
            } else {
                result.setListMode(ListMode.CREATE);
                existing.m_value = "";
                result.setNewListName(dispAndInternName);
            }
            return result;
        }

        protected abstract boolean supportsCreateList();

        protected abstract T create();
    }

    static final class BaseListMigration extends AbstractListMigration<BaseListParameters> {

        @Override
        protected BaseListParameters create() {
            return new BaseListParameters();
        }

        @Override
        protected boolean supportsCreateList() {
            return false;
        }
    }

    static final class CreateListMigration extends AbstractListMigration<CreateListParameters> {

        @Override
        protected CreateListParameters create() {
            return new CreateListParameters();
        }

        @Override
        protected boolean supportsCreateList() {
            return true;
        }
    }

    abstract static class AbstractListPersistor<T extends AbstractListParameters>
            implements NodeParametersPersistor<T> {

        @Override
        public T load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var result = createAndLoadSpecific(settings);
            final var existing = result.getExistingList();

            final var displayName = settings.getString("listDisplayName");
            final var internalName = settings.getString("listInternalName");
            final var id = settings.getString("listId");
            if (id.isEmpty() && displayName.isEmpty() && internalName.isEmpty()) {
                existing.m_value = "";
            } else {
                existing.m_value = String.join("\0", id, internalName, displayName);
            }

            return result;
        }

        @Override
        public void save(final T params, final NodeSettingsWO settings) {
            final var fields = SEP.split(params.getExistingList().m_value, 3);

            settings.addString("listId", fields.length > 0 ? fields[0] : "");
            settings.addString("listInternalName", fields.length > 1 ? fields[1] : "");
            settings.addString("listDisplayName", fields.length > 2 ? fields[2] : "");

        }

        protected abstract T createAndLoadSpecific(final NodeSettingsRO settings) throws InvalidSettingsException;

    }

    static final class BaseListPersistor extends AbstractListPersistor<BaseListParameters> {

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { "listId" }, //
                    { "listDisplayName" }, //
                    { "listInternalName" }, //
            };
        }

        @Override
        protected BaseListParameters createAndLoadSpecific(final NodeSettingsRO settings) {
            return new BaseListParameters();
        }
    }

    static final class CreateListPersistor extends AbstractListPersistor<CreateListParameters> {

        @Override
        protected CreateListParameters createAndLoadSpecific(final NodeSettingsRO settings)
                throws InvalidSettingsException {
            final var result = new CreateListParameters();
            try {
                final var mode = settings.getString("mode");
                result.m_listMode = ListMode.valueOf(mode);
                result.m_newListName = settings.getString("listCreateName");
            } catch (IllegalArgumentException e) {
                throw new InvalidSettingsException(e.getMessage(), e);
            }

            return result;
        }

        @Override
        public void save(final CreateListParameters params, final NodeSettingsWO settings) {
            super.save(params, settings);
            settings.addString("mode", params.m_listMode.name());
            settings.addString("listCreateName", params.m_newListName);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] { //
                    { "listId" }, //
                    { "listCreateName" }, //
                    { "listDisplayName" }, //
                    { "listInternalName" }, //
                    { "mode" }, //
            };
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

    /**
     * Can be used to change some widgets of this component by overwriting the
     * relevant methods and providing own values.
     */
    public static class SharepointListParametersModification implements Modification.Modifier {

        @Override
        public final void modify(final WidgetGroupModifier group) {
            final var desciption = getExistingStringDescriptionPostfix();
            if (desciption.isPresent()) {
                group.find(ExistingListWRef.class).modifyAnnotation(Widget.class) //
                        .withProperty("description", ExistingList.DESCRIPTION + desciption.get()).modify();
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

    static final class ExistingListWRef implements Modification.Reference {
    }
}
