package io.micronaut.docs.client.multipart

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.Client
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import org.reactivestreams.Publisher

import javax.inject.Inject

@Singleton
class UploadService {

    static final String URL = ''

    @Inject
    @Client(URL)
    HttpClient client

    Publisher<HttpResponse<String>> uploadFile(File file, MediaType contentType) {

        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file)
                .addPart("title", "default")
                .build()

        client.exchange(
                HttpRequest.POST("/upload/receiveFileUpload", requestBody)
                        .contentType(contentType)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        )
    }

    Publisher<HttpResponse<String>> uploadBytes(byte[] content, String filename, MediaType contentType) {

        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", filename, contentType, content)
                .addPart("title", "default")
                .build()

        client.exchange(
                HttpRequest.POST("/upload/receiveFileUpload", requestBody)
                        .contentType(contentType)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        )
    }

    Publisher<HttpResponse<String>> uploadInputStream(InputStream data, long contentLength, String filename, MediaType contentType) {

        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", filename, contentType, data, contentLength)
                .addPart("title", "default")
                .build()

        client.exchange(
                HttpRequest.POST("/upload/receiveFileUpload", requestBody)
                        .contentType(contentType)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        )
    }


    Publisher<HttpResponse<String>> uploadMixedContent(byte[] content, String filename, MediaType contentType,
                                                       File image
                                                       ) {

        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", filename, contentType, content)
                .addPart("logo", image)
                .build()

        client.exchange(
                HttpRequest.POST("/upload/mixedUpload", requestBody)
                        .contentType(contentType)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        )
    }

    Publisher<HttpResponse<String>> uploadWithPublisher(byte[] content, String filename, MediaType contentType) {

        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", filename, contentType, content)
                .addPart("title", "default")
                .build()

        client.exchange(
                HttpRequest.POST("/upload/receivePublisher", requestBody)
                        .contentType(contentType)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        )
    }


}
