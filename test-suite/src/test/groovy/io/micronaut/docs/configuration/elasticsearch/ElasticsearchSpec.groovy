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

package io.micronaut.docs.configuration.elasticsearch

import io.micronaut.context.ApplicationContext
import org.elasticsearch.Version
import org.elasticsearch.action.main.MainResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.testcontainers.elasticsearch.ElasticsearchContainer
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author puneetbehl
 * @since 1.0
 */
class ElasticsearchSpec extends Specification {

    // tag::es-testcontainer[]
    @Shared ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:6.4.1")

    // end::es-testcontainer[]


    //tag::es-stats[]
    void "Test simple info for Elasticsearch stats using the High Level REST Client"() {
        given:
        //tag::es-conf[]
        elasticsearch.start()
        ApplicationContext applicationContext = ApplicationContext.run("elasticsearch.httpHosts": "http://${elasticsearch.getHttpHostAddress()}", "test")
        //end::es-conf
        String stats

        when:
        //tag::es-bean[]
        RestHighLevelClient client = applicationContext.getBean(RestHighLevelClient)
        //end::es-bean[]
        //tag::query[]
        MainResponse response =
                client.info(RequestOptions.DEFAULT) // <1>
        //end::query[]

        then:
        "docker-cluster" == response.getClusterName().value()
        Version.fromString("6.4.1") == response.getVersion()

        cleanup:
        applicationContext.close()
        elasticsearch.stop()
    }
    //end::es-dbstats[]
}
