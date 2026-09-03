/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.config.env

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory

import java.net.URI
import java.sql.Connection

// tag::eachBean[]
@Factory // <1>
class DataSourceFactory:

  @EachBean(classOf[DataSourceConfiguration]) // <2>
  def dataSource(configuration: DataSourceConfiguration): DataSourceFactory.DataSource = // <3>
    val url: URI = configuration.url
    new DataSourceFactory.DataSource(url)
// end::eachBean[]

object DataSourceFactory:

  class DataSource(val uri: URI):

    def connect(): Connection =
      throw new UnsupportedOperationException("Can't really connect. I'm not a real data source")
