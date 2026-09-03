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
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.CompletedPart
import io.micronaut.http.server.multipart.MultipartBody
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

import java.io.IOException

@Requires(property = "spec.name", value = "UploadControllerSpec")
@Controller("/upload")
class WholeBodyUploadController:

  @Post(value = "/whole-body", consumes = Array(MediaType.MULTIPART_FORM_DATA), produces = Array(MediaType.TEXT_PLAIN)) // <1>
  @SingleResult
  def uploadBytes(@Body body: MultipartBody): Publisher[String] = // <2>

    Flux.from(body)
      .publishOn(Schedulers.boundedElastic())
      .doOnNext { completedPart =>
        val partName = completedPart.getName
        completedPart match
          case upload: CompletedFileUpload =>
            val originalFileName = upload.getFilename
          case _ =>
        try completedPart.close()
        catch
          case e: IOException => throw RuntimeException(e)
      }
      .`then`(Mono.just("Uploaded"))
// end::class[]
