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
import org.apache.http.HttpHost
import org.elasticsearch.client.NodeSelector
import org.elasticsearch.client.RestHighLevelClient
import spock.lang.Specification

/**
 * @author puneetbehl
 * @since 1.1.0
 */
class ElasticsearchConfigurationPropertiesSpec extends Specification {

    void "Test Elasticsearch high level rest client configrations"() {

        when:
        ApplicationContext applicationContext = ApplicationContext.run(

                'elasticsearch.httpHosts': 'http://127.0.0.1:9200',
                'elasticsearch.defaultHeaders': 'Content-Type:application/json',
                'elasticsearch.maxRetryTimeoutMillis': 1000,
                'elasticsearch.nodeSelector': "SKIP_DEDICATED_MASTERS",
        )

        then:
        applicationContext.containsBean(ElasticsearchConfigurationProperties)
        applicationContext.getBean(ElasticsearchConfigurationProperties).httpHosts == [new HttpHost('127.0.0.1', 9200, 'http')].toArray()
        applicationContext.getBean(ElasticsearchConfigurationProperties).defaultHeaders.length == 1
        applicationContext.getBean(ElasticsearchConfigurationProperties).maxRetryTimeoutMillis == 1000
        applicationContext.getBean(ElasticsearchConfigurationProperties).nodeSelector == NodeSelector.SKIP_DEDICATED_MASTERS

        cleanup:
        applicationContext.close()

    }

    void "Test configure multiple hosts"() {

        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                'elasticsearch.httpHosts': 'http://127.0.0.1:9200,http://127.0.1.1:9200',
                'elasticsearch.maxRetryTimeoutMillis': 1000
        )

        then:
        applicationContext.containsBean(ElasticsearchConfigurationProperties)
        applicationContext.getBean(ElasticsearchConfigurationProperties).httpHosts == [new HttpHost('127.0.0.1', 9200, 'http'),
                                                                                       new HttpHost('127.0.1.1', 9200, 'http')].toArray()

        cleanup:
        applicationContext.close()

    }

    void "Test Elasticsearch default request configurations"() {

        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                "elasticsearch.httpHosts": "http://127.0.0.1:9200,http://127.0.1.1:9200",
                "elasticsearch.maxRetryTimeoutMillis": 1000,
                "elasticsearch.request.default.localAddress": "198.57.151.22",
                "elasticsearch.request.default.expectContinueEnabled": true


        )

        then:
        applicationContext.containsBean(ElasticsearchConfigurationProperties)
        applicationContext.getBean(ElasticsearchConfigurationProperties).httpHosts == [new HttpHost('127.0.0.1', 9200, 'http'),
                                                                                       new HttpHost('127.0.1.1', 9200, 'http')].toArray()

        cleanup:
        applicationContext.close()

    }

    void "Test Elasticsearch configuration with file"() {

        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        applicationContext.containsBean(ElasticsearchConfigurationProperties)
        applicationContext.containsBean(RestHighLevelClient)
        applicationContext.getBean(ElasticsearchConfigurationProperties).httpHosts.size() == 2

    }

}
