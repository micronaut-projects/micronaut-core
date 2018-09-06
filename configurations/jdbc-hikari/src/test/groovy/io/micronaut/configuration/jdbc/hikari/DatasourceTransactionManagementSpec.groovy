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
package io.micronaut.configuration.jdbc.hikari

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
