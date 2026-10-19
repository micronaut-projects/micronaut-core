package io.micronaut.http.server.netty.resources

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StaticResourceJarDirectorySpec extends Specification {

    @Shared
    @TempDir
    Path tempDir

    @Shared
    @AutoCleanup
    URLClassLoader classLoader

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    @AutoCleanup
    HttpClient client

    void setupSpec() {
        Path jar = tempDir.resolve('static.jar')
        Files.newOutputStream(jar).withCloseable { out ->
            new ZipOutputStream(out).withCloseable { zip ->
                for (String directory : ['META-INF/', 'jar-public/', 'jar-public/docs/', 'jar-public/empty/']) {
                    zip.putNextEntry(new ZipEntry(directory))
                    zip.closeEntry()
                }
                for (String file : ['jar-public/site.css', 'jar-public/docs/index.html']) {
                    zip.putNextEntry(new ZipEntry(file))
                    zip.write(file.getBytes(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
        classLoader = new URLClassLoader([jar.toUri().toURL()] as URL[], StaticResourceJarDirectorySpec.classLoader)
        context = ApplicationContext.builder()
                .classLoader(classLoader)
                .properties('micronaut.router.static-resources.default.paths': ['classpath:jar-public'])
                .start()
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, server.URI)
    }

    void 'a file of a jar under the base path is served'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/site.css'), String)

        then:
        response.status == HttpStatus.OK
        response.body() == 'jar-public/site.css'
    }

    void 'a directory of a jar under the base path serves its index page'() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/docs'), String)

        then:
        response.status == HttpStatus.OK
        response.body() == 'jar-public/docs/index.html'
    }

    void 'a directory of a jar under the base path without an index page is not found'() {
        when:
        client.toBlocking().exchange(HttpRequest.GET('/empty'), String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }
}
