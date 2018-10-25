package io.micronaut.docs.dbmigration.liquibase

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.dbmigration.liquibase.LiquibaseConfigurationProperties
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.testutils.YamlAsciidocTagCleaner
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.sql.DataSource

class LiquibaseSpec  extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
datasources:
    default: # <2>
        url: 'jdbc:h2:mem:liquibaseDisabledDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        username: 'sa'
        password: ''
        driverClassName: 'org.h2.Driver'
jpa:
    default:
        packages-to-scan:
            - 'example.micronaut'
        properties:
            hibernate:
                hbm2ddl:
                    auto: none # <1>
                show_sql: true
liquibase:
    default: # <2>
        change-log: 'classpath:db/liquibase-changelog.xml' # <3> 
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> liquibaseMap = [
            jpa: [
                    default: [
                            'packages-to-scan' : ['example.micronaut'],
                            properties: [
                                    hibernate: [
                                        hbm2ddl: [
                                                auto: 'none'
                                        ],
                                        'show_sql' : true,
                                    ]
                            ]

                    ]
            ],
            liquibase: [
                    default: [
                            'change-log': 'classpath:db/liquibase-changelog.xml'
                    ]
            ],
            datasources: [
                    default: [
                            url: 'jdbc:h2:mem:liquibaseDisabledDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
                            username: 'sa',
                            password: '',
                            driverClassName: 'org.h2.Driver',
                    ]
            ]
    ]

    @Shared
    Map<String, Object> config = [:] << flatten(liquibaseMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, Environment.TEST)

    void "test liquibase changelog are run"() {

        when:
        embeddedServer.applicationContext.getBean(DataSource)

        then:
        noExceptionThrown()

        when:
        LiquibaseConfigurationProperties config = embeddedServer.applicationContext.getBean(LiquibaseConfigurationProperties, Qualifiers.byName('default'))

        then:
        noExceptionThrown()
        config.getChangeLog() == 'classpath:db/liquibase-changelog.xml'

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))

        then:
        m == liquibaseMap

        when:
        Map db = [url:'jdbc:h2:mem:liquibaseDisabledDb', user:'sa', password:'', driver:'org.h2.Driver']
        Sql sql = Sql.newInstance(db.url, db.user, db.password, db.driver)

        then:
        sql.rows('select count(*) from books').get(0)[0] == 2
    }
}
