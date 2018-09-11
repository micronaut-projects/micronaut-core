/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.http.server.netty.types

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.server.netty.types.files.FileTypeHandler
import io.micronaut.http.server.netty.types.files.FileTypeHandlerConfiguration
import io.micronaut.http.server.netty.types.files.NettyStreamedFileCustomizableResponseType
import io.micronaut.http.server.netty.types.files.NettySystemFileCustomizableResponseType
import io.micronaut.http.server.types.files.AttachedFile
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.http.server.types.files.SystemFileCustomizableResponseType

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import static io.micronaut.http.HttpHeaders.*

class FileTypeHandlerSpec extends AbstractMicronautSpec {

    private static File tempFile
    private static String tempFileContents = "<html><head></head><body>HTML Page</body></html>"

    static {
        tempFile = File.createTempFile("fileTypeHandlerSpec", ".html")
        tempFile.write(tempFileContents)
        tempFile
    }

    void "test returning a file from a controller"() {
        when:
        def response = rxClient.exchange('/test/html', String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test 304 is returned if the correct header is sent"() {
        when:
        MutableHttpRequest<?> request = HttpRequest.GET('/test/html')
        request.headers.ifModifiedSince(tempFile.lastModified())
        def response = rxClient.exchange(request, String).blockingFirst()

        then:
        response.code() == HttpStatus.NOT_MODIFIED.code
        response.header(DATE)
    }

    void "test what happens when a file isn't found"() {
        when:
        rxClient.exchange('/test/not-found', String).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.body() == '{"message":"Internal Server Error: Could not find file"}'
    }

    void "test when an attached file is returned"() {
        when:
        def response = rxClient.exchange('/test/download', String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"fileTypeHandlerSpec")
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test when an attached file is returned with a name"() {
        when:
        def response = rxClient.exchange('/test/different-name', String).blockingFirst()

        then: "the content type is still based on the file extension"
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION) == "attachment; filename=\"abc.xyz\""
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test the content type is honored when an attached file response is returned"() {
        when:
        def response = rxClient.exchange('/test/custom-content-type', String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_DISPOSITION) == "attachment; filename=\"temp.html\""
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test supports"() {
        when:
        FileTypeHandler fileTypeHandler = new FileTypeHandler(new FileTypeHandlerConfiguration())

        then:
        fileTypeHandler.supports(type) == expected

        where:
        type                                      | expected
        NettySystemFileCustomizableResponseType   | true
        NettyStreamedFileCustomizableResponseType | true
        SystemFileCustomizableResponseType        | true
        StreamedFile                              | true
        File                                      | true
        AttachedFile                              | true
    }

    @Controller('/test')
    @Requires(property = 'spec.name', value = 'FileTypeHandlerSpec')
    static class TestController {

        @Get('/html')
        File html() {
            tempFile
        }

        @Get('/not-found')
        File notFound() {
            new File('/xyzabc')
        }

        @Get('/download')
        AttachedFile download() {
            new AttachedFile(tempFile)
        }

        @Get('/different-name')
        AttachedFile differentName() {
            new AttachedFile(tempFile, "abc.xyz")
        }

        @Get('/custom-content-type')
        HttpResponse<AttachedFile> customContentType() {
            HttpResponse.ok(new AttachedFile(tempFile, "temp.html")).contentType(MediaType.TEXT_PLAIN_TYPE)
        }
    }
}
