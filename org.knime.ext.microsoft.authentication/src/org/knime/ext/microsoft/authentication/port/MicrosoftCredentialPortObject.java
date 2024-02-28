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

import java.util.Objects;

import javax.swing.JComponent;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.ModelContentRO;
import org.knime.core.node.ModelContentWO;
import org.knime.core.node.port.AbstractSimplePortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;

/**
 * Port object containing a {@link MicrosoftCredential}.
 *
 * @author Alexander Bondaletov
 */
public class MicrosoftCredentialPortObject extends AbstractSimplePortObject {

    /**
     * Serializer class
     */
    public static final class Serializer extends AbstractSimplePortObjectSerializer<MicrosoftCredentialPortObject> {
    }

    /**
     * The type of this port.
     */
    @SuppressWarnings("hiding")
    public static final PortType TYPE = PortTypeRegistry.getInstance().getPortType(MicrosoftCredentialPortObject.class);

    private MicrosoftCredentialPortObjectSpec m_spec;

    /**
     * Creates new instance
     */
    public MicrosoftCredentialPortObject() {
        this(null);
    }

    /**
     * Creates new instance with a given spec.
     *
     * @param spec
     *            The spec.
     *
     */
    public MicrosoftCredentialPortObject(final MicrosoftCredentialPortObjectSpec spec) {
        m_spec = spec;
    }

    /**
     * @return The microsoft connection object.
     */
    public MicrosoftCredential getMicrosoftCredentials() {
        return m_spec.getMicrosoftCredential();
    }

    @Override
    public String getSummary() {
        return getMicrosoftCredentials().getSummary();
    }

    @Override
    public PortObjectSpec getSpec() {
        return m_spec;
    }

    @Override
    protected void save(final ModelContentWO model, final ExecutionMonitor exec) throws CanceledExecutionException {
        // nothing to do

    }

    @Override
    protected void load(final ModelContentRO model, final PortObjectSpec spec, final ExecutionMonitor exec)
            throws InvalidSettingsException, CanceledExecutionException {
        m_spec = (MicrosoftCredentialPortObjectSpec) spec;
    }

    @Override
    public JComponent[] getViews() {
        return m_spec.getViews();
    }

    @Override
    public int hashCode() {
        final var prime = 31;
        var result = super.hashCode();
        result = prime * result + Objects.hash(m_spec);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (this == obj) {
            return true;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final var other = (MicrosoftCredentialPortObject) obj;
        return Objects.equals(m_spec, other.m_spec);
    }
}