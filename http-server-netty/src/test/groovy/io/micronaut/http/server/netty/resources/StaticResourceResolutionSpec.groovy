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
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.resource.StaticResourceConfiguration
import reactor.core.publisher.Flux

import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import static io.micronaut.http.HttpHeaders.*

class StaticResourceResolutionSpec extends AbstractMicronautSpec {

    static File tempFile
    static File tempSubDir

    static {
        tempFile = File.createTempFile("staticResourceResolutionSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page from static file</body></html>")
        tempSubDir = new File(tempFile.getParentFile(), "doesntexist")
    }

    @Override
    Map<String, Object> getConfiguration() {
        ['micronaut.router.static-resources.default.paths': ['classpath:public', 'file:' + tempFile.parent, 'file:' + tempSubDir.absolutePath]]
    }

    @Override
    void cleanupSpec() {
        tempFile.delete()
    }

    void "test resources from the file system are returned"() {
        when:
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/'+tempFile.getName()), String
        )).blockFirst()

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
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/index.html'), String
        )).blockFirst()

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
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/'), String
        )).blockFirst()

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

    void "test creating the directory and adding a file after start can still resolve"() {
        when:
        tempSubDir.mkdir()
        File file = new File(tempSubDir, "nowexists.html")
        file.write("<html><head></head><body>HTML Page created after start</body></html>")

        def response = rxClient.toBlocking().exchange(
                HttpRequest.GET('/nowexists.html'), String)

        then:
        file.exists()
        response.status == HttpStatus.OK
        response.header(CONTENT_TYPE) == "text/html"
        Integer.parseInt(response.header(CONTENT_LENGTH)) > 0
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == "<html><head></head><body>HTML Page created after start</body></html>"
    }

    void "test resources with configured mapping"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.default.paths': ['classpath:public', 'file:' + tempFile.parent],
                'micronaut.router.static-resources.default.mapping': '/static/**'], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        )).blockFirst()
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
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        expect:
        embeddedServer.applicationContext.getBeansOfType(StaticResourceConfiguration).size() == 2

        when:
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        )).blockFirst()
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
        response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/file/'+tempFile.getName()), String
        )).blockFirst()

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
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        expect:
        embeddedServer.applicationContext.getBeansOfType(StaticResourceConfiguration).size() == 2

        when:
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET("/static/index.html"), String
        )).blockFirst()
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
        response = Flux.from(rxClient.exchange(
                HttpRequest.GET('/file/'+tempFile.getName()), String
        )).blockFirst()

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
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())


        when:
        def response = Flux.from(rxClient.exchange(
                HttpRequest.GET("/static"), String
        )).blockFirst()
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
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET("/static/foo"), String
        )).blockFirst()
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

    void "test resolving a file with special characters"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.router.static-resources.default.paths': ['classpath:public'],
                'micronaut.router.static-resources.default.mapping': '/static/**'], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.GET("/static/%E6%B5%8B%E8%AF%95-0.0.yml"), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == """openapi: 3.0.1
info:
  title: 测试
  version: "0.0"
"""

        cleanup:
        embeddedServer.close()
    }

    void 'test static file on same path as controller'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'StaticResourceResolutionSpec.test static file on same path as controller',
                'micronaut.router.static-resources.default.paths': ['classpath:public'],
                'micronaut.router.static-resources.default.mapping': '/static/**'], Environment.TEST)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def staticResponse = client.toBlocking().exchange(HttpRequest.GET('/static/index.html'), String)
        then:
        staticResponse.status() == HttpStatus.OK
        staticResponse.body().startsWith('<html>')

        when:
        def dynamicResponse = client.toBlocking().exchange(HttpRequest.POST('/static/index.html', 'foo'), String)
        then:
        dynamicResponse.status() == HttpStatus.OK
        dynamicResponse.body() == 'foo'

        cleanup:
        embeddedServer.close()
    }

}
