package io.micronaut.dbmigration.liquibase.management.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.dbmigration.liquibase.LiquibaseConfigurationProperties;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.reactivex.Single;
import liquibase.changelog.ChangeSet.ExecType;
import liquibase.changelog.RanChangeSet;
import liquibase.changelog.StandardChangeLogHistoryService;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides a liquibase endpoint to get all the migrations applied.
 *
 * @author Iván López
 * @see <a href="https://github.com/spring-projects/spring-boot/blob/v2.0.6.RELEASE/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/liquibase/LiquibaseEndpoint.java">LiquibaseEndpoint</a>
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

    /**
     * @return A list of liquibase changes per active configuration
     */
    @Read
    public Single<List<LiquibaseReport>> liquibaseMigrations() {
        List<LiquibaseReport> reports = new ArrayList<>();

        if (liquibaseConfigurationProperties != null) {
            for (LiquibaseConfigurationProperties conf : liquibaseConfigurationProperties) {
                if (conf.isEnabled()) {
                    DatabaseFactory factory = DatabaseFactory.getInstance();
                    StandardChangeLogHistoryService service = new StandardChangeLogHistoryService();
                    DataSource dataSource = conf.getDataSource();

                    try {
                        JdbcConnection connection = new JdbcConnection(dataSource.getConnection());

                        try {
                            Database database = factory.findCorrectDatabaseImplementation(connection);
                            service.setDatabase(database);
                            reports.add(
                                    new LiquibaseReport(
                                            conf.getNameQualifier(),
                                            service.getRanChangeSets()
                                                    .stream()
                                                    .map(ChangeSet::new)
                                                    .collect(Collectors.toList())
                                    )
                            );
                        } finally {
                            connection.close();
                        }

                    } catch (SQLException | DatabaseException ex) {
                        throw new IllegalStateException("Unable to get Liquibase changelog", ex);
                    }
                }
            }
        }

        return Single.just(reports);
    }

    /**
     * A Liquibase change set.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class ChangeSet {

        private final String author;

        private final String changeLog;

        private final String comments;

        private final Set<String> contexts;

        private final Instant dateExecuted;

        private final String deploymentId;

        private final String description;

        private final ExecType execType;

        private final String id;

        private final Set<String> labels;

        private final String checksum;

        private final Integer orderExecuted;

        private final String tag;

        /**
         * @param ranChangeSet The liquibase changeset
         */
        public ChangeSet(RanChangeSet ranChangeSet) {
            this.author = ranChangeSet.getAuthor();
            this.changeLog = ranChangeSet.getChangeLog();
            this.comments = ranChangeSet.getComments();
            this.contexts = ranChangeSet.getContextExpression().getContexts();
            this.dateExecuted = Instant.ofEpochMilli(ranChangeSet.getDateExecuted().getTime());
            this.deploymentId = ranChangeSet.getDeploymentId();
            this.description = ranChangeSet.getDescription();
            this.execType = ranChangeSet.getExecType();
            this.id = ranChangeSet.getId();
            this.labels = ranChangeSet.getLabels().getLabels();
            this.checksum = ((ranChangeSet.getLastCheckSum() != null) ? ranChangeSet.getLastCheckSum().toString() : null);
            this.orderExecuted = ranChangeSet.getOrderExecuted();
            this.tag = ranChangeSet.getTag();
        }

        /**
         * @return The author of the change
         */
        public String getAuthor() {
            return this.author;
        }

        /**
         * @return The changelog
         */
        public String getChangeLog() {
            return this.changeLog;
        }

        /**
         * @return The comments
         */
        public String getComments() {
            return this.comments;
        }

        /**
         * @return The contexts
         */
        public Set<String> getContexts() {
            return this.contexts;
        }

        /**
         * @return The execution date for the changeset
         */
        public Instant getDateExecuted() {
            return this.dateExecuted;
        }

        /**
         * @return The deployment id
         */
        public String getDeploymentId() {
            return this.deploymentId;
        }

        /**
         * @return The descriptions
         */
        public String getDescription() {
            return this.description;
        }

        /**
         * @return The {@link ExecType}
         */
        public ExecType getExecType() {
            return this.execType;
        }

        /**
         * @return The changeset id
         */
        public String getId() {
            return this.id;
        }

        /**
         * @return The labels
         */
        public Set<String> getLabels() {
            return this.labels;
        }

        /**
         * @return The checksum
         */
        public String getChecksum() {
            return this.checksum;
        }

        /**
         * @return The order in which the migration has been executed
         */
        public Integer getOrderExecuted() {
            return this.orderExecuted;
        }

        /**
         * @return The tag
         */
        public String getTag() {
            return this.tag;
        }
    }

    /**
     * Liquibase report for one datasource.
     */
    public static class LiquibaseReport {

        private final String name;

        private final List<ChangeSet> changeSets;

        /**
         * @param name       The name of the data source
         * @param changeSets The list of changes
         */
        public LiquibaseReport(String name, List<ChangeSet> changeSets) {
            this.name = name;
            this.changeSets = changeSets;
        }

        /**
         * @return The name of the data source
         */
        public String getName() {
            return this.name;
        }

        /**
         * @return The list of changesets
         */
        public List<ChangeSet> getChangeSets() {
            return changeSets;
        }
    }
}
