package org.knime.ext.microsoft.authentication.providers.oauth2.interactive.storage;

/**
 * Enum for the different storage provider.
 *
 * @author Bjoern Lohrmann, KNIME GmbH
 */
public enum StorageType {

    /**
     * Stores tokens in an ephemeral in-memory cache.
     */
    MEMORY("Memory (stores token in-memory)"),

    /**
     * Stores tokens persistently in a file.
     */
    FILE("File (stores token in separate file)"),

    /**
     * Stores tokens persistently in node settings.
     */
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
