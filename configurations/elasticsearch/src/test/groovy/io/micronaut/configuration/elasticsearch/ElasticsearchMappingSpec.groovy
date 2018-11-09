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
import org.elasticsearch.Version
import org.elasticsearch.action.DocWriteResponse
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import org.testcontainers.elasticsearch.ElasticsearchContainer
import spock.lang.Specification

/**
 * @author lishuai
 * @author Puneet Behl
 * @since 1.0.1
 */
class ElasticsearchMappingSpec extends Specification {

    void "Test Elasticsearch(6.x) Mapping API"() {

        given:
        ElasticsearchContainer container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:6.4.1");
        container.start()

        ApplicationContext applicationContext = ApplicationContext.run('elasticsearch.uris': 'http://' + container.getHttpHostAddress())
        RestHighLevelClient client = applicationContext.getBean(RestHighLevelClient)

        expect: "Make sure the version of ES is 6.4.1 because these tests may cause unexpected results"
        client.info(RequestOptions.DEFAULT).getVersion().equals(Version.fromString("6.4.1"))

        when:
        GetIndexRequest getIndexRequest = new GetIndexRequest().indices("posts")

        then: "index does not exists"
        !client.indices().exists(getIndexRequest, RequestOptions.DEFAULT)

        when: "create index request"
        IndexRequest request = new IndexRequest(
                "posts",
                "doc",
                "1")
        String jsonString = "{" +
                "\"user\":\"kimchy\"," +
                "\"postDate\":\"2013-01-30\"," +
                "\"message\":\"trying out Elasticsearch\"" +
                "}"

        request.source(jsonString, XContentType.JSON)
        IndexResponse response = client.index(request, RequestOptions.DEFAULT)

        then: "verify version and result"
        response.getType() == "doc"
        response.getIndex() == "posts"
        response.getVersion() == 1
        response.getResult() == DocWriteResponse.Result.CREATED

        when: "update index request"
        request = new IndexRequest(
                "posts",
                "doc",
                "1")
        jsonString = "{" +
                "\"user\":\"kimchy1\"," +
                "\"postDate\":\"2018-10-30\"," +
                "\"message\":\"Trying out Elasticsearch6\"" +
                "}"

        request.source(jsonString, XContentType.JSON)
        response = client.index(request, RequestOptions.DEFAULT)

        then: "verify version and result"
        response.getType() == "doc"
        response.getIndex() == "posts"
        response.getVersion() == 2
        response.getResult() == DocWriteResponse.Result.UPDATED

        when: "get request"
        GetRequest getRequest = new GetRequest(
                "posts",
                "doc",
                "1").
                fetchSourceContext(FetchSourceContext.DO_NOT_FETCH_SOURCE)

        String[] includes = ["message", "*Date"]
        String[] excludes = []
        FetchSourceContext fetchSourceContext =
                new FetchSourceContext(true, includes, excludes);
        getRequest.fetchSourceContext(fetchSourceContext)


        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT)

        then: "verify source"
        getResponse.getType() == "doc"
        getResponse.getIndex() == "posts"
        getResponse.isExists()
        getResponse.getVersion() == 2
        getResponse.getSourceAsMap() == [postDate:"2018-10-30", message:"Trying out Elasticsearch6"]


        when: "exits request"
        getRequest = new GetRequest(
                "posts",
                "doc",
                "1")
        getRequest.fetchSourceContext(new FetchSourceContext(false))
        getRequest.storedFields("_none_")

        then:
        client.exists(getRequest, RequestOptions.DEFAULT)


        when: "delete request"
        DeleteRequest deleteRequest = new DeleteRequest(
                "posts",
                "doc",
                "1")

        DeleteResponse deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT)

        then:
        deleteResponse.getType() == "doc"
        deleteResponse.getIndex() == "posts"

        cleanup:
        applicationContext.close()
        container.stop()
    }

}
