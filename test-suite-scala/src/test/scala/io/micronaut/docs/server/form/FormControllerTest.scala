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

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class FormControllerTest:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "FormControllerTest").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  @Test
  def testString(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("userId", "5")
        .addPart("userName", "yawkat")
        .build()
      assertEquals(
        "New user name for user ID 5: yawkat",
        client.toBlocking.retrieve(HttpRequest.POST("/form/string", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
      )
    }

  @Test
  def testFileUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("userId", "5")
        .addPart("avatar", "avatar.png", new Array[Byte](16))
        .build()
      assertEquals(
        "Uploaded avatar for user 5: 16 bytes",
        client.toBlocking.retrieve(HttpRequest.POST("/form/file-upload", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
      )
    }

  @Test
  def testStreamingFileUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("userId", "6")
        .addPart("avatar", "avatar.png", new Array[Byte](8))
        .build()
      assertEquals(
        "Streamed avatar for user 6: 8 bytes",
        client.toBlocking.retrieve(HttpRequest.POST("/form/file-upload-streaming", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
      )
    }

  @Test
  def testPublisherCompletedFileUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("userId", "7")
        .addPart("avatar", "a1.png", new Array[Byte](10))
        .build()
      assertEquals(
        "Uploaded avatar for user 7: 10 bytes",
        client.toBlocking.retrieve(HttpRequest.POST("/form/file-upload-completed-publisher", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
      )
    }

  @Test
  def testPublisherStreamingFileUpload(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("userId", "8")
        .addPart("avatar", "a1.png", new Array[Byte](15))
        .build()
      assertEquals(
        "Streamed avatar for user 8: 15 bytes",
        client.toBlocking.retrieve(HttpRequest.POST("/form/file-upload-streaming-publisher", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
      )
    }

  @Test
  def testPublisherPublisher(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("userId", "8")
        .addPart("avatar", "a1.png", new Array[Byte](15))
        .addPart("avatar", "a2.png", new Array[Byte](30))
        .build()
      assertEquals(
        "Streamed avatars for user 8: [15, 30] bytes",
        client.toBlocking.retrieve(HttpRequest.POST("/form/publisher-publisher", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
      )
    }

  @Test
  def testPublisherPartData(): Unit =
    withClient { client =>
      val body = MultipartBody.builder()
        .addPart("userId", "8")
        .addPart("avatar", "a1.png", new Array[Byte](15))
        .addPart("avatar", "a2.png", new Array[Byte](30))
        .build()
      assertEquals(
        "Streamed avatars for user 8: 45 bytes",
        client.toBlocking.retrieve(HttpRequest.POST("/form/publisher-part-data", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
      )
    }
