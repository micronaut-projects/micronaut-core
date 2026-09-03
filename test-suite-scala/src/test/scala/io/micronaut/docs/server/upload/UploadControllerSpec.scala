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

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.util.{List as JList, Map as JMap}
import scala.jdk.CollectionConverters.*

class UploadControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "UploadControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally server.stop()

  private def bytes(value: String): Array[Byte] =
    value.getBytes(StandardCharsets.UTF_8)

  private def exchange(client: HttpClient, path: String, body: MultipartBody): HttpResponse[String] =
    client.toBlocking.exchange(
      HttpRequest.POST(path, body)
        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        .accept(MediaType.TEXT_PLAIN_TYPE),
      classOf[String]
    )

  private def errorMessage(e: HttpClientResponseException): Object =
    val body = e.getResponse.getBody(classOf[JMap[String, Object]]).get()
    val embedded = body.get("_embedded").asInstanceOf[JMap[String, Object]]
    val errors = embedded.get("errors").asInstanceOf[JList[JMap[String, Object]]]
    errors.get(0).get("message")

  @Test
  def testFileUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, bytes("""{"title":"Foo"}"""))
        .build()

      val response = exchange(client, "/upload", body)

      assertEquals(HttpStatus.OK, response.status())
      assertEquals("Uploaded", response.getBody.get())
    }

  @Test
  def testFileUploadOutputStream(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, bytes("""{"title":"Foo"}"""))
        .build()

      val response = exchange(client, "/upload/outputStream", body)

      assertEquals(HttpStatus.OK, response.status())
      assertEquals("Uploaded", response.getBody.get())
    }

  @Test
  def testCompletedFileUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, bytes("""{"title":"Foo"}"""))
        .build()

      val response = exchange(client, "/upload/completed", body)

      assertEquals(HttpStatus.OK, response.status())
      assertEquals("Uploaded", response.getBody.get())
    }

  @Test
  def testCompletedFileUploadWithFilenameButNoBytes(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, Array.emptyByteArray)
        .build()

      val response = exchange(client, "/upload/completed", body)

      assertEquals(HttpStatus.OK, response.status())
      assertEquals("Uploaded", response.getBody.get())
    }

  @Test
  def testCompletedFileUploadNoNameWithBytes(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, bytes("""{"title":"Foo"}"""))
        .build()

      val e = assertThrows(classOf[HttpClientResponseException], () => exchange(client, "/upload/completed", body))

      assertEquals("Field [file] was expected to be a file upload, but is missing a file name", errorMessage(e))
    }

  @Test
  def testCompletedFileUploadWithNoFileNameAndNoBytes(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, Array.emptyByteArray)
        .build()

      val e = assertThrows(classOf[HttpClientResponseException], () => exchange(client, "/upload/completed", body))

      assertEquals("Field [file] was expected to be a file upload, but is missing a file name", errorMessage(e))
    }

  @Test
  def testCompletedFileUploadWithNoPart(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("filex", "", MediaType.APPLICATION_JSON_TYPE, Array.emptyByteArray)
        .build()

      val e = assertThrows(classOf[HttpClientResponseException], () => exchange(client, "/upload/completed", body))

      assertEquals("Required argument [CompletedFileUpload file] not specified", errorMessage(e))
    }

  @Test
  def testFileBytesUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "file.json", MediaType.TEXT_PLAIN_TYPE, bytes("some data"))
        .addPart("fileName", "bar")
        .build()

      val response = exchange(client, "/upload/bytes", body)

      assertEquals(HttpStatus.OK, response.status())
      assertEquals("Uploaded", response.getBody.get())
    }

  @Test
  def testWholeBodyUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, bytes("""{"title":"Foo"}"""))
        .addPart("description", "test")
        .build()

      val response = exchange(client, "/upload/whole-body", body)

      assertEquals(HttpStatus.OK, response.status())
      assertEquals("Uploaded", response.getBody.get())
    }
