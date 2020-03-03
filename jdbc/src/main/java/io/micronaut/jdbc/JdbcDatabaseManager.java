/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jdbc;

import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Stores information on popular JDBC drivers.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class JdbcDatabaseManager {

    private static List<JdbcDatabase> databases = new ArrayList<>(16);

    static {
        databases.add(new EmbeddedJdbcDatabase("org.h2.Driver", "h2", "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"));
        databases.add(new EmbeddedJdbcDatabase("org.apache.derby.jdbc.EmbeddedDriver", "SELECT 1 FROM SYSIBM.SYSDUMMY1", new String[]{"derby"}, "jdbc:derby:memory:%s;create=true"));
        databases.add(new EmbeddedJdbcDatabase("org.hsqldb.jdbc.JDBCDriver", "select 1 from INFORMATION_SCHEMA.SYSTEM_USERS", new String[]{"hsqldb"}, "jdbc:hsqldb:mem:%s"));

        databases.add(new JdbcDatabase("com.mysql.jdbc.Driver", "mysql"));
        databases.add(new JdbcDatabase("oracle.jdbc.OracleDriver", "SELECT 1 FROM DUAL", new String[]{"oracle"}));
        databases.add(new JdbcDatabase("org.postgresql.Driver", "postgresql"));
        databases.add(new JdbcDatabase("com.microsoft.sqlserver.jdbc.SQLServerDriver", "sqlserver"));
        databases.add(new JdbcDatabase("org.sqlite.JDBC", "sqlite"));
        databases.add(new JdbcDatabase("org.mariadb.jdbc.Driver", "mariadb"));
        databases.add(new JdbcDatabase("com.google.appengine.api.rdbms.AppEngineDriver", "gae"));
        databases.add(new JdbcDatabase("net.sourceforge.jtds.jdbc.Driver", "jtds"));
        databases.add(new JdbcDatabase("org.firebirdsql.jdbc.FBDriver", "SELECT 1 FROM RDB$DATABASE", new String[]{"firebirdsql"}));
        databases.add(new JdbcDatabase("com.ibm.db2.jcc.DB2Driver", "SELECT 1 FROM SYSIBM.SYSDUMMY1", new String[]{"db2"}));
        databases.add(new JdbcDatabase("com.ibm.as400.access.AS400JDBCDriver", "SELECT 1 FROM SYSIBM.SYSDUMMY1", new String[]{"as400"}));
        databases.add(new JdbcDatabase("com.teradata.jdbc.TeraDriver", "teradata"));
        databases.add(new JdbcDatabase("com.informix.jdbc.IfxDriver", "select count(*) from systables", new String[]{"informix"}));
    }

    /**
     * Searches defined database where the URL prefix matches one of the prefixes defined in a {@link JdbcDatabase}.
     * The prefix is determined by:
     * <p>
     * jdbc:<prefix>:...
     *
     * @param jdbcUrl The connection URL
     * @return An optional {@link JdbcDatabase}
     */
    @SuppressWarnings("MagicNumber")
    public static Optional<JdbcDatabase> findDatabase(String jdbcUrl) {
        if (StringUtils.isNotEmpty(jdbcUrl)) {
            if (!jdbcUrl.startsWith("jdbc")) {
                throw new IllegalArgumentException("Invalid JDBC URL [" + jdbcUrl + "]. JDBC URLs must start with 'jdbc'.");
            }
            String partialUrl = jdbcUrl.substring(5);
            String prefix = partialUrl.substring(0, partialUrl.indexOf(':')).toLowerCase();

            return databases.stream().filter(db -> db.containsPrefix(prefix)).findFirst();
        }
        return Optional.empty();
    }

    /**
     * Searches the provided classloader for an embedded database driver.
     *
     * @param classLoader The classloader to search
     * @return An optional {@link EmbeddedJdbcDatabase}
     */
    public static Optional<EmbeddedJdbcDatabase> get(ClassLoader classLoader) {
        return databases
            .stream()
            .filter(JdbcDatabase::isEmbedded)
            .map(EmbeddedJdbcDatabase.class::cast)
            .filter(embeddedDatabase -> ClassUtils.isPresent(embeddedDatabase.getDriverClassName(), classLoader))
            .findFirst();
    }

    /**
     * Searches embedded databases where the driver matches the argument.
     *
     * @param driverClassName The driver class name to search on
     * @return True if the driver matches an embedded database type
     */
    public static boolean isEmbedded(String driverClassName) {
        return databases
            .stream()
            .filter(JdbcDatabase::isEmbedded)
            .anyMatch(db -> db.driverClassName.equals(driverClassName));
    }

    /**
     * Provides the required information in order to connect toa JDBC database, including the necessary driver and
     * validation query.
     */
    public static class JdbcDatabase {

        private String driverClassName;
        private String validationQuery;
        private Collection<String> urlPrefixes;

        /**
         * @param driverClassName The jdbc driver class name
         * @param validationQuery The validation query
         * @param urlPrefixes     The url prefixes
         */
        JdbcDatabase(String driverClassName, String validationQuery, String[] urlPrefixes) {
            this.driverClassName = driverClassName;
            this.urlPrefixes = Arrays.asList(urlPrefixes);
            this.validationQuery = validationQuery;
        }

        /**
         * @param driverClassName The jdbc driver class name
         * @param urlPrefixes     The url prefixes
         */
        JdbcDatabase(String driverClassName, String[] urlPrefixes) {
            this(driverClassName, "SELECT 1", urlPrefixes);
        }

        /**
         * @param driverClassName The jdbc driver class name
         * @param urlPrefix       The url prefix
         */
        JdbcDatabase(String driverClassName, String urlPrefix) {
            this(driverClassName, "SELECT 1", new String[]{urlPrefix});
        }

        /**
         * @return Whether the database is embedded
         */
        protected boolean isEmbedded() {
            return Boolean.FALSE;
        }

        /**
         * @return The name of the driver class
         */

        public String getDriverClassName() {
            return driverClassName;
        }

        /**
         * @return The query to run to the validate the connection
         */
        public String getValidationQuery() {
            return validationQuery;
        }

        /**
         * @param prefix The prefix to check
         * @return Whether the url prefixes contain the prefix
         */
        public boolean containsPrefix(String prefix) {
            return urlPrefixes.contains(prefix);
        }
    }

    /**
     * Extends {@link JdbcDatabase} with additional defaults for the use of embedded databases such as H2.
     */
    public static class EmbeddedJdbcDatabase extends JdbcDatabase {

        private String defaultUrl;
        private String defaultName = "devDb";

        /**
         * @param driverClassName The jdbc driver class name
         * @param validationQuery The validation query
         * @param urlPrefixes     The url prefixes
         * @param defaultUrl      The default url
         */
        EmbeddedJdbcDatabase(String driverClassName, String validationQuery, String[] urlPrefixes, String defaultUrl) {
            super(driverClassName, validationQuery, urlPrefixes);
            this.defaultUrl = defaultUrl;
        }

        /**
         * @param driverClassName The jdbc driver class name
         * @param urlPrefixes     The url prefixes
         * @param defaultUrl      The default url
         */
        EmbeddedJdbcDatabase(String driverClassName, String[] urlPrefixes, String defaultUrl) {
            super(driverClassName, urlPrefixes);
            this.defaultUrl = defaultUrl;
        }

        /**
         * @param driverClassName The jdbc driver class name
         * @param urlPrefix       The url prefix
         * @param defaultUrl      The default url
         */
        EmbeddedJdbcDatabase(String driverClassName, String urlPrefix, String defaultUrl) {
            super(driverClassName, urlPrefix);
            this.defaultUrl = defaultUrl;
        }

        /**
         * Obtain an embedded database URL for the given database name.
         *
         * @param databaseName The database name
         * @return The URL
         */
        public String getUrl(String databaseName) {
            if (databaseName == null) {
                databaseName = defaultName;
            }
            return String.format(this.defaultUrl, databaseName);
        }

        @Override
        protected boolean isEmbedded() {
            return Boolean.TRUE;
        }
    }
}
