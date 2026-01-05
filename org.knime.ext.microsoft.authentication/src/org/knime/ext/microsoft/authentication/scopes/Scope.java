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
 *   2020-06-06 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.scopes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enum holding different OAuth2 scopes for the Microsoft Authenticator node.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public enum Scope {

    /**
     * Sites.Read.All scope.
     */
    SITES_READ("Sharepoint files and list items (read-only)", "Sites.Read.All", ScopeType.DELEGATED),

    /**
     * Sites.ReadWrite.All scope.
     */
    SITES_READ_WRITE("Sharepoint files and list items", "Sites.ReadWrite.All", ScopeType.DELEGATED),

    /**
     * Sites.Manage.All scope.
     */
    SITES_MANAGE_ALL("Sharepoint files, lists and list items", "Sites.Manage.All",
            ScopeType.DELEGATED),

    /**
     * Azure Databricks delegated scope.
     */
    AZURE_DATABRICKS("Azure Databricks", "2ff814a6-3304-4ab8-85cb-cd0e6f879c1d/user_impersonation",
            ScopeType.DELEGATED),

    /**
     * Azure Databricks application scope.
     */
    AZURE_DATABRICKS_APP("Azure Databricks", "2ff814a6-3304-4ab8-85cb-cd0e6f879c1d/.default", ScopeType.APPLICATION),

    /**
     * Resource identifier of the Microsoft Graph
     */
    GRAPH_APP("Sharepoint", "https://graph.microsoft.com/.default", ScopeType.APPLICATION),

    /**
     * Directory.Read.All scope.
     */
    DIRECTORY_READ(
            "<html>User Groups (Read) <i>Note: Only used to list groups in Sharepoint Connector dialog. "
                    + "This is a sensitive scope which requires admin consent.</i></html>",
            "Directory.Read.All", ScopeType.DELEGATED),
    /**
     * User.Read scope.
     */
    USER_READ(
            "<html>User Groups (IDs only) <i>Note: Only used to list groups in Sharepoint Connector dialog. "
                    + "This scope only allows to see technical IDs of groups, not the human-readable name.</i></html>",
            "User.Read", ScopeType.DELEGATED),
    /**
     * Azure Blob storage scope
     */
    AZURE_BLOB_STORAGE("Azure Blob Storage/Azure Data Lake Storage Gen2",
            "https://%s.blob.core.windows.net/user_impersonation", ScopeType.DELEGATED),

    /**
     * Azure SQL Database scope
     */
    AZURE_SQL_DATABASE("Azure SQL Database", "https://database.windows.net/user_impersonation", ScopeType.DELEGATED),

    /**
     * Resource identifier of the Azure SQL database
     */
    AZURE_SQL_DATABASE_APP("Azure SQL Database", "https://database.windows.net/.default", ScopeType.APPLICATION),

    /**
     * Power BI
     */
    POWER_BI("Power BI", "https://analysis.windows.net/powerbi/api/Dataset.ReadWrite.All "
            + "https://analysis.windows.net/powerbi/api/Workspace.Read.All", ScopeType.DELEGATED),

    /**
     * Resource identifier of the Power BI
     */
    POWER_BI_APP("Power BI", "https://analysis.windows.net/powerbi/api/.default", ScopeType.APPLICATION),

    /**
     * Fabric
     */
    FABRIC("Fabric", "https://api.fabric.microsoft.com/Workspace.Read.All", ScopeType.DELEGATED),

    /**
     * Fabric application.
     */
    FABRIC_APP("Fabric", "https://api.fabric.microsoft.com/.default", ScopeType.APPLICATION),

    /**
     * Exchange Online
     */
    OUTLOOK("Exchange Online",
            "https://outlook.office.com/IMAP.AccessAsUser.All " + "https://outlook.office.com/SMTP.Send",
            ScopeType.DELEGATED),

    /**
     * Azure OpenAI
     */
    AZURE_OPENAI("Azure OpenAI", "https://cognitiveservices.azure.com/user_impersonation", ScopeType.DELEGATED),

    /**
     * Azure OpenAI application
     */
    AZURE_OPENAI_APP("Azure OpenAI", "https://cognitiveservices.azure.com/.default", ScopeType.APPLICATION);

    private static final Map<String, Scope> SCOPES = new HashMap<>();
    static {
        for (Scope scopeEnum : Scope.values()) {
            if (SCOPES.containsKey(scopeEnum.getScope())) {
                // some sanity checking
                throw new IllegalStateException("Duplicate scope " + scopeEnum.getScope());
            }
            SCOPES.put(scopeEnum.getScope(), scopeEnum);
        }
    }

    private final String m_title;
    private final String m_scope;
    private final ScopeType m_scopeType;

    private Scope(final String title, final String scope, final ScopeType scopeType) {
        m_title = title;
        m_scope = scope;
        m_scopeType = scopeType;
    }

    /**
     * @return The string representation of the scope.
     */
    public String getScope() {
        return m_scope;
    }

    /**
     * @return the title
     */
    public String getTitle() {
        return m_title;
    }

    /**
     * @return the type
     */
    public ScopeType getScopeType() {
        return m_scopeType;
    }

    /**
     * OAuth2 tokens grants a set of permissions for a *single* resource. A token
     * cannot grant permissions on different resources. This method checks whether
     * two {@link Scope}s can potentially be requested together for the same token.
     * This method is more
     *
     * @param otherScope
     *            The other scope for which to check whether it can be grouped
     *            together with this one.
     * @return true, if
     */
    public boolean canBeGroupedWith(final Scope otherScope) {
        // this is temporary until we can differentiate the scopes on the authorization
        // URL, from those in the token request MSAL4J does not support this yet, which
        // means the token request will fail for scopes from multiple resources.
        return ScopeResourceUtil.parseResource(getScope())
                .equals(ScopeResourceUtil.parseResource(otherScope.getScope()));
    }

    /**
     * Returns the {@link Scope} enum instance for the given scope string.
     *
     * @param scope
     *            the string representation of the scope
     * @return the {@link Scope} enum instance
     */
    public static Scope fromScope(final String scope) {
        return SCOPES.get(scope);
    }

    /**
     * Lists all scopes of the given scope type.
     *
     * @param scopeType
     *            scope type {@link ScopeType}
     * @return the list of scopes
     */
    public static List<Scope> listByScopeType(final ScopeType scopeType) {
        return Stream.of(values()).filter(s -> s.getScopeType() == scopeType).collect(Collectors.toList());
    }
}
