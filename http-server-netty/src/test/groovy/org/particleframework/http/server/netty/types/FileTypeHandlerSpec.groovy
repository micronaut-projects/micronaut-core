package org.particleframework.http.server.netty.types

import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpStatus
import org.particleframework.http.MutableHttpRequest
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.client.exceptions.HttpClientResponseException
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.server.netty.types.files.FileTypeHandler
import org.particleframework.http.server.netty.types.files.FileTypeHandlerConfiguration
import org.particleframework.http.server.netty.types.files.NettyStreamedFileCustomizableResponseType
import org.particleframework.http.server.netty.types.files.NettySystemFileCustomizableResponseType
import org.particleframework.http.server.types.files.AttachedFile
import org.particleframework.http.server.types.files.SystemFileCustomizableResponseType

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

import static org.particleframework.http.HttpHeaders.*

class FileTypeHandlerSpec extends AbstractParticleSpec {

    private static File tempFile

    static {
        tempFile = File.createTempFile("fileTypeHandlerSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page</body></html>")
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
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page</body></html>"
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
        rxClient.exchange('/test/notFound', String).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.body() == '{"_links":{},"_embedded":{},"message":"Internal Server Error: Could not find file"}'
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
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page</body></html>"
    }

    void "test when an attached file is returned with a name"() {
        when:
        def response = rxClient.exchange('/test/differentName', String).blockingFirst()

        then: "the content type is still based on the file extension"
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION) == "attachment; filename=\"abc.xyz\""
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page</body></html>"
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
        File                                      | true
        AttachedFile                              | true
    }

    @Controller
    @Requires(property = 'spec.name', value = 'FileTypeHandlerSpec')
    static class TestController {

        @Get
        File html() {
            tempFile
        }

        @Get
        File notFound() {
            new File('/xyzabc')
        }

        @Get
        AttachedFile download() {
            new AttachedFile(tempFile)
        }

        @Get
        AttachedFile differentName() {
            new AttachedFile(tempFile, "abc.xyz")
        }
    }
}
