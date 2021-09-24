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
 *   6 Apr 2020 (Temesgen H. Dadi, KNIME GmbH, Berlin, Germany): created
 */
package org.knime.ext.sharepoint.filehandling.node.listreader;

import java.util.LinkedHashMap;
import java.util.Map;

import org.knime.filehandling.core.node.table.reader.config.ReaderSpecificConfig;

/**
 * “SharePoint List Reader” {@link ReaderSpecificConfig} implementation.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author Lars Schweikardt, KNIME GmbH, Konstanz, Germany
 */
public final class SharepointListReaderConfig implements ReaderSpecificConfig<SharepointListReaderConfig> {

    private boolean m_idDisplayNameMappingPresent = false;

    private Map<String, String> m_idDisplayNameMapping = new LinkedHashMap<>();
    // TODO put the site settings in here?

    /**
     * @return a mapping between the “name” field which is a kind of ID and the
     *         “displayName” which is purely visual and used for the column names
     */
    public Map<String, String> getIdDisplayNameMapping() {
        return m_idDisplayNameMapping;
    }

    /**
     * @return whether the ID name and display name mapping has been calculated.
     */
    public boolean isIdDisplayNameMappingPresent() {
        return m_idDisplayNameMappingPresent;
    }

    /**
     * @param idDisplayNameMappingPresent
     *            whether the ID name and display name mapping has been calculated.
     */
    public void setIdDisplayNameMappingPresent(final boolean idDisplayNameMappingPresent) {
        m_idDisplayNameMappingPresent = idDisplayNameMappingPresent;
    }

    @Override
    public SharepointListReaderConfig copy() {
        final var res = new SharepointListReaderConfig();
        res.m_idDisplayNameMappingPresent = m_idDisplayNameMappingPresent;
        m_idDisplayNameMapping.forEach(res.m_idDisplayNameMapping::put);
        return res;
    }
}
