package org.particleframework.http.server.netty.resources

import okhttp3.Request
import org.particleframework.http.HttpStatus
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.server.netty.types.files.FileTypeHandlerConfiguration

import java.text.SimpleDateFormat

import static org.particleframework.http.HttpHeaders.CACHE_CONTROL
import static org.particleframework.http.HttpHeaders.CONTENT_LENGTH
import static org.particleframework.http.HttpHeaders.CONTENT_TYPE
import static org.particleframework.http.HttpHeaders.DATE
import static org.particleframework.http.HttpHeaders.EXPIRES
import static org.particleframework.http.HttpHeaders.LAST_MODIFIED

class StaticResourceResolutionSpec extends AbstractParticleSpec {

    private static File tempFile

    static {
        tempFile = File.createTempFile("staticResourceResolutionSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page from static file</body></html>")
        tempFile
    }

    Map<String, Object> getConfiguration() {
        ['router.static.resources.paths': ['classpath:', 'file:' + tempFile.parent], 'router.static.resources.enabled': true]
    }

    void "test resources from the file system are returned"() {
        when:
        def request = new Request.Builder()
                .url("$server/" + tempFile.getName())
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
        response.body().string() == "<html><head></head><body>HTML Page from static file</body></html>"
    }

    void "test resources from the classpath are returned"() {
        when:
        def request = new Request.Builder()
                .url("$server/index.html")
                .get()

        def response = client.newCall(
                request.build()
        ).execute()
        FileTypeHandlerConfiguration config = new FileTypeHandlerConfiguration()
        SimpleDateFormat dateFormat = new SimpleDateFormat(config.dateFormat)
        dateFormat.timeZone = config.dateTimeZone
        File file = new File(this.getClass().getClassLoader().getResource("index.html").path)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        dateFormat.parse(response.header(DATE)) < dateFormat.parse(response.header(EXPIRES))
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.header(LAST_MODIFIED) == dateFormat.format(new Date(file.lastModified()))
        response.body().string() == "<html><head></head><body>HTML Page from resources</body></html>"
    }
}
