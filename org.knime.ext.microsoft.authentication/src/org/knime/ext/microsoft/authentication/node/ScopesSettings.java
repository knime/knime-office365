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
 *   2023-08-22 (Alexander Bondaletov, Redfield SE): created
 */
package org.knime.ext.microsoft.authentication.node;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.LayoutGroup;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.NodeSettingsPersistorWithConfigKey;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.field.Persist;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.rule.OneOfEnumCondition;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Signal;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.IdAndText;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.CustomScope.CustomScopesPersistor;
import org.knime.ext.microsoft.authentication.node.ScopesSettings.StandardScope.StandardScopesPersistor;
import org.knime.ext.microsoft.authentication.port.oauth2.Scope;
import org.knime.ext.microsoft.authentication.port.oauth2.ScopeType;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The scopes settings for the Microsoft Authenticator node.
 *
 * @author Alexander Bondaletov, Redfield SE
 */
@SuppressWarnings("restriction")
public class ScopesSettings implements LayoutGroup, DefaultNodeSettings {

    @Widget(title = "Scope selection type", //
            description = """
                    Whether to select from a list of predefined standard scopes, or enter custom scopes manually.
                    Scopes define the level of access that will be requested.
                    """, //
            hideTitle = true)
    @Signal(condition = ScopesSelectionType.IsCustom.class)
    @ValueSwitchWidget
    ScopesSelectionType m_scopesSelectionType = ScopesSelectionType.STANDARD;

    enum ScopesSelectionType {
        STANDARD, CUSTOM;

        static class IsCustom extends OneOfEnumCondition<ScopesSelectionType> {
            @Override
            public ScopesSelectionType[] oneOf() {
                return new ScopesSelectionType[] { CUSTOM };
            }

        }
    }

    @Widget(title = "Standard scopes", description = "Select from a fixed list of standard scopes", hideTitle = true)
    @ArrayWidget(addButtonText = "Add")
    @Effect(signals = ScopesSelectionType.IsCustom.class, type = EffectType.HIDE)
    @Persist(customPersistor = StandardScopesPersistor.class)
    StandardScope[] m_standardScopes = new StandardScope[0];

    static final class StandardScope implements DefaultNodeSettings {
        @Widget(hideTitle = true)
        @ChoicesWidget(choices = StandardScopesChoicesProvider.class)
        String m_id;

        StandardScope(final String id) {
            m_id = id;
        }

        StandardScope() {
        }

        static class StandardScopesPersistor extends NodeSettingsPersistorWithConfigKey<StandardScope[]> {
            @Override
            public StandardScope[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
                return Stream.of(settings.getStringArray(getConfigKey())) //
                        .map(StandardScope::new) //
                        .toArray(StandardScope[]::new);
            }

            @Override
            public void save(final StandardScope[] obj, final NodeSettingsWO settings) {
                var strings = Stream.of(obj).map(s -> s.m_id).toArray(String[]::new);
                settings.addStringArray(getConfigKey(), strings);
            }
        }

        static class StandardScopesChoicesProvider implements ChoicesProvider {
            @Override
            public IdAndText[] choicesWithIdAndText(final DefaultNodeSettingsContext context) {
                return Scope.listByScopeType(ScopeType.DELEGATED).stream() //
                        .filter(s -> s != Scope.OTHER && s != Scope.OTHERS) //
                        .map(s -> new IdAndText(s.name(), stripHtml(s.getTitle()))) //
                        .toArray(IdAndText[]::new);
            }

            private static String stripHtml(final String str) {
                return str.replace("<html>", "") //
                        .replace("</html>", "") //
                        .replace("<i>", "") //
                        .replace("</i>", "");
            }
        }
    }

    @Widget(title = "Custom scopes", description = "Enter the a custom lst of scopes to request.", hideTitle = true)
    @ArrayWidget(addButtonText = "Add")
    @Effect(signals = ScopesSelectionType.IsCustom.class, type = EffectType.SHOW)
    @Persist(customPersistor = CustomScopesPersistor.class)
    CustomScope[] m_customScopes = new CustomScope[0];

    static class CustomScope implements DefaultNodeSettings {
        @Widget(hideTitle = true)
        String m_scope;

        CustomScope(final String scope) {
            m_scope = scope;
        }

        CustomScope() {
        }

        static class CustomScopesPersistor extends NodeSettingsPersistorWithConfigKey<CustomScope[]> {
            @Override
            public CustomScope[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
                return Stream.of(settings.getStringArray(getConfigKey())) //
                        .map(CustomScope::new) //
                        .toArray(CustomScope[]::new);
            }

            @Override
            public void save(final CustomScope[] obj, final NodeSettingsWO settings) {
                var strings = Stream.of(obj).map(s -> s.m_scope).toArray(String[]::new);
                settings.addStringArray(getConfigKey(), strings);
            }
        }
    }

    /**
     * Validates the settings.
     *
     * @throws InvalidSettingsException
     */
    public void validate() throws InvalidSettingsException {
        if (m_scopesSelectionType == ScopesSelectionType.STANDARD
                && (m_standardScopes == null || m_standardScopes.length == 0)) {
            throw new InvalidSettingsException("Please specify at least one scope");
        }
        if (m_scopesSelectionType == ScopesSelectionType.CUSTOM
                && (m_customScopes == null || m_customScopes.length == 0)) {
            throw new InvalidSettingsException("Please specify at least one scope");
        }

        if (m_scopesSelectionType == ScopesSelectionType.STANDARD) {
            validateScopesAreNotBlank(m_standardScopes, s -> s.m_id);
            validateStandardScopesGrouping();
        } else {
            validateScopesAreNotBlank(m_customScopes, s -> s.m_scope);
        }
    }

    private void validateStandardScopesGrouping() throws InvalidSettingsException {
        Set<Scope> scopes = EnumSet.noneOf(Scope.class);
        for (StandardScope s : m_standardScopes) {
            var scopeToCheck = Scope.valueOf(s.m_id);

            for (Scope scopeToCompare : scopes) {
                if (!scopeToCheck.canBeGroupedWith(scopeToCompare)) {
                    throw new InvalidSettingsException(
                            String.format("The '%s' scope cannot be grouped with '%s' scope.", scopeToCheck.getTitle(),
                                    scopeToCompare.getTitle()));
                }
            }

            scopes.add(scopeToCheck);
        }
    }

    private static <T> void validateScopesAreNotBlank(final T[] scopes, final Function<T, String> toString)
            throws InvalidSettingsException {
        var pos = 1;
        for (final var scope : scopes) {
            if (StringUtils.isBlank(toString.apply(scope))) {
                throw new InvalidSettingsException("Please remove blank scope at position " + pos);
            }

            pos++;
        }
    }

    /**
     * @return The selected scopes as a set of strings.
     */
    @JsonIgnore
    public Set<String> getScopesStringSet() {
        if (m_scopesSelectionType == ScopesSelectionType.STANDARD) {
            return Stream.of(m_standardScopes) //
                    .map(s -> Scope.valueOf(s.m_id).getScope()) //
                    .collect(Collectors.toSet());
        } else {
            return Stream.of(m_customScopes) //
                    .map(s -> s.m_scope) //
                    .collect(Collectors.toSet());
        }

    }
}
