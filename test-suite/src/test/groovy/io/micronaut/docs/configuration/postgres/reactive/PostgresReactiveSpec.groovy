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

package io.micronaut.docs.configuration.postgres.reactive
//tag::appcontext-import[]
import io.micronaut.context.ApplicationContext
//end::appcontext-import[]
import io.reactiverse.reactivex.pgclient.PgPool
import io.reactivex.Single
//tag::pg-testcontainer-import[]
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

//end::pg-testcontainer-import[]
import spock.lang.Specification

/**
 * @author puneetbehl
 * @since 1.0
 */
class PostgresReactiveSpec extends Specification {

    // tag::pg-testcontainer[]
    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer()

    // end::pg-testcontainer[]

    //tag::pg-dbstats[]
    void "test a simple query for database stats"() {
        given:
        //tag::pg-client-conf[]
        postgres.start()

        ApplicationContext applicationContext = ApplicationContext.run(
                'postgres.reactive.client.port': postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                'postgres.reactive.client.host': postgres.getContainerIpAddress(),
                'postgres.reactive.client.database': postgres.databaseName,
                'postgres.reactive.client.user': postgres.username,
                'postgres.reactive.client.password': postgres.password,
                'postgres.reactive.client.maxSize': '5'
        )

        //end::pg-client-conf[]
        String result

        when:

        // tag::pgPool-bean[]
        PgPool client = applicationContext.getBean(PgPool)
        // end::pgPool-bean[]

        // tag::query[]
        result = Single.create({emitter->
            client.query('SELECT * FROM pg_stat_database', {ar-> // <1>
                if (ar.succeeded()) {
                    emitter.onSuccess("Size: " + ar.result().size())
                } else {
                    emitter.onSuccess(ar.cause().getMessage())
                }
            })
        }).toFlowable().blockingFirst()
        // end::query[]

        then:
        result == "Size: 4"

        cleanup:
        client.close()
        postgres.stop()
    }
    //end::pg-dbstats[]

}
