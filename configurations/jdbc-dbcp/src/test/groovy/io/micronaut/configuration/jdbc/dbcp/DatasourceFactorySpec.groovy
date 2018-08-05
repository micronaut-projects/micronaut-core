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

package io.micronaut.configuration.jdbc.dbcp

import org.apache.commons.dbcp2.BasicDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import spock.lang.Specification

class DatasourceFactorySpec extends Specification {

    DatasourceFactory datasourceFactory

    def setup() {
        datasourceFactory = new DatasourceFactory()
    }

    def "create basic datasource"() {
        given:
        def dataSource = new BasicDataSource(validationQuery: "SELECT 1")

        when:
        def metadata = datasourceFactory.dbcpDataSourcePoolMetadata("test", dataSource)

        then:
        metadata
        metadata.idle >= 0
        metadata.max >= 0
        metadata.active >= 0
        metadata.getValidationQuery()
        metadata.usage >= 0
    }

    def "create transactional datasource"() {
        given:
        def transactionalDataSource = Mock(TransactionAwareDataSourceProxy)
        def dataSource = new BasicDataSource(validationQuery: "SELECT 1")

        when:
        def metadata = datasourceFactory.dbcpDataSourcePoolMetadata("test", transactionalDataSource)

        then:
        2 * transactionalDataSource.targetDataSource >> dataSource
        metadata
        metadata.idle >= 0
        metadata.max >= 0
        metadata.active >= 0
        metadata.validationQuery == "SELECT 1"
        metadata.usage >= 0
    }
}
