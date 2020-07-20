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
package io.micronaut.http.server.netty.resources

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.resource.StaticResourceConfiguration
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import static io.micronaut.http.HttpHeaders.*

class StaticResourceResolutionSpec extends AbstractMicronautSpec {

    private static File tempFile

    static {
        tempFile = File.createTempFile("staticResourceResolutionSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page from static file</body></html>")
        tempFile
    }

    @Override
    Map<String, Object> getConfiguration() {
        ['micronaut.router.static-resources.default.paths': ['classpath:public', 'file:' + tempFile.parent]]
    }

    @Override
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
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from static file</body></html>"
    }

    void "test resources from the classpath are returned"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/index.html'), String
        ).blockingFirst()

        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("public/index.html").toURI()).toFile()

        then:
        file.exists()
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"
    }

    void "test index.html will be resolved"() {
        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/'), String
        ).blockingFirst()

        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("public/index.html").toURI()).toFile()

        then:
        file.exists()
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"
    }

    void "test resources with configured mapping"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.default.paths': ['classpath:public', 'file:' + tempFile.parent],
                'micronaut.router.static-resources.default.mapping': '/static/**'], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("public/index.html").toURI()).toFile()

        then:
        file.exists()
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"

        cleanup:
        embeddedServer.stop()
    }

    void "test resources with multiple configured mappings"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.cp.paths': ['classpath:public', 'file:' + tempFile.parent],
                'micronaut.router.static-resources.cp.mapping': '/static/**',
                'micronaut.router.static-resources.file.paths': ['file:' + tempFile.parent],
                'micronaut.router.static-resources.file.mapping': '/file/**'], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        expect:
        embeddedServer.applicationContext.getBeansOfType(StaticResourceConfiguration).size() == 2

        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("public/index.html").toURI()).toFile()

        then:
        file.exists()
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"

        when:
        response = rxClient.exchange(
                HttpRequest.GET('/file/'+tempFile.getName()), String
        ).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from static file</body></html>"

        cleanup:
        embeddedServer.stop()
    }

    void "test resources with multiple configured mappings and one is disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.cp.paths': ['classpath:public', 'file:' + tempFile.parent],
                'micronaut.router.static-resources.cp.mapping': '/static/**',
                'micronaut.router.static-resources.file.paths': ['file:' + tempFile.parent],
                'micronaut.router.static-resources.file.enabled': false,
                'micronaut.router.static-resources.file.mapping': '/file/**'], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        expect:
        embeddedServer.applicationContext.getBeansOfType(StaticResourceConfiguration).size() == 2

        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("public/index.html").toURI()).toFile()

        then:
        file.exists()
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"

        when:
        response = rxClient.exchange(
                HttpRequest.GET('/file/'+tempFile.getName()), String
        ).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        cleanup:
        embeddedServer.stop()
    }

    void "test resources with configured mapping automatically resolves index.html"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.default.paths': ['classpath:public', 'file:' + tempFile.parent],
                'micronaut.router.static-resources.default.mapping': '/static/**'], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("public/index.html").toURI()).toFile()

        then:
        file.exists()
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from resources</body></html>"

        cleanup:
        embeddedServer.stop()
        embeddedServer.close()
    }

    void "test resources with configured mapping automatically resolves index.html in path"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.default.paths': ['classpath:public'],
                'micronaut.router.static-resources.default.mapping': '/static/**'], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


        when:
        def response = rxClient.exchange(
                HttpRequest.GET("/static/foo"), String
        ).blockingFirst()
        File file = Paths.get(StaticResourceResolutionSpec.classLoader.getResource("public/foo/index.html").toURI()).toFile()

        then:
        file.exists()
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page from resources/foo</body></html>"

        cleanup:
        embeddedServer.stop()
        embeddedServer.close()
    }

    void "test its not possible to configure a path with 'classpath:'"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.default.paths': ['classpath:'],
                'micronaut.router.static-resources.default.mapping': '/static/**'], Environment.TEST)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains("A path value of [classpath:] will allow access to class files!")

        cleanup:
        embeddedServer?.stop()
        embeddedServer?.close()
    }

}
