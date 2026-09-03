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
package io.micronaut.docs.server.upload

// tag::class[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

@Requires(property = "spec.name", value = "UploadControllerSpec")
@Controller("/upload")
class UploadController:
// end::class[]

  // tag::file[]
  @Post(value = "/", consumes = Array(MediaType.MULTIPART_FORM_DATA), produces = Array(MediaType.TEXT_PLAIN)) // <1>
  @SingleResult
  def upload(file: StreamingFileUpload): Publisher[HttpResponse[String]] = // <2>

    val tempFile =
      try File.createTempFile(file.getFilename, "temp")
      catch
        case e: IOException => return Mono.error(e)

    val uploadPublisher = file.transferTo(tempFile) // <3>

    Mono.from(uploadPublisher) // <4>
      .`thenReturn`(HttpResponse.ok("Uploaded"))
      .onErrorReturn(HttpResponse.status[String](HttpStatus.CONFLICT).body("Upload Failed"))
  // end::file[]

  // tag::outputStream[]
  @Post(value = "/outputStream", consumes = Array(MediaType.MULTIPART_FORM_DATA), produces = Array(MediaType.TEXT_PLAIN)) // <1>
  @SingleResult
  def uploadOutputStream(file: StreamingFileUpload): Mono[HttpResponse[String]] = // <2>

    val outputStream: OutputStream = ByteArrayOutputStream() // <3>

    val uploadPublisher = file.transferTo(outputStream) // <4>

    Mono.from(uploadPublisher) // <5>
      .`thenReturn`(HttpResponse.ok("Uploaded"))
      .onErrorReturn(HttpResponse.status[String](HttpStatus.CONFLICT).body("Upload Failed"))
  // end::outputStream[]

// tag::endclass[]
// end::endclass[]
