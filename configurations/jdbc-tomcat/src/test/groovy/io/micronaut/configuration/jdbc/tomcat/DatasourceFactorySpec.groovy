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

import io.micronaut.spring.tx.datasource.SpringDataSourceResolver
import org.apache.tomcat.jdbc.pool.DataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import spock.lang.Specification

class DatasourceFactorySpec extends Specification {

    DatasourceFactory datasourceFactory

    def setup() {
        datasourceFactory = new DatasourceFactory(new SpringDataSourceResolver())
    }

    def "create basic datasource"() {
        given:
        def dataSource = new DataSource(validationQuery: "SELECT 1")

        when:
        def metadata = datasourceFactory.tomcatPoolDataSourceMetadataProvider(dataSource)

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
        def dataSource = new DataSource(validationQuery: "SELECT 1")
        def transactionalDataSource = new TransactionAwareDataSourceProxy(targetDataSource: dataSource)

        when:
        def metadata = datasourceFactory.tomcatPoolDataSourceMetadataProvider(transactionalDataSource)

        then:
        metadata
        metadata.idle >= 0
        metadata.max >= 0
        metadata.active >= 0
        metadata.validationQuery == "SELECT 1"
        metadata.usage >= 0
    }
}
