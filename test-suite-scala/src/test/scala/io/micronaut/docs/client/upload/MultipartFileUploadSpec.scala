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
package io.micronaut.docs.client.upload

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import java.io.File
import java.io.FileWriter
// end::imports[]

// tag::multipartBodyImports[]
import io.micronaut.http.client.multipart.MultipartBody
// end::multipartBodyImports[]

// tag::controllerImports[]
import io.micronaut.http.annotation.Controller
import reactor.core.publisher.Flux
// end::controllerImports[]

import scala.jdk.CollectionConverters.*

// tag::class[]
class MultipartFileUploadSpec:
// end::class[]

  @Test
  def testMultipartFileRequestByteArray(): Unit =
    // tag::file[]
    val toWrite = "test file"
    val file = File.createTempFile("data", ".txt")
    val writer = FileWriter(file)
    try writer.write(toWrite)
    finally writer.close()
    // end::file[]

    // tag::multipartBody[]
    val requestBody = MultipartBody.builder()     // <1>
      .addPart(                                   // <2>
        "data",
        file.getName,
        MediaType.TEXT_PLAIN_TYPE,
        file
      ).build()                                   // <3>

    // end::multipartBody[]

    val flowable = Flux.from(MultipartFileUploadSpec.client.exchange(
      // tag::request[]
      HttpRequest.POST("/multipart/upload", requestBody)    // <1>
        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)    // <2>
      // end::request[]
        .accept(MediaType.TEXT_PLAIN_TYPE),
      classOf[String]
    ))
    val response = flowable.blockFirst()
    val body = response.getBody.get()

    assertEquals("Uploaded 9 bytes", body)

  @Test
  def testMultipartFileRequestByteArrayWithContentType(): Unit =
    // tag::multipartBodyBytes[]
    val requestBody = MultipartBody.builder()
      .addPart("data", "sample.txt", MediaType.TEXT_PLAIN_TYPE, "test content".getBytes())
      .build()
    // end::multipartBodyBytes[]

    val flowable = Flux.from(MultipartFileUploadSpec.client.exchange(
      HttpRequest.POST("/multipart/upload", requestBody)
        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        .accept(MediaType.TEXT_PLAIN_TYPE),
      classOf[String]
    ))
    val response = flowable.blockFirst()
    val body = response.getBody.get()

    assertEquals("Uploaded 12 bytes", body)

  @Test
  def testMultipartFileRequestByteArrayWithoutContentType(): Unit =
    val toWrite = "test file"
    val file = File.createTempFile("data", ".txt")
    val writer = FileWriter(file)
    try writer.write(toWrite)
    finally writer.close()
    file.createNewFile()

    val flowable = Flux.from(MultipartFileUploadSpec.client.exchange(
      HttpRequest.POST("/multipart/upload", MultipartBody.builder().addPart("data", file.getName, file))
        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        .accept(MediaType.TEXT_PLAIN_TYPE),
      classOf[String]
    ))
    val response = flowable.blockFirst()
    val body = response.getBody.get()

    assertEquals("Uploaded 9 bytes", body)

object MultipartFileUploadSpec:
  private var context: ApplicationContext = _
  private var embeddedServer: EmbeddedServer = _
  private var client: HttpClient = _

  @BeforeAll
  def setupServer(): Unit =
    embeddedServer = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "MultipartFileUploadSpec").asJava
    )
    context = embeddedServer.getApplicationContext
    client = context.createBean(classOf[HttpClient], embeddedServer.getURL)

  @AfterAll
  def stopServer(): Unit =
    if embeddedServer != null then embeddedServer.stop()
    if client != null then client.stop()

@Requires(property = "spec.name", value = "MultipartFileUploadSpec")
@Controller("/multipart")
class MultipartController:

  @Post(value = "/upload", consumes = Array(MediaType.MULTIPART_FORM_DATA), produces = Array(MediaType.TEXT_PLAIN))
  def upload(data: Array[Byte]): HttpResponse[String] =
    HttpResponse.ok(s"Uploaded ${data.length} bytes")
