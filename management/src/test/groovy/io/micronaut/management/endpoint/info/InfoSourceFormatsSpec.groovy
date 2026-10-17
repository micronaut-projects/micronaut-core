package io.micronaut.management.endpoint.info

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.management.endpoint.info.source.BuildInfoSource
import io.micronaut.management.endpoint.info.source.GitInfoSource
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

class InfoSourceFormatsSpec extends Specification {

    private static final String MESSAGE = 'héllo ✓'

    void "the info endpoint exposes git info read from a JSON file"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'endpoints.info.git.location': 'git.json'], Environment.TEST)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = client.exchange(HttpRequest.GET("/info"), Map).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().git.branch == 'main'
        response.body().git.commit.id == 'abc'
        response.body().git.commit.message.short == MESSAGE

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "nested JSON objects are flattened to the keys of a git.properties file"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.info.git.location': 'git.json'])

        when:
        def source = Mono.from(context.getBean(GitInfoSource).source).block()

        then:
        source.get('git.branch') == 'main'
        source.get('git.commit.id') == 'abc'
        source.get('git.commit.message.short') == MESSAGE

        cleanup:
        context.close()
    }

    void "git info can be read from YAML and from a location with the classpath prefix"(String location) {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.info.git.location': location])

        when:
        def source = Mono.from(context.getBean(GitInfoSource).source).block()

        then:
        source.get('git.branch') == 'yaml-branch'
        source.get('git.commit.message.short') == MESSAGE

        cleanup:
        context.close()

        where:
        location << ['git.yml', 'classpath:git.yml']
    }

    void "the build info source reads other formats too"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.info.build.location': 'build.json'])

        when:
        def source = Mono.from(context.getBean(BuildInfoSource).source).block()

        then:
        source.get('build.artifact') == 'json-artifact'
        source.get('build.name') == 'json-name'

        cleanup:
        context.close()
    }

    void "a location with an extension no property source loader supports yields no info"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'endpoints.info.git.location': 'git.txt'], Environment.TEST)
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = client.exchange(HttpRequest.GET("/info"), Map).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        !response.body().containsKey('git')

        and:
        Mono.from(embeddedServer.applicationContext.getBean(GitInfoSource).source).block() == null

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "a missing file in a supported format yields no info"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.info.git.location': 'missing-git.json'])

        expect:
        Mono.from(context.getBean(GitInfoSource).source).block() == null

        cleanup:
        context.close()
    }

    void "sources created without an environment only read properties files"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        def resolver = context.getBean(io.micronaut.core.io.ResourceResolver)

        expect:
        Mono.from(new GitInfoSource(resolver, 'git.properties').source).block().get('git.branch') == 'master'
        Mono.from(new GitInfoSource(resolver, 'git.json').source).block() == null
        Mono.from(new GitInfoSource(resolver, 'git.json', context.environment).source).block().get('git.branch') == 'main'

        cleanup:
        context.close()
    }
}
