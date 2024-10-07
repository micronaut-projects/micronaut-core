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
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.server.types.files.FileCustomizableResponseType
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.http.server.types.files.SystemFile
import jakarta.inject.Inject
import jakarta.inject.Named
import reactor.core.publisher.Mono
import spock.lang.IgnoreIf

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService

import static io.micronaut.http.HttpHeaders.ACCEPT_RANGES
import static io.micronaut.http.HttpHeaders.CACHE_CONTROL
import static io.micronaut.http.HttpHeaders.CONTENT_DISPOSITION
import static io.micronaut.http.HttpHeaders.CONTENT_LENGTH
import static io.micronaut.http.HttpHeaders.CONTENT_RANGE
import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.HttpHeaders.DATE
import static io.micronaut.http.HttpHeaders.EXPIRES
import static io.micronaut.http.HttpHeaders.LAST_MODIFIED
import static io.micronaut.http.HttpHeaders.RANGE

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
        def response = httpClient.toBlocking().exchange('/test/html', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.headers.getAll(DATE).size() == 1
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test 304 is returned if the correct header is sent"() {
        when:
        MutableHttpRequest<?> request = HttpRequest.GET('/test/html')
        request.headers.ifModifiedSince(tempFile.lastModified())
        def response = httpClient.toBlocking().exchange(request, String)

        then:
        response.code() == HttpStatus.NOT_MODIFIED.code
        response.header(DATE)
    }

    void "test 206 is returned for Byte-Range queries"() {
        when:
        MutableHttpRequest<?> request = HttpRequest.GET('/test/html')
        request.headers.add(RANGE, range)
        def response = httpClient.toBlocking().exchange(request, String)

        then:
        response.code() == expectedStatus
        response.header(ACCEPT_RANGES) == "bytes"
        response.header(CONTENT_RANGE) == expectedContentRange
        response.header(CONTENT_LENGTH) == Long.toString(expectedContent.length())
        response.body() == expectedContent

        where:
        range          | expectedStatus | expectedContentRange                                     | expectedContent
        ""             | 200            | null                                                     | tempFileContents
        "bytes"        | 200            | null                                                     | tempFileContents
        "bytes="       | 200            | null                                                     | tempFileContents
        "bytes=2"      | 200            | null                                                     | tempFileContents
        "bytes=9000-"  | 200            | null                                                     | tempFileContents
        "bytes=0-9000" | 200            | null                                                     | tempFileContents
        "bytes=abc-10" | 200            | null                                                     | tempFileContents
        "bytes=0-def"  | 200            | null                                                     | tempFileContents
        "bytes=0-"     | 206            | "bytes 0-${tempFile.length() - 1}/${tempFile.length()}"  | tempFileContents
        "bytes=10-"    | 206            | "bytes 10-${tempFile.length() - 1}/${tempFile.length()}" | tempFileContents.substring(10)
        "bytes=1-2"    | 206            | "bytes 1-2/${tempFile.length()}"                         | tempFileContents.substring(1, 3)
    }

    void "test cache control can be overridden"() {
        when:
        MutableHttpRequest<?> request = HttpRequest.GET('/test/custom-cache-control')
        def response = httpClient.toBlocking().exchange(request, String)

        then:
        response.code() == HttpStatus.OK.code
        response.getHeaders().getAll(CACHE_CONTROL).size() == 1
        response.header(CACHE_CONTROL) == "public, immutable, max-age=31556926"
    }

    void "test what happens when a file isn't found"() {
        when:
        httpClient.toBlocking().exchange('/test/not-found', String)

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
        def response = httpClient.toBlocking().exchange('/test/download', String)

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
        def response = httpClient.toBlocking().exchange('/test-system/download', String)

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
        def response = httpClient.toBlocking().exchange('/test-stream/download', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"fileTypeHandlerSpec")
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == tempFileContents
    }

    void "test when an reactive attached streamed file is returned"() {
        when:
        def response = httpClient.toBlocking().exchange('/test-stream/reactive-stream', byte[].class)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == MediaType.TEXT_PLAIN
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == "My file content".bytes
    }

    void "test when an reactive attached system file is returned"() {
        given:
        def file = Files.createTempFile("test", ".txt").toFile().tap {
            it << "System file!"
        }

        when:
        def response = httpClient.toBlocking().exchange("/test-stream/reactive-system?path=$file.absolutePath", byte[].class)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == MediaType.TEXT_PLAIN
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == "System file!".bytes

        cleanup:
        file.delete()
    }

    void "test when an attached file is returned with a name"() {
        when:
        def response = httpClient.toBlocking().exchange('/test/different-name', String)

        then: "the content type is still based on the file extension"
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"abc.xyz\"")
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test when a system file is returned with a name"() {
        when:
        def response = httpClient.toBlocking().exchange('/test-system/different-name', String)

        then: "the content type is still based on the file extension"
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"abc.xyz\"")
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test the content type is honored when an attached file response is returned"() {
        when:
        def response = httpClient.toBlocking().exchange('/test/custom-content-type', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"temp.html\"")
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test the content type is honored when a system file response is returned"() {
        when:
        def response = httpClient.toBlocking().exchange('/test-system/custom-content-type', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"temp.html\"")
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test the content type is honored when a streamed file response is returned"() {
        when:
        def response = httpClient.toBlocking().exchange('/test-stream/custom-content-type', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"temp.html\"")
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == tempFileContents
    }

    void "test using a piped stream"() {
        when:
        def response = httpClient.toBlocking().exchange('/test-stream/piped-stream', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.body() == ("a".."z").join('')
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

        @Get('/reactive-stream')
        Mono<FileCustomizableResponseType> reactiveStream() {
            def stream = new ByteArrayInputStream("My file content".bytes)
            Mono.just(new StreamedFile(stream, MediaType.TEXT_PLAIN_TYPE))
        }

        @Get('/reactive-system')
        Mono<FileCustomizableResponseType> reactiveStream(@QueryValue Path path) {
            Mono.just(new SystemFile(path.toFile(), MediaType.TEXT_PLAIN_TYPE))
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
