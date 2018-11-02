package io.micronaut.docs.dbmigration.liquibase

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.dbmigration.liquibase.LiquibaseConfigurationProperties
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class LiquibaseConfigurationPropertiesEnabledSpec extends Specification {

    void 'if no Liquibase configuration then LiquibaseConfigurationProperties bean is created'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name': LiquibaseConfigurationPropertiesEnabledSpec.simpleName] as Map,
            Environment.TEST
        )

        when:
        applicationContext.getBean(LiquibaseConfigurationProperties)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type [' + LiquibaseConfigurationProperties.name + '] exists.')

        cleanup:
        applicationContext.close()
    }

    void 'if Liquibase configuration then LiquibaseConfigurationProperties is created'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
            ['spec.name'                         : LiquibaseConfigurationPropertiesEnabledSpec.simpleName,
             'liquibase.movies.enabled'          : true,
             'liquibase.movies.change-log'       : 'classpath:db/liquibase-changelog.xml',
             'datasources.movies.url'            : 'jdbc:h2:mem:liquibaseMoviesDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.movies.username'       : 'sa',
             'datasources.movies.password'       : '',
             'datasources.movies.driverClassName': 'org.h2.Driver',
             'liquibase.books.enabled'           : true,
             'liquibase.books.change-log'        : 'classpath:db/liquibase-changelog.xml',
             'datasources.books.url'             : 'jdbc:h2:mem:liquibaseBooksDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
             'datasources.books.username'        : 'sa',
             'datasources.books.password'        : '',
             'datasources.books.driverClassName' : 'org.h2.Driver',
            ] as Map
            , Environment.TEST
        )
        when:
        applicationContext.getBean(LiquibaseConfigurationProperties, Qualifiers.byName('movies'))

        then:
        noExceptionThrown()

        when:
        applicationContext.getBean(LiquibaseConfigurationProperties, Qualifiers.byName('books'))

        then:
        noExceptionThrown()

        cleanup:
        applicationContext.close()
    }
}
