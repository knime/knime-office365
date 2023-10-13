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
 *   2023-10-01 (Zkriya Rakhimberdiyev, Redfield SE): created
 */
package org.knime.ext.microsoft.authentication.azure.storage;

import static org.knime.credentials.base.CredentialPortViewUtil.obfuscate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import org.knime.credentials.base.Credential;
import org.knime.credentials.base.CredentialPortViewData;
import org.knime.credentials.base.CredentialType;
import org.knime.credentials.base.CredentialTypeRegistry;
import org.knime.credentials.base.NoOpCredentialSerializer;

/**
 * {@link Credential} implementation for Azure Shared Access Signature (SAS) URL
 * Credential.
 *
 * <p>
 * SAS URLs are URLs which contain a signature that was created using an Azure
 * Storage shared key. The URL grants a particular type of access to a storage
 * account or a folder/file therein.
 * </p>
 *
 * @author Zkriya Rakhimberdiyev, Redfield SE
 */
public class AzureStorageSasUrlCredential implements Credential {

    /**
     * The serializer class
     */
    public static class Serializer extends NoOpCredentialSerializer<AzureStorageSasUrlCredential> {
    }

    /**
     * Credential type.
     */
    public static final CredentialType TYPE = CredentialTypeRegistry //
            .getCredentialType("knime.AzureStorageSasUrlCredential");

    private URI m_sasUrl;

    /**
     * Default constructor for ser(de).
     */
    public AzureStorageSasUrlCredential() {
    }

    /**
     * Constructor.
     *
     * @param sasUrl
     *            the SAS URL
     */
    public AzureStorageSasUrlCredential(final URI sasUrl) {
        m_sasUrl = sasUrl;
    }

    /**
     * @return the sasUrl
     */
    public URI getSasUrl() {
        return m_sasUrl;
    }

    @Override
    public CredentialType getType() {
        return TYPE;
    }

    @Override
    public CredentialPortViewData describe() {
        final var sections = new LinkedList<CredentialPortViewData.Section>();
        sections.add(
                new CredentialPortViewData.Section("Azure Storage Shared Access Signature (SAS) URL", new String[][] {
                        { "Field", "Value" }, //
                        { "SAS URL", obfuscateSASUrl(m_sasUrl) },//
        }));
        return new CredentialPortViewData(sections);
    }

    private static String obfuscateSASUrl(final URI sasUrl) {
        try {
            return new URI(sasUrl.getScheme(), //
                    sasUrl.getAuthority(), //
                    sasUrl.getPath(), //
                    obfuscate(sasUrl.getQuery()), // the query part contains the confidential SAS token
                    null).toString();
        } catch (URISyntaxException ex) { // NOSONAR no need to log it
            return obfuscate(sasUrl.toString());
        }
    }
}
