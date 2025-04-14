/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.ctx

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import javax.sql.DataSource

class DatasourceConfigurationSpec extends Specification {

    void "test datasource can be disabled and enabled"() {
        given:
            ApplicationContext applicationContext = new DefaultApplicationContext("test")
            applicationContext.environment.addPropertySource(MapPropertySource.of(
                    'test',
                    [
                            'spec.name': getClass().getSimpleName(),
                            'datasources.default'        : [:],
                            'datasources.default.enabled': false,
                            'datasources.custom'         : [:],
                    ]
            ))
            applicationContext.start()

        when:
            applicationContext.getBean(DataSource, Qualifiers.byName('default'))
        then:
            thrown(NoSuchBeanException)
        when:
            applicationContext.getBean(DataSource, Qualifiers.byName('default'))
        then:
            thrown(NoSuchBeanException)

        when:
            DataSource customDataSource = applicationContext.getBean(DataSource, Qualifiers.byName('custom'))
        then:
            noExceptionThrown()
            customDataSource
    }

    void "test datasource can be disabled and enabled REVERSED"() {
        given:
            ApplicationContext applicationContext = new DefaultApplicationContext("test")
            applicationContext.environment.addPropertySource(MapPropertySource.of(
                    'test',
                    [
                            'spec.name': getClass().getSimpleName(),
                            'datasources.default'        : [:],
                            'datasources.default.enabled': false,
                            'datasources.custom'         : [:],
                    ]
            ))
            applicationContext.start()

        when:
            DataSource customDataSource = applicationContext.getBean(DataSource, Qualifiers.byName('custom'))
        then:
            noExceptionThrown()
            customDataSource

        when:
            applicationContext.getBean(DataSource, Qualifiers.byName('default'))
        then:
            thrown(NoSuchBeanException)
        when:
            applicationContext.getBean(DataSource, Qualifiers.byName('default'))
        then:
            thrown(NoSuchBeanException)
    }

}
