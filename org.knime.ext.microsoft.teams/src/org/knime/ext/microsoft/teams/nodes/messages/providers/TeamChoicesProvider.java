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
 *   2025-11-19 (halilyerlikaya): created
 */
package org.knime.ext.microsoft.teams.nodes.messages.providers;

/**
 *
 * @author Halil Yerlikaya, KNIME GmbH, Berlin, Germany
 */

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.widget.choices.StringChoice;

import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request;

/**
 * ChoicesProvider for available Teams.
 *
 * Loads the user's joined Teams and exposes them as dropdown entries.
 */
public final class TeamChoicesProvider extends AbstractTeamsChoicesProvider {

    private Supplier<String> m_teamSupplier;

    @Override
    public void init(final StateProviderInitializer initializer) {
        initializer.computeAfterOpenDialog();
        m_teamSupplier = initializer.computeFromValueSupplier(
                org.knime.ext.microsoft.teams.nodes.messages.TeamsMessageSenderNodeSettings.TeamRef.class);
    }

    @Override
    public List<StringChoice> computeState(final NodeParametersInput context) {
        try {
            final GraphServiceClient<Request> graphClient = getGraphClient(context);
            var page = graphClient.me().joinedTeams().buildRequest().select("id,displayName").get();
            var teamChoices = new ArrayList<StringChoice>();

            while (true) {
                for (var team : page.getCurrentPage()) {
                    teamChoices.add(new StringChoice(team.id, team.displayName));
                }
                if (page.getNextPage() == null) {
                    break;
                }
                page = page.getNextPage().buildRequest().get();
            }

            return teamChoices;
        } catch (Exception e) {
            final var current = m_teamSupplier != null ? m_teamSupplier.get() : null;
            return (current == null || current.isBlank())
                    ? List.of()
                    : List.of(new StringChoice(current, current));
        }
    }
}
