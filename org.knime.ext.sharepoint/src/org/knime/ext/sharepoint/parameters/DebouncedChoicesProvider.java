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
 *   2025-12-17 (loescher): created
 */
package org.knime.ext.sharepoint.parameters;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.StateProvider;

/**
 * A state provider that debounces calls to
 * {@link #computeState(NodeParametersInput)}.
 *
 * @param <T>
 *            the type returned by state computation.
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public abstract class DebouncedChoicesProvider<T> {

    private final AtomicReference<Thread> m_debounceLock;
    private final AtomicLong m_lastTimeCalled;

    private final long m_debounceTime;

    /**
     * Initialize a new provider.
     *
     * @param debounceTime
     *            the time to wait until the value is debounced.
     * @param debounceLock
     *            the thread which called the state provider last. This value must
     *            be shared by all instances of the implementing class.
     * @param lastTimeCalled
     *            the last time the state provider was called in milliseconds.This
     *            value must be shared by all instances of the implementing class.
     */
    protected DebouncedChoicesProvider(final long debounceTime, final AtomicReference<Thread> debounceLock,
            final AtomicLong lastTimeCalled) {
        this.m_debounceLock = debounceLock;
        this.m_lastTimeCalled = lastTimeCalled;
        m_debounceTime = debounceTime;
    }

    /**
     * Compute the state.
     *
     * @param context
     *            the state computation context.
     * @return the computed value
     *
     * @see StateProvider#computeState(NodeParametersInput)
     */
    public final T computeState(final NodeParametersInput context) {
        final var preCheck = preDebounceCheck(context);
        if (preCheck.isPresent()) {
            return preCheck.get();
        }

        synchronized (m_debounceLock) {
            final var now = System.currentTimeMillis();
            final var current = Thread.currentThread();

            try {
                final var past = m_debounceLock.getAndSet(current);
                final var then = m_lastTimeCalled.getAndSet(now);

                if (past != null) {
                    past.interrupt();
                }

                // only wait, if past was still running
                // or the last call was less than debounce time ago
                if (past != null || (now - then) < m_debounceTime) {
                    m_debounceLock.wait(m_debounceTime); // NOSONAR acts like a sleep
                }

                return computeStateDebounced(context);
            } catch (InterruptedException ignored) { // NOSONAR bounce
                return bounced();
            } finally {
                m_debounceLock.compareAndExchange(current, null);
            }
        }
    }

    /**
     * This method is called after the debounce time has passed and the thread was
     * allowed to continue.
     *
     * @param context
     *            the state computation context.
     * @return the computed value
     */
    protected abstract T computeStateDebounced(final NodeParametersInput context);

    /**
     * Compute a value which is not bounced. Can be used for easy checks that can
     * return faster.
     *
     * @param context
     *            the state computation context.
     * @return the value, if it could be computed.
     */
    protected Optional<T> preDebounceCheck(final NodeParametersInput context) {
        return Optional.empty();
    }

    /**
     * @return the value to be used if the thread was bounced, i.e. stopped because
     *         a new thread acquired the bounce lock.
     */
    protected abstract T bounced();
}
