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
package io.micronaut.docs.http.server.stream

import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.IOUtils
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@Requires(property = "spec.name", value = "StreamControllerSpec")
@Controller("/stream")
class StreamController:

  // tag::write[]
  @Get(value = "/write", produces = Array(MediaType.TEXT_PLAIN))
  def write(): InputStream =
    val bytes = "test".getBytes(StandardCharsets.UTF_8)
    ByteArrayInputStream(bytes) // <1>
  // end::write[]

  // tag::read[]
  @Post(value = "/read", processes = Array(MediaType.TEXT_PLAIN))
  @ExecuteOn(TaskExecutors.IO) // <1>
  def read(@Body inputStream: InputStream): String = // <2>
    IOUtils.readText(BufferedReader(InputStreamReader(inputStream))) // <3>
  // end::read[]
