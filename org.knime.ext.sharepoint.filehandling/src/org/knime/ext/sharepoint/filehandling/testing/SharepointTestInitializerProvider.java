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
 *   2020-05-03 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling.testing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.knime.ext.sharepoint.filehandling.GraphApiConnector;
import org.knime.ext.sharepoint.filehandling.connections.SharepointConnection;
import org.knime.filehandling.core.testing.FSTestInitializer;
import org.knime.filehandling.core.testing.FSTestInitializerProvider;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * Itinializer provider for Sharepoint.
 * 
 * @author Alexander Bondaletov
 */
public class SharepointTestInitializerProvider implements FSTestInitializerProvider {
    private static final String FS_NAME = "azure-sharepoint";

    /**
     * {@inheritDoc}
     */
    @Override
    public FSTestInitializer setup(final Map<String, String> configuration) {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            setupProperties(configuration);
            IAuthenticationProvider authProvider = GraphApiConnector.connect(pool);
            SharepointConnection fsConnection = new SharepointConnection(authProvider,
                    configuration.get("site"));
            return new SharepointTestInitializer(fsConnection, configuration.get("drive"),
                    configuration.get("testfolder"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (URISyntaxException | ExecutionException | IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            pool.shutdown();
        }
        return null;
    }

    private static void setupProperties(final Map<String, String> configuration) {
        List<String> keys = Arrays.asList("username", "password", "tenant");
        for (String key : keys) {
            System.setProperty("sharepoint." + key, configuration.get(key));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFSType() {
        return FS_NAME;
    }

}
