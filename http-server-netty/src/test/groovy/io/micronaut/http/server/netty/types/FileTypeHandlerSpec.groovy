/*
 * Copyright 2017-2019 original authors
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


import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import groovy.transform.CompileStatic
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
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.http.server.netty.types.files.FileTypeHandler
import io.micronaut.http.server.netty.types.files.NettyStreamedFileCustomizableResponseType
import io.micronaut.http.server.netty.types.files.NettySystemFileCustomizableResponseType
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.http.server.types.files.SystemFile
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.IgnoreIf

import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService

import static io.micronaut.http.HttpHeaders.CACHE_CONTROL
import static io.micronaut.http.HttpHeaders.CONTENT_DISPOSITION
import static io.micronaut.http.HttpHeaders.CONTENT_LENGTH
import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.HttpHeaders.DATE
import static io.micronaut.http.HttpHeaders.EXPIRES
import static io.micronaut.http.HttpHeaders.LAST_MODIFIED

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
        def response = rxClient.toBlocking().exchange('/test/html', String)

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
        def response = rxClient.toBlocking().exchange(request, String)

        then:
        response.code() == HttpStatus.NOT_MODIFIED.code
        response.header(DATE)
    }

    void "test cache control can be overridden"() {
        when:
        MutableHttpRequest<?> request = HttpRequest.GET('/test/custom-cache-control')
        def response = rxClient.toBlocking().exchange(request, String)

        then:
        response.code() == HttpStatus.OK.code
        response.getHeaders().getAll(CACHE_CONTROL).size() == 1
        response.header(CACHE_CONTROL) == "public, immutable, max-age=31556926"
    }

    void "test what happens when a file isn't found"() {
        when:
        rxClient.toBlocking().exchange('/test/not-found', String)

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response
        def body = sortJson(response.body())

        then:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        body == '{"_embedded":{"errors":[{"message":"Internal Server Error: Could not find file"}]},"_links":{"self":{"href":"/test/not-found","templated":false}},"message":"Internal Server Error"}'
    }

    void "test when an attached file is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test/download', String)

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

    void "test when a system file is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-system/download', String)

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

    //Using curl because the netty client reads the first chunk and alters the headers
    @IgnoreIf({ os.windows })
    void "test when a streamed file is returned transfer-encoding should be set"() {
        when:
        String curlCommand = "curl -I -X GET ${getServer().toString()}/test-stream/download"
        Process process = ['bash', '-c', curlCommand].execute()
        String output = process.text.toLowerCase()

        then:
        output.contains("200 ok")
        output.contains("transfer-encoding: chunked")
        !output.contains("content-length:")
    }

    //Using curl because the netty client will remove one of transfer encoding or
    // content type if both are set
    @IgnoreIf({ os.windows })
    void "est when a system file is returned content-length should be set"() {
        when:
        String curlCommand = "curl -I -X GET ${getServer().toString()}/test-system/download"
        Process process = ['bash', '-c', curlCommand].execute()
        String output = process.text.toLowerCase()

        then:
        output.contains("200 ok")
        !output.contains("transfer-encoding: chunked")
        output.contains("content-length:")
    }

    void "test when an attached streamed file is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-stream/download', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"fileTypeHandlerSpec")
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == tempFileContents
    }

    void "test when an attached file is returned with a name"() {
        when:
        def response = rxClient.toBlocking().exchange('/test/different-name', String)

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

    void "test when a system file is returned with a name"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-system/different-name', String)

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
        def response = rxClient.toBlocking().exchange('/test/custom-content-type', String)

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

    void "test the content type is honored when a system file response is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-system/custom-content-type', String)

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

    void "test the content type is honored when a streamed file response is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-stream/custom-content-type', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_DISPOSITION) == "attachment; filename=\"temp.html\""
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == tempFileContents
    }

    void "test using a piped stream"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-stream/piped-stream', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.body() == ("a".."z").join('')
    }

    void "test supports"() {
        when:
        FileTypeHandler fileTypeHandler = new FileTypeHandler(new NettyHttpServerConfiguration.FileTypeHandlerConfiguration())

        then:
        fileTypeHandler.supports(type) == expected

        where:
        type                                      | expected
        NettySystemFileCustomizableResponseType   | true
        NettyStreamedFileCustomizableResponseType | true
        StreamedFile                              | true
        File                                      | true
        SystemFile                                | true
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
        SystemFile download() {
            new SystemFile(tempFile).attach("fileTypeHandlerSpec ")
        }

        @Get('/custom-cache-control')
        HttpResponse<File> cacheControl() {
            HttpResponse.ok(tempFile)
                        .header(CACHE_CONTROL, "public, immutable, max-age=31556926")
        }

        @Get('/different-name')
        SystemFile differentName() {
            new SystemFile(tempFile).attach("abc.xyz")
        }

        @Get('/custom-content-type')
        HttpResponse<SystemFile> customContentType() {
            HttpResponse.ok(new SystemFile(tempFile, MediaType.TEXT_HTML_TYPE).attach("temp.html")).contentType(MediaType.TEXT_PLAIN_TYPE)
        }
    }

    @Controller('/test-system')
    @Requires(property = 'spec.name', value = 'FileTypeHandlerSpec')
    static class TestSystemController {

        @Get('/download')
        SystemFile download() {
            new SystemFile(tempFile).attach()
        }

        @Get('/different-name')
        SystemFile differentName() {
            new SystemFile(tempFile).attach("abc.xyz")
        }

        @Get('/custom-content-type')
        HttpResponse<SystemFile> customContentType() {
            HttpResponse.ok(new SystemFile(tempFile, MediaType.TEXT_PLAIN_TYPE).attach("temp.html"))
        }
    }

    @Controller('/test-stream')
    @Requires(property = 'spec.name', value = 'FileTypeHandlerSpec')
    static class TestStreamController {

        @Named("io")
        @Inject
        ExecutorService executorService

        @Get('/download')
        StreamedFile download() {
            new StreamedFile(Files.newInputStream(tempFile.toPath()), MediaType.TEXT_HTML_TYPE).attach("fileTypeHandlerSpec.html")
        }

        @Get('/custom-content-type')
        HttpResponse<StreamedFile> customContentType() {
            HttpResponse.ok(new StreamedFile(Files.newInputStream(tempFile.toPath()), MediaType.TEXT_HTML_TYPE).attach("temp.html"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
        }

        @Get('/piped-stream')
        StreamedFile pipedStream() {
            def output = new PipedOutputStream()
            def input = new PipedInputStream(output)
            executorService.execute({ ->
                ("a".."z").each {
                    output.write(it.bytes)
                }
                output.flush()
                output.close()
            })
            return new StreamedFile(input, MediaType.TEXT_PLAIN_TYPE)
        }

    }

    @CompileStatic
    private static String sortJson(String input) {
        def mapper = JsonMapper.builder()
                .nodeFactory(new SortingNodeFactory())
                .build()
        def tree = mapper.readTree(input)
        mapper.writeValueAsString(tree)
    }

    @CompileStatic
    static class SortingNodeFactory extends JsonNodeFactory {
        @Override
        ObjectNode objectNode() {
            return new ObjectNode(this, new TreeMap<>())
        }
    }
}
