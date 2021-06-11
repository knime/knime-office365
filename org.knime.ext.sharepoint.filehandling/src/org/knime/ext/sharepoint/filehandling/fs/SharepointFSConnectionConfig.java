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
 *   2021-06-04 Moditha Hewasinghage: created
 */
package org.knime.ext.sharepoint.filehandling.fs;

import java.time.Duration;

import org.knime.filehandling.core.connections.meta.base.BaseFSConnectionConfig;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Sharepoint connection configuration implementation.
 *
 * @author Moditha Hewasinghage
 */
@SuppressWarnings("deprecation")
public class SharepointFSConnectionConfig extends BaseFSConnectionConfig {

    /**
     * Default read/connect timeout in seconds.
     */
    public static final int DEFAULT_TIMEOUT = 20;

    private final IAuthenticationProvider m_authenticationProvider;

    private Duration m_connectionTimeOut;

    private Duration m_readTimeOut;

    private String m_webURL;
    private String m_group;
    private SiteMode m_mode;
    private String m_subsite;


    /**
     *
     * @param workingDirectory
     * @param authenticationProvider
     */
    public SharepointFSConnectionConfig(final String workingDirectory,
            final IAuthenticationProvider authenticationProvider) {
        super(workingDirectory, true);
        m_authenticationProvider = authenticationProvider;
    }

    /**
     * @return the webURL
     */
    public String getWebURL() {
        return m_webURL;
    }

    /**
     * @param webURL
     *            the webURL to set
     */
    public void setWebURL(final String webURL) {
        m_webURL = webURL;
    }

    /**
     * @return the group
     */
    public String getGroup() {
        return m_group;
    }

    /**
     * @param group
     *            the group to set
     */
    public void setGroup(final String group) {
        m_group = group;
    }

    /**
     * @return the mode
     */
    public SiteMode getMode() {
        return m_mode;
    }

    /**
     * @param mode
     *            the mode to set
     */
    public void setMode(final SiteMode mode) {
        m_mode = mode;
    }

    /**
     * @return the subsite
     */
    public String getSubsite() {
        return m_subsite;
    }

    /**
     * @param subsite
     *            the subsite to set
     */
    public void setSubsite(final String subsite) {
        m_subsite = subsite;
    }

    /**
     * @return the authenticationProvider
     */
    public IAuthenticationProvider getAuthenticationProvider() {
        return m_authenticationProvider;
    }

    /**
     * @return the connectionTimeOut
     */
    public Duration getConnectionTimeOut() {
        return m_connectionTimeOut;
    }

    /**
     * @param connectionTimeOut
     *            the connectionTimeOut to set
     */
    public void setConnectionTimeOut(final Duration connectionTimeOut) {
        m_connectionTimeOut = connectionTimeOut;
    }

    /**
     * @return the readTimeOut
     */
    public Duration getReadTimeOut() {
        return m_readTimeOut;
    }

    /**
     * @param readTimeOut
     *            the readTimeOut to set
     */
    public void setReadTimeOut(final Duration readTimeOut) {
        m_readTimeOut = readTimeOut;
    }

    /**
     * Sharepoint site mode
     *
     */
    public enum SiteMode { //
        /**
         * Root site
         */
        ROOT("Root site"), //
        /**
         * Web URL
         */
        WEB_URL("Web URL"), //
        /**
         * Group site
         */
        GROUP("Group site");

        private String m_selectorLabel;

        private SiteMode(final String selectorLable) {
            m_selectorLabel = selectorLable;
        }

        /**
         * @return the selectorLabel
         */
        public String getSelectorLabel() {
            return m_selectorLabel;
        }
    }
}
