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
 *   2020-05-10 (Alexander Bondaletov): created
 */
package org.knime.ext.sharepoint.filehandling;

import java.io.IOException;
import java.nio.file.AccessDeniedException;

import org.knime.ext.sharepoint.GraphApiUtil;
import org.knime.ext.sharepoint.filehandling.fs.SharepointFileSystem;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.http.GraphError;
import com.microsoft.graph.http.GraphServiceException;

/**
 * Utility class for Graph API.
 *
 * @author Alexander Bondaletov
 */
public final class FSGraphApiUtil {
    /**
     * Error code of the <code>nameAlreadyExists</code>
     * {@link GraphServiceException}.
     */
    public static final String NAME_ALREADY_EXISTS_CODE = "nameAlreadyExists";

    private static final String SEPARATOR_REPLACEMENT = "$_$";
    private static final String ACCESS_DENIED_CODE = "accessDenied";

    private FSGraphApiUtil() {
        // hide constructor for Utils class
    }

    /**
     *
     * <p>
     * Attempts to unwrap provided {@link ClientException}.
     * </p>
     * <p>
     * Firstly, it iterates through the whole 'cause-chain' by calling
     * {@link GraphApiUtil#unwrapIOE(ClientException)} in attempt to find
     * {@link IOException} and throws it if one is found.<br>
     * </p>
     * <p>
     * Secondly, if provided exception is instance of {@link GraphServiceException}
     * then {@link GraphError} code is checked and {@link AccessDeniedException} is
     * thrown when necessary.<br>
     * </p>
     * <p>
     * Otherwise, returns {@link WrappedGraphException} with user-friendly error
     * message from {@link GraphServiceException}, or the original exception in case
     * it is not an instance of {@link GraphServiceException}.<br>
     * </p>
     *
     * @param ex
     *            The exception.
     * @return {@link WrappedGraphException} instance or the original exception in
     *         case it is not an instance of {@link GraphServiceException}.
     * @throws IOException
     */
    public static RuntimeException unwrapClientEx(final ClientException ex) throws IOException {
        GraphApiUtil.unwrapIOE(ex);

        if (ex instanceof GraphServiceException) {
            GraphError error = ((GraphServiceException) ex).getServiceError();
            if (ACCESS_DENIED_CODE.equals(error.code)) {
                var ade = new AccessDeniedException(error.message);
                ade.initCause(ex);
                throw ade;
            }
            return new WrappedGraphException((GraphServiceException) ex);
        }

        return ex;
    }

    /**
     * Escapes the drive name by replacing '/' characters with '$_$' sequence.
     *
     * @param name
     *            The drive name.
     * @return Escaped drive name.
     */
    public static final String escapeDriveName(final String name) {
        return name.replace(SharepointFileSystem.PATH_SEPARATOR, SEPARATOR_REPLACEMENT);
    }

    /**
     * Wrapped {@link GraphServiceException} with more user-friendly error message
     * extracted
     *
     * @author Alexander Bondaletov
     */
    public static class WrappedGraphException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private WrappedGraphException(final GraphServiceException ex) {
            super(ex.getServiceError().message, ex);
        }
    }
}
