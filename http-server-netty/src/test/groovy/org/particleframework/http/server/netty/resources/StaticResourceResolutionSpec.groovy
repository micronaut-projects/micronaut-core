package org.particleframework.http.server.netty.resources

import okhttp3.OkHttpClient
import okhttp3.Request
import org.particleframework.http.HttpRequest
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpStatus
import org.particleframework.http.client.RxHttpClient
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.server.netty.types.files.FileTypeHandlerConfiguration
import org.particleframework.runtime.server.EmbeddedServer

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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

    void cleanupSpec() {
        tempFile.delete()
    }

    void "test resources from the file system are returned"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/'+tempFile.getName()), String
        ).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page from static file</body></html>"
    }

    void "test resources from the classpath are returned"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/index.html'), String
        ).blockingFirst()
        File file = new File(this.getClass().getClassLoader().getResource("index.html").path)

        then:
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"
    }

    void "test resources with configured mapping"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['router.static.resources.paths': ['classpath:', 'file:' + tempFile.parent], 'router.static.resources.enabled': true, 'router.static.resources.mapping': '/static/**'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        ).blockingFirst()
        File file = new File(this.getClass().getClassLoader().getResource("index.html").path)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"

        cleanup:
        embeddedServer.stop()
    }
}
