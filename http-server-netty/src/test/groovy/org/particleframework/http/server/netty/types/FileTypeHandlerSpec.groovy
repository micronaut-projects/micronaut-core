package org.particleframework.http.server.netty.types

import okhttp3.Request
import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Controller
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.server.netty.types.impl.FileTypeHandlerConfiguration
import org.particleframework.web.router.annotation.Get

import java.text.SimpleDateFormat
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
        def request = new Request.Builder()
                .url("$server/test/html")
                .get()

        def response = client.newCall(
                request.build()
        ).execute()
        FileTypeHandlerConfiguration config = new FileTypeHandlerConfiguration()
        SimpleDateFormat dateFormat = new SimpleDateFormat(config.dateFormat)
        dateFormat.timeZone = config.dateTimeZone

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        dateFormat.parse(response.header(DATE)) < dateFormat.parse(response.header(EXPIRES))
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.header(LAST_MODIFIED) == dateFormat.format(new Date(tempFile.lastModified()))
        response.body().string() == "<html><head></head><body>HTML Page</body></html>"
    }

    void "test 304 is returned if the correct header is sent"() {
        when:
        FileTypeHandlerConfiguration config = new FileTypeHandlerConfiguration()
        SimpleDateFormat dateFormat = new SimpleDateFormat(config.dateFormat)
        dateFormat.timeZone = config.dateTimeZone
        def request = new Request.Builder()
                .url("$server/test/html")
                .header(IF_MODIFIED_SINCE, dateFormat.format(new Date(tempFile.lastModified())))
                .get()

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.NOT_MODIFIED.code
        response.header(DATE)
    }

    void "test what happens when a file isn't found"() {
        when:
        def request = new Request.Builder()
                .url("$server/test/notFound")
                .get()

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.body().string() == '{"message":"Internal Server Error: Could not find file"}'
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
    }
}
