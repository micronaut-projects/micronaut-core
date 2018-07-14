package io.micronaut.configuration.jdbc.tomcat

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class DatasourceTransactionManagementSpec extends Specification {

    def "test datasource transaction management"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'datasources.default.defaultAutoCommit': false,
                'datasources.default.enableAutoCommitOnReturn': false,
                'datasources.secondary.defaultAutoCommit': false,
                'datasources.secondary.enableAutoCommitOnReturn': false
        )

        BookService bookService = ctx.getBean(BookService)

        expect:
        bookService.save("one") == "1"
        bookService.save("two") == "2"

        bookService.saveTwo("one") == "1"
        bookService.saveTwo("two") == "2"

        bookService.save("three") == "3"

        cleanup:
        ctx.close()

    }




}
