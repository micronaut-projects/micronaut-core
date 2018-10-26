package io.micronaut.dbmigration.liquibase.management.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.dbmigration.liquibase.LiquibaseConfigurationProperties;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.reactivex.Single;
import liquibase.changelog.StandardChangeLogHistoryService;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Provides a liquibase endpoint to get all the migrations applied.
 *
 * @author Iván López
 * @since 1.1
 */
@Endpoint(id = LiquibaseEndpoint.NAME)
public class LiquibaseEndpoint {

    /**
     * Endpoint name.
     */
    public static final String NAME = "liquibase";

    private final Collection<LiquibaseConfigurationProperties> liquibaseConfigurationProperties;

    /**
     * Constructor.
     *
     * @param liquibaseConfigurationProperties Collection of Liquibase Configurations
     */
    public LiquibaseEndpoint(Collection<LiquibaseConfigurationProperties> liquibaseConfigurationProperties) {
        this.liquibaseConfigurationProperties = liquibaseConfigurationProperties;
    }

    @Read
    public Single<List<LiquibaseReport>> liquibaseMigrations() {
        List<LiquibaseReport> reports = new ArrayList<LiquibaseReport>();

        if (liquibaseConfigurationProperties != null) {

            DatabaseFactory factory = DatabaseFactory.getInstance();
            StandardChangeLogHistoryService service = new StandardChangeLogHistoryService();

            for (LiquibaseConfigurationProperties conf : liquibaseConfigurationProperties) {
                DataSource dataSource = conf.getDataSource();

                try {
                    JdbcConnection connection = new JdbcConnection(dataSource.getConnection());

                    try {
                        Database database = factory.findCorrectDatabaseImplementation(connection);
                        reports.add(
                                new LiquibaseReport(conf.getNameQualifier(),
                                service.queryDatabaseChangeLogTable(database))
                        );

                    } finally {
                        connection.close();
                    }

                } catch (Exception ex) {
                    throw new IllegalStateException("Unable to get Liquibase changelog", ex);
                }
            }
        }

        return Single.just(reports);
    }


    /**
     * Liquibase report for one datasource.
     */
    public static class LiquibaseReport {

        private final String name;

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private final List<Map<String, ?>> changeLogs;

        public LiquibaseReport(String name, List<Map<String, ?>> changeLogs) {
            this.name = name;
            this.changeLogs = changeLogs;
        }

        public String getName() {
            return this.name;
        }

        public List<Map<String, ?>> getChangeLogs() {
            return this.changeLogs;
        }

    }
}
