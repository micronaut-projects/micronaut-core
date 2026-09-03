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
package io.micronaut.docs.server.form

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.PartData
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
// end::imports[]

@Requires(property = "spec.name", value = "FormControllerTest")
// tag::class[]
@Controller("/form")
class FormController:
// end::class[]

  // tag::String[]
  @Consumes(Array(MediaType.MULTIPART_FORM_DATA))
  @Post("/string")
  def setUserName(userId: Int, userName: String): String =
    if !userName.matches("[a-z]+") then
      throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid username")
    "New user name for user ID " + userId + ": " + userName
  // end::String[]

  // tag::CompletedFileUpload[]
  @Consumes(Array(MediaType.MULTIPART_FORM_DATA))
  @Post("/file-upload")
  @ExecuteOn(TaskExecutors.BLOCKING) // <1>
  def fileUpload(userId: Int, avatar: CompletedFileUpload): String =
    val tmp = Files.createTempFile("avatar" + userId, null)
    try
      avatar.transferTo(tmp) // <2>

      "Uploaded avatar for user " + userId + ": " + Files.size(tmp) + " bytes"
    finally
      Files.delete(tmp)
  // end::CompletedFileUpload[]

  // tag::StreamingFileUpload[]
  @Consumes(Array(MediaType.MULTIPART_FORM_DATA))
  @Post("/file-upload-streaming")
  @ExecuteOn(TaskExecutors.BLOCKING) // <1>
  def streamingFileUpload(userId: Int, avatar: StreamingFileUpload): String =
    val stream: InputStream = avatar.asInputStream() // <2>
    try
      val count = stream.readAllBytes().length // <3>
      "Streamed avatar for user " + userId + ": " + count + " bytes"
    finally
      stream.close()
  // end::StreamingFileUpload[]

  // tag::PublisherCompletedFileUpload[]
  @Consumes(Array(MediaType.MULTIPART_FORM_DATA))
  @Post("/file-upload-completed-publisher")
  def fileUploadCompletedPublisher(userId: Int, avatar: Publisher[CompletedFileUpload]): Mono[String] =
    Mono.from(avatar) // <1>
      .map(cfu => "Uploaded avatar for user " + userId + ": " + cfu.getSize + " bytes")
  // end::PublisherCompletedFileUpload[]

  // tag::PublisherStreamingFileUpload[]
  @Consumes(Array(MediaType.MULTIPART_FORM_DATA))
  @Post("/file-upload-streaming-publisher")
  def fileUploadStreamingPublisher(userId: Int, avatar: Publisher[StreamingFileUpload]): Publisher[String] =
    val tmp = Files.createTempFile("upload", null) // <1>
    Mono.from(avatar)
      .flatMap(sfu => Mono.from(sfu.transferTo(tmp)))
      .`then`(Mono.fromCallable(() =>
        try
          "Streamed avatar for user " + userId + ": " + Files.size(tmp) + " bytes" // <1>
        catch
          case e: IOException => throw new UncheckedIOException(e)
      ))
      .doOnTerminate(() =>
        try Files.deleteIfExists(tmp) // <1>
        catch
          case e: IOException => throw new UncheckedIOException(e)
      )
  // end::PublisherStreamingFileUpload[]

  // tag::PublisherPublisherBytes[]
  @Consumes(Array(MediaType.MULTIPART_FORM_DATA))
  @Post("/publisher-publisher")
  def publisherPublisher(userId: Int, avatar: Publisher[Publisher[Array[Byte]]]): Publisher[String] =
    Flux.from(avatar)
      .flatMap(p => Flux.from(p).collect(Collectors.summingInt((arr: Array[Byte]) => arr.length)))
      .collectList()
      .map(lengths => "Streamed avatars for user " + userId + ": " + lengths + " bytes")
  // end::PublisherPublisherBytes[]

  // tag::PublisherPartData[]
  @Consumes(Array(MediaType.MULTIPART_FORM_DATA))
  @Post("/publisher-part-data")
  def publisherPartData(userId: Int, avatar: Publisher[PartData]): Publisher[String] =
    Flux.from(avatar)
      .collect(Collectors.summingInt((part: PartData) =>
        try part.getBytes.length
        finally part.close()
      ))
      .map(lengths => "Streamed avatars for user " + userId + ": " + lengths + " bytes")
  // end::PublisherPartData[]

// tag::endclass[]
// end::endclass[]
