package io.micronaut.docs.dbmigration.liquibase

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.dbmigration.liquibase.LiquibaseConfigurationProperties
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.testutils.YamlAsciidocTagCleaner
import org.h2.jdbc.JdbcSQLException
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class LiquibaseDisabledSpec extends Specification implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
datasources:
    default:
        url: 'jdbc:h2:mem:liquibaseDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
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
                    auto: none
                show_sql: true
liquibase:
    enabled: false
    default:
        change-log: 'classpath:db/liquibase-changelog.xml'
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
                    enabled: false,
                    default: [
                            'change-log': 'classpath:db/liquibase-changelog.xml'
                    ]
            ],
            datasources: [
                    default: [
                            url: 'jdbc:h2:mem:liquibaseDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
                            username: 'sa',
                            password: '',
                            driverClassName: 'org.h2.Driver',
                    ]
            ]
    ]

    @Shared
    Map<String, Object> config = [
            'datasources.books.url': 'jdbc:h2:mem:booksDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
            'datasources.books.username': 'sa',
            'datasources.books.password': '',
            'datasources.books.driverClassName': 'org.h2.Driver'
    ] << flatten(liquibaseMap)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, Environment.TEST)

    void "if liquibase.enabled=false changelog are not run"() {

        when:
        embeddedServer.applicationContext.getBean(DataSource)

        then:
        noExceptionThrown()
        
        when:
        LiquibaseConfigurationProperties config = embeddedServer.applicationContext.getBean(LiquibaseConfigurationProperties, Qualifiers.byName('default'))

        then:
        noExceptionThrown()
        config.getChangeLog() == 'classpath:db/liquibase-changelog.xml'
        config.isEnabled()

        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))

        then:
        m == liquibaseMap

        when:
        Map db = [url:'jdbc:h2:mem:liquibaseDb', user:'sa', password:'', driver:'org.h2.Driver']
        Sql sql = Sql.newInstance(db.url, db.user, db.password, db.driver)
        List<GroovyRowResult> rowResultList = sql.rows 'select count(*) from books'

        then:
        rowResultList == null
        def e = thrown(JdbcSQLException)
        e.message.contains('Table "BOOKS" not found')
    }
}
