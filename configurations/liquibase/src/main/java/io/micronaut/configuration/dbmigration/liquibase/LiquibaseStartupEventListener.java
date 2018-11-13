/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.dbmigration.liquibase;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.OfflineConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

/**
 * Listener for  {@link io.micronaut.context.event.StartupEvent} to run liquibase operations.
 *
 * @author Sergio del Amo
 * @since 1.1
 */
@Singleton
class LiquibaseStartupEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseStartupEventListener.class);

    private final ResourceAccessor resourceAccessor;
    private final Collection<LiquibaseConfigurationProperties> liquibaseConfigurationProperties;

    /**
     * @param resourceAccessor                 An implementation of {@link liquibase.resource.ResourceAccessor}.
     * @param liquibaseConfigurationProperties Collection of Liquibase Configurations
     */
    public LiquibaseStartupEventListener(ResourceAccessor resourceAccessor, Collection<LiquibaseConfigurationProperties> liquibaseConfigurationProperties) {
        this.resourceAccessor = resourceAccessor;
        this.liquibaseConfigurationProperties = liquibaseConfigurationProperties;
    }

    /**
     * Runs Liquibase for the datasource where there is a liquibase configuration available.
     *
     * @param event Server startup event
     */
    @EventListener
    public void onStartup(StartupEvent event) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Executing synchronous liquibase migrations");
        }
        run(false);
    }

    /**
     * Runs Liquibase asynchronously for the datasource where there is a liquibase configuration available.
     *
     * @param event Server startup event
     */
    @Async
    @EventListener
    public void onStartupAsync(StartupEvent event) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Executing asynchronous liquibase migrations");
        }
        run(true);
    }

    /**
     * Runs Liquibase for the datasource where there is a liquibase configuration available.
     *
     * @param async if true only liquibase configurations set to async are run.
     */
    public void run(boolean async) {
        liquibaseConfigurationProperties
                .stream()
                .filter(c -> c.getDataSource() != null)
                .filter(c -> c.isEnabled())
                .filter(c -> c.isAsync() == async)
                .forEach(this::migrate);
    }

    /**
     * Performs liquibase update for the given data datasource and configuration.
     *
     * @param config Liquibase configuration
     */
    protected void migrate(LiquibaseConfigurationProperties config) {
        Connection connection;
        DataSource dataSource = config.getDataSource();
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Migration failed! Could not connect to the datasource.", e);
            }
            return;
        }

        Liquibase liquibase = null;
        try {
            liquibase = createLiquibase(connection, config);
            generateRollbackFile(liquibase, config);
            performUpdate(liquibase, config);
        } catch (LiquibaseException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Migration failed! Liquibase encountered an exception.", e);
            }
        } finally {
            Database database = null;
            if (liquibase != null) {
                database = liquibase.getDatabase();
            }
            if (database != null) {
                try {
                    database.close();
                } catch (DatabaseException e) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Error closing the connection after the migration.", e);
                    }
                }
            }
        }
    }

    /**
     * Performs Liquibase update.
     *
     * @param liquibase Primary facade class for interacting with Liquibase.
     * @param config      Liquibase configuration
     * @throws LiquibaseException Liquibase exception.
     */
    protected void performUpdate(Liquibase liquibase, LiquibaseConfigurationProperties config) throws LiquibaseException {
        LabelExpression labelExpression = new LabelExpression(config.getLabels());
        Contexts contexts = new Contexts(config.getContexts());
        if (config.isTestRollbackOnUpdate()) {
            if (config.getTag() != null) {
                liquibase.updateTestingRollback(config.getTag(), contexts, labelExpression);
            } else {
                liquibase.updateTestingRollback(contexts, labelExpression);
            }
        } else {
            if (config.getTag() != null) {
                liquibase.update(config.getTag(), contexts, labelExpression);
            } else {
                liquibase.update(contexts, labelExpression);
            }
        }
    }

    /**
     * Generates Rollback file.
     *
     * @param liquibase Primary facade class for interacting with Liquibase.
     * @param config      Liquibase configuration
     * @throws LiquibaseException Liquibase exception.
     */
    protected void generateRollbackFile(Liquibase liquibase, LiquibaseConfigurationProperties config) throws LiquibaseException {
        if (config.getRollbackFile() != null) {
            String outputEncoding = LiquibaseConfiguration.getInstance().getConfiguration(GlobalConfiguration.class).getOutputEncoding();
            try (FileOutputStream fileOutputStream = new FileOutputStream(config.getRollbackFile());
                 Writer output = new OutputStreamWriter(fileOutputStream, outputEncoding)) {
                Contexts contexts = new Contexts(config.getContexts());
                LabelExpression labelExpression = new LabelExpression(config.getLabels());
                if (config.getTag() != null) {
                    liquibase.futureRollbackSQL(config.getTag(), contexts, labelExpression, output);
                } else {
                    liquibase.futureRollbackSQL(contexts, labelExpression, output);
                }
            } catch (IOException e) {
                throw new LiquibaseException("Unable to generate rollback file.", e);
            }
        }
    }

    /**
     * @param connection Connection with the data source
     * @param config       Liquibase Configuration for the Data source
     * @return A Liquibase object
     * @throws LiquibaseException A liquibase exception.
     */
    protected Liquibase createLiquibase(Connection connection, LiquibaseConfigurationProperties config) throws LiquibaseException {
        String changeLog = config.getChangeLog();
        Database database = createDatabase(connection, resourceAccessor, config);
        Liquibase liquibase = new Liquibase(changeLog, resourceAccessor, database);
        liquibase.setIgnoreClasspathPrefix(config.isIgnoreClasspathPrefix());
        if (config.getParameters() != null) {
            for (Map.Entry<String, String> entry : config.getParameters().entrySet()) {
                liquibase.setChangeLogParameter(entry.getKey(), entry.getValue());
            }
        }

        if (config.isDropFirst()) {
            liquibase.dropAll();
        }

        return liquibase;
    }

    /**
     * Subclasses may override this method add change some database settings such as
     * default schema before returning the database object.
     *
     * @param connection       Connection with the data source
     * @param resourceAccessor Abstraction of file access
     * @param config             Liquibase Configuration for the Data source
     * @return a Database implementation retrieved from the {@link DatabaseFactory}.
     * @throws DatabaseException A Liquibase Database exception.
     */
    protected Database createDatabase(Connection connection,
                                      ResourceAccessor resourceAccessor,
                                      LiquibaseConfigurationProperties config) throws DatabaseException {

        DatabaseConnection liquibaseConnection;
        if (connection == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Null connection returned by liquibase datasource. Using offline unknown database");
            }
            liquibaseConnection = new OfflineConnection("offline:unknown", resourceAccessor);

        } else {
            liquibaseConnection = new JdbcConnection(connection);
        }

        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(liquibaseConnection);
        String defaultSchema = config.getDefaultSchema();
        if (StringUtils.isNotEmpty(defaultSchema)) {
            if (database.supportsSchemas()) {
                database.setDefaultSchemaName(defaultSchema);
            } else if (database.supportsCatalogs()) {
                database.setDefaultCatalogName(defaultSchema);
            }
        }
        String liquibaseSchema = config.getLiquibaseSchema();
        if (StringUtils.isNotEmpty(liquibaseSchema)) {
            if (database.supportsSchemas()) {
                database.setLiquibaseSchemaName(liquibaseSchema);
            } else if (database.supportsCatalogs()) {
                database.setLiquibaseCatalogName(liquibaseSchema);
            }
        }
        if (StringUtils.trimToNull(config.getLiquibaseTablespace()) != null && database.supportsTablespaces()) {
            database.setLiquibaseTablespaceName(config.getLiquibaseTablespace());
        }
        if (StringUtils.trimToNull(config.getDatabaseChangeLogTable()) != null) {
            database.setDatabaseChangeLogTableName(config.getDatabaseChangeLogTable());
        }
        if (StringUtils.trimToNull(config.getDatabaseChangeLogLockTable()) != null) {
            database.setDatabaseChangeLogLockTableName(config.getDatabaseChangeLogLockTable());
        }
        return database;
    }
}
