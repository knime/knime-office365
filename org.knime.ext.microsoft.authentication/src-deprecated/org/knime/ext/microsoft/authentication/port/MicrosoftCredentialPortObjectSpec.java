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
 *   2020-06-04 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.port;

import java.util.UUID;

import org.knime.credentials.base.CredentialPortObject;
import org.knime.credentials.base.CredentialPortObjectSpec;
import org.knime.credentials.base.CredentialType;

/**
 * Specification for the legacy Microsoft Credential port type. The only purpose
 * of this class is to be referenced by the port type definition in plugin.xml.
 *
 * @author Alexander Bondaletov
 * @deprecated Since 5.2. Use {@link CredentialPortObject} instead.
 */
@Deprecated(since = "5.2")
public class MicrosoftCredentialPortObjectSpec extends CredentialPortObjectSpec {

    /**
     * Serializer class.
     */
    public static final class Serializer
            extends AbstractSimplePortObjectSpecSerializer<MicrosoftCredentialPortObjectSpec> {
    }

    /**
     * Don't use, framework constructor.
     */
    public MicrosoftCredentialPortObjectSpec() {
        super(null, null);
    }

    /**
     * Creates a new instance. During node mode configure() it is okay to call this
     * method with one or both arguments null.
     *
     * @param type
     *            The credential type. May be null, but then cacheId must also be
     *            null.
     * @param cacheId
     *            The cache id. May be null, if currently unknown.
     */
    public MicrosoftCredentialPortObjectSpec(final CredentialType type, final UUID cacheId) {
        super(type, cacheId);
    }
}