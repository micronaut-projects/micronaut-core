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


/**
 * Configuration for Elasticsearch RestHighLevelClient.
 * refer to https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.3/java-rest-high.html
 *
 * @author lishuai
 * @since 1.0.1
 */
@Configuration
@RequiresElasticsearch
package io.micronaut.configuration.elasticsearch;

import io.micronaut.configuration.elasticsearch.conditon.RequiresElasticsearch;
import io.micronaut.context.annotation.Configuration;
