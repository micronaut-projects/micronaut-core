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

package io.micronaut.configuration.elasticsearch

import io.micronaut.context.ApplicationContext
import org.elasticsearch.action.main.MainResponse
import org.elasticsearch.client.RestHighLevelClient
import spock.lang.Specification

/**
 * @author lishuai
 * @since 1.0.1
 */
class ElasticsearchConfigurationSpec extends Specification {

    void "test es configuration"() {

        given:
        ApplicationContext applicationContext = ApplicationContext.run('elasticsearch.uris':'http://localhost:9200')
        ApplicationContext applicationContext2 = ApplicationContext.run('elasticsearch.uris':'http://localhost:9200,http://127.0.0.1:9200')

        expect:
        applicationContext.containsBean(DefaultElasticsearchConfiguration)
        applicationContext.containsBean(RestHighLevelClient)
        applicationContext.getBean(DefaultElasticsearchConfiguration).uris.size() == 1

        applicationContext2.containsBean(DefaultElasticsearchConfiguration)
        applicationContext2.containsBean(RestHighLevelClient)
        applicationContext2.getBean(DefaultElasticsearchConfiguration).uris.size() == 2

    }

    void "test es configuration with file"() {

        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        applicationContext.containsBean(DefaultElasticsearchConfiguration)
        applicationContext.containsBean(RestHighLevelClient)
        applicationContext.getBean(DefaultElasticsearchConfiguration).uris.size() == 2

    }

    void "test es connection"() {

        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        applicationContext.containsBean(RestHighLevelClient)
        applicationContext.getBean(RestHighLevelClient).ping()

        MainResponse response = applicationContext.getBean(RestHighLevelClient).info()
        System.out.println(String.format("cluser: %s, node: %s, version: %s, build: %s", response.getClusterName(), response.getNodeName(), response.getVersion(), response.getBuild()))

    }
}
