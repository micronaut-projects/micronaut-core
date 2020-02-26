/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.config.env

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory

import java.net.URI
import java.sql.Connection

// tag::eachBean[]
@Factory // <1>
class DataSourceFactory {

    @EachBean(DataSourceConfiguration::class) // <2>
    internal fun dataSource(configuration: DataSourceConfiguration): DataSource { // <3>
        val url = configuration.url
        return DataSource(url)
    }

    // end::eachBean[]
    internal class DataSource(private val uri: URI) {

        fun connect(): Connection {
            throw UnsupportedOperationException("Can't really connect. I'm not a real data source")
        }
    }
}
