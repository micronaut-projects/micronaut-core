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

package io.micronaut.configuration.elasticsearch6

import io.micronaut.context.ApplicationContext
import org.elasticsearch.Version
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import spock.lang.Specification

/**
 * @author lishuai
 * @since 1.0.1
 */
class ElasticsearchMappingSpec extends Specification {

    void "test es(6.x) mapping apis"() {

        given:
        def indiceName = "twitter_test"

        ApplicationContext applicationContext = ApplicationContext.run('elasticsearch.uris':'http://localhost:9200,http://127.0.0.1:9200')
        RestHighLevelClient client = applicationContext.getBean(RestHighLevelClient)

        // make sure es version 6.0.0+
        if (client.info().getVersion().before(Version.fromString("6.0.0"))) {
            return
        }

        // Indices APIs
        GetIndexRequest getIndexRequest = new GetIndexRequest().indices(indiceName)
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indiceName)
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indiceName)

        // Document APIs
        IndexRequest indexRequest = new IndexRequest(indiceName,"doc","1")
        GetRequest getRequest = new GetRequest(indiceName,"doc","1")
        DeleteRequest deleteRequest = new DeleteRequest(indiceName,"doc","1")

        // if indice exits , delete it
        if (client.indices().exists(getIndexRequest)) {
            client.indices().delete(deleteIndexRequest)
        }

        expect:
        !client.indices().exists(getIndexRequest)

        // create indice
        when:
        createIndexRequest.source("{\n" +
                "    \"settings\" : {\n" +
                "        \"number_of_shards\" : 1,\n" +
                "        \"number_of_replicas\" : 0\n" +
                "    },\n" +
                "    \"mappings\" : {\n" +
                "        \"doc\" : {\n" +
                "            \"properties\" : {\n" +
                "                \"message\" : { \"type\" : \"text\" }\n" +
                "            }\n" +
                "        }\n" +
                "    },\n" +
                "    \"aliases\" : {\n" +
                "        \"twitter_alias\" : {}\n" +
                "    }\n" +
                "}", XContentType.JSON)
        client.indices().create(createIndexRequest).isAcknowledged()

        then:
        client.indices().exists(getIndexRequest)

        when:
        String jsonString = "{" +
                "\"message\":\"trying out Elasticsearch\"" +
                "}"
        indexRequest.source(jsonString, XContentType.JSON)

        then:
        if (client.exists(getRequest)) {
            client.delete(deleteRequest)
        }
        client.index(indexRequest).getIndex() == indiceName
        System.out.println(String.format("mapping fields: %s", client.get(getRequest).getSourceAsString()))

        cleanup:
        // delete indice
        if (client.indices().exists(getIndexRequest)) {
            client.indices().delete(deleteIndexRequest)
        }
        client.close()

    }

}
