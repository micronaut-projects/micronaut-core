package org.particleframework.jdbc;

import org.particleframework.core.reflect.ClassUtils;

public enum EmbeddedDatabaseConnection {
    /**
     * No Connection.
     */
    NONE(null, null),

    /**
     * H2 Database Connection.
     */
    H2("org.h2.Driver",
            "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"),

    /**
     * Derby Database Connection.
     */
    DERBY("org.apache.derby.jdbc.EmbeddedDriver",
            "jdbc:derby:memory:%s;create=true"),

    /**
     * HSQL Database Connection.
     */
    HSQL("org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:%s");

    private static final String DEFAULT_DATABASE_NAME = "devdb";


    private final String driverClass;

    private final String url;

    EmbeddedDatabaseConnection(String driverClass,
                               String url) {
        this.driverClass = driverClass;
        this.url = url;
    }

    /**
     * Returns the driver class name.
     * @return the driver class name
     */
    public String getDriverClassName() {
        return this.driverClass;
    }

    /**
     * Returns the URL for the connection using the default database name.
     * @return the connection URL
     */
    public String getUrl() {
        return getUrl(DEFAULT_DATABASE_NAME);
    }

    /**
     * Returns the URL for the connection using the specified {@code databaseName}.
     * @param databaseName the name of the database
     * @return the connection URL
     */
    public String getUrl(String databaseName) {
        if (databaseName == null) {
            throw new IllegalArgumentException("Cannot get an embedded database URL with a null database name.");
        }
        return String.format(this.url, databaseName);
    }


    /**
     * Convenience method to determine if a given driver class name represents an embedded
     * database type.
     * @param driverClass the driver class
     * @return true if the driver class is one of the embedded types
     */
    public static boolean isEmbedded(String driverClass) {
        return driverClass != null && (driverClass.equals(HSQL.driverClass)
                || driverClass.equals(H2.driverClass)
                || driverClass.equals(DERBY.driverClass));
    }



    /**
     * Returns the most suitable {@link EmbeddedDatabaseConnection} for the given class
     * loader.
     * @param classLoader the class loader used to check for classes
     * @return an {@link EmbeddedDatabaseConnection} or {@link #NONE}.
     */
    public static EmbeddedDatabaseConnection get(ClassLoader classLoader) {
        for (EmbeddedDatabaseConnection candidate : EmbeddedDatabaseConnection.values()) {
            if (candidate != NONE && ClassUtils.isPresent(candidate.getDriverClassName(), classLoader)) {
                return candidate;
            }
        }
        return NONE;
    }

}
