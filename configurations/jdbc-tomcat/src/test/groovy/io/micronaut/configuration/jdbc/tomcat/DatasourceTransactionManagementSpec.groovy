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

package io.micronaut.configuration.jdbc.tomcat

import io.micronaut.configuration.jdbc.tomcat.metadata.TomcatDataSourcePoolMetadata
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class DatasourceTransactionManagementSpec extends Specification {

    def "test datasource transaction management"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'datasources.default': ['defaultAutoCommit': false, 'enableAutoCommitOnReturn': false],
                'datasources.secondary': ['defaultAutoCommit': false, 'enableAutoCommitOnReturn': false]
        )
        TomcatDataSourcePoolMetadata poolMetadataDefault = ctx.getBean(TomcatDataSourcePoolMetadata, Qualifiers.byName("default"))
        TomcatDataSourcePoolMetadata poolMetadataSecondary = ctx.getBean(TomcatDataSourcePoolMetadata, Qualifiers.byName("secondary"))
        BookService bookService = ctx.getBean(BookService)

        expect:
        poolMetadataDefault.borrowed == 1
        poolMetadataSecondary.borrowed == 1

        and:
        bookService.save("one") == "1"
        bookService.save("two") == "2"
        bookService.saveTwo("one") == "1"
        bookService.saveTwo("two") == "2"
        bookService.save("three") == "3"

        and:
        poolMetadataDefault.borrowed > 1
        poolMetadataSecondary.borrowed > 1

        cleanup:
        ctx.close()
    }
}
