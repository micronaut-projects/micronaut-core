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

package io.micronaut.configuration.postgres.reactive

import io.micronaut.configuration.postgres.reactive.health.PgPoolHealthIndicator
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

/**
 * @author puneetbehl
 * @since 1.0
 */
class PgPoolConfigurationSpec extends Specification {

    void "test reactive-pg-client configuration"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                'reactive.pg.client.port': '5432',
                'reactive.pg.client.host': 'the-host',
                'reactive.pg.client.database': 'the-db',
                'reactive.pg.client.user': 'user',
                'reactive.pg.client.password': 'secret',
                'reactive.pg.client.maxSize': '5'
        )
        PgPoolHealthIndicator indicator = applicationContext.getBean(PgPoolHealthIndicator)

        then:
        applicationContext.containsBean(PgPoolConfiguration)
        applicationContext.getBean(PgPoolConfiguration).pgPoolOptions
        applicationContext.getBean(PgPoolConfiguration).pgPoolOptions.port == 5432
        applicationContext.getBean(PgPoolConfiguration).pgPoolOptions.host == 'the-host'
        applicationContext.getBean(PgPoolConfiguration).pgPoolOptions.database == 'the-db'
        applicationContext.getBean(PgPoolConfiguration).pgPoolOptions.user == 'user'
        applicationContext.getBean(PgPoolConfiguration).pgPoolOptions.password == 'secret'
        applicationContext.getBean(PgPoolConfiguration).pgPoolOptions.maxSize == 5


        cleanup:
        applicationContext?.stop()
    }



}
