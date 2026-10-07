package io.micronaut.docs.server.functional

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

class StaticResourceRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            "spec.name"     : "StaticResourceRoutesSpec",
            "site.directory": "src/test/resources/functional-static/site"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the routes serve static resources"() {
        when:
        HttpResponse<String> css = client.toBlocking().exchange(HttpRequest.GET("/assets/css/site.css"), String)

        then:
        css.status == HttpStatus.OK
        css.body() == "body { color: teal; }\n"
        css.contentType.get().name == "text/css"
        css.headers.get("X-Assets") == "true"
        css.headers.get("Cache-Control") == "public, max-age=31536000, immutable"

        when:
        HttpResponse<String> hello = client.toBlocking().exchange(HttpRequest.GET("/site/hello.txt"), String)

        then:
        hello.status == HttpStatus.OK
        hello.body() == "Hello from the file system\n"
        hello.contentType.get().name == "text/plain"

        when: "the index file at the prefix of the group, with the content type of the file, not of the group"
        HttpResponse<String> manual = client.toBlocking().exchange(HttpRequest.GET("/manual"), String)

        then:
        manual.status == HttpStatus.OK
        manual.body() == "<h1>Manual</h1>\n"
        manual.contentType.get().name == "text/html"
        manual.headers.get("X-Manual") == "true"
    }

    void "a missing file and a path that leaves the directory are not found: #path"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET(path), String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND

        where:
        path << ["/assets/missing.css", "/assets/../secret.txt", "/assets/%2e%2e/secret.txt",
                 "/assets/..%2fsecret.txt", "/site/..%2f..%2fsecret.txt"]
    }
}
