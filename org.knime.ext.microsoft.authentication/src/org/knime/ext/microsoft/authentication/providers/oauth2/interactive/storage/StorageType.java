package org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage;

/**
 * Enum for the different storage provider.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public enum StorageType {

    MEMORY("Memory (stores token in-memory)"), //
    FILE("File (stores token in separate file)"), //
    SETTINGS("Node (stores token in node settings)");

    private String m_title;

    private StorageType(final String title) {
        m_title = title;
    }

    /**
     * @return the title
     */
    public String getTitle() {
        return m_title;
    }
}
