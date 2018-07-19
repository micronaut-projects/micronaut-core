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
package io.micronaut.http.server.netty.resources

import io.micronaut.http.HttpRequest
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Ignore

import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

import static io.micronaut.http.HttpHeaders.CACHE_CONTROL
import static io.micronaut.http.HttpHeaders.CONTENT_LENGTH
import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.HttpHeaders.DATE
import static io.micronaut.http.HttpHeaders.EXPIRES
import static io.micronaut.http.HttpHeaders.LAST_MODIFIED

class StaticResourceResolutionSpec extends AbstractMicronautSpec {

    private static File tempFile

    static {
        tempFile = File.createTempFile("staticResourceResolutionSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page from static file</body></html>")
        tempFile
    }

    Map<String, Object> getConfiguration() {
        ['micronaut.router.static.resources.paths':
                 ['classpath:', 'file:' + tempFile.parent],
         'micronaut.router.static.resources.enabled': true]
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

        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("index.html").toURI()).toFile()

        then:
        file.exists()
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"
    }

    void "test index.html will be resolved"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/'), String
        ).blockingFirst()

        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("index.html").toURI()).toFile()

        then:
        file.exists()
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
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static.resources.paths': ['classpath:', 'file:' + tempFile.parent],
                'micronaut.router.static.resources.enabled': true,
                'micronaut.router.static.resources.mapping': '/static/**'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("index.html").toURI()).toFile()

        then:
        file.exists()
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

    void "test resources with configured mapping automatically resolves index.html"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static.resources.paths': ['classpath:', 'file:' + tempFile.parent],
                'micronaut.router.static.resources.enabled': true,
                'micronaut.router.static.resources.mapping': '/static/**'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("index.html").toURI()).toFile()

        then:
        file.exists()
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

    void "test resources with configured mapping automatically resolves index.html in path"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static.resources.paths': ['classpath:foo'],
                'micronaut.router.static.resources.enabled': true,
                'micronaut.router.static.resources.mapping': '/static/**'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("index.html").toURI()).toFile()

        then:
        file.exists()
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT") )
        response.body() == "<html><head></head><body>HTML Page from resources/foo</body></html>"

        cleanup:
        embeddedServer.stop()
    }
}
