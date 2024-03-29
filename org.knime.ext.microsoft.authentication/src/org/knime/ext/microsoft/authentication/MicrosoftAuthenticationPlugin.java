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
 *   2020-06-21 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication;

import javax.ws.rs.ext.RuntimeDelegate;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.knime.core.node.NodeLogger;
import org.osgi.framework.BundleContext;

import com.sun.ws.rs.ext.RuntimeDelegateImpl; // NOSONAR have to use com.sun internal class here due to OSGI issue

/**
 * Plugin activate for the Microsoft Authentication plugin.
 *
 * @author Alexander Bondaletov
 */
public class MicrosoftAuthenticationPlugin extends AbstractUIPlugin {
    private static final NodeLogger LOG = NodeLogger.getLogger(MicrosoftAuthenticationPlugin.class);

    // The shared instance.
    private static MicrosoftAuthenticationPlugin plugin;

    /**
     * The constructor.
     */
    public MicrosoftAuthenticationPlugin() {
        plugin = this; // NOSONAR standard pattern, class is a actually a singleton
    }

    /**
     * This method is called upon plug-in activation.
     *
     * @param context
     *            The bundle context.
     * @throws Exception
     *             If cause by super class.
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        try {
            // This method initializes the JAX-RS 1.x RuntimeDelegate, which is provided by
            // com.sun.jersey.core. Due to the way the RuntimeDelegate is initialized (using
            // ServiceLoader
            // and ThreadContextClassLoader), the default initialization might fail.
            if (RuntimeDelegate.getInstance() == null) {
                throw new IllegalStateException("No implementation found");
            }
        } catch (Throwable t) { // NOSONAR intentionally broad
            LOG.debug("Failed to initialize the JAX-RS 1.x RuntimeDelegate. Provided error message: " + t.getMessage());
            RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
        }
    }

    /**
     * This method is called when the plug-in is stopped.
     *
     * @param context
     *            The bundle context.
     * @throws Exception
     *             If cause by super class.
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        plugin = null; // NOSONAR standard pattern, otherwise memory leak
        super.stop(context);
    }

    /**
     * Returns the shared instance.
     *
     * @return The shared instance
     */
    public static MicrosoftAuthenticationPlugin getDefault() {
        return plugin;
    }
}
