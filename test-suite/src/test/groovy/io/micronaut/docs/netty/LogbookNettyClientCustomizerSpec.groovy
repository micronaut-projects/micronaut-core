package io.micronaut.docs.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.zalando.logbook.HttpRequest
import org.zalando.logbook.HttpResponse
import org.zalando.logbook.Logbook
import org.zalando.logbook.Origin
import org.zalando.logbook.Strategy
import spock.lang.Specification

class LogbookNettyClientCustomizerSpec extends Specification {
    def 'plaintext http 1'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'LogbookNettyClientCustomizerSpec'])
        ctx.getBean(Logbook)
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        def client = ctx.createBean(HttpClient, embeddedServer.URI).toBlocking()

        when:
        def response = client.exchange(io.micronaut.http.HttpRequest.POST("/logbook/logged", 'foo'), String)

        then:
        response.status() == HttpStatus.OK
        response.body() == 'foo'

        ctx.getBean(LogbookFactory).log == [
                'POST /logbook/logged',
                'foo',
                '200',
                'foo',
        ]

        cleanup:
        embeddedServer.stop()
    }

    def 'tls alpn http 2'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.http-version'       : '2.0',
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.ssl.port':            0,
                'micronaut.http.client.http-version'  : '2.0',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name'                           : 'LogbookNettyClientCustomizerSpec'
        ])

        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        def client = ctx.createBean(HttpClient, embeddedServer.URI).toBlocking()

        when:
        def response = client.exchange(io.micronaut.http.HttpRequest.POST("/logbook/logged", 'foo'), String)

        then:
        response.status() == HttpStatus.OK
        response.body() == 'foo'

        ctx.getBean(LogbookFactory).log == [
                'POST /logbook/logged',
                'foo',
                '200',
                'foo',
        ]

        cleanup:
        embeddedServer.stop()
    }

    def 'tls alpn http 1'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.ssl.port':            0,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name'                           : 'LogbookNettyClientCustomizerSpec'
        ])

        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        def client = ctx.createBean(HttpClient, embeddedServer.URI).toBlocking()

        when:
        def response = client.exchange(io.micronaut.http.HttpRequest.POST("/logbook/logged", 'foo'), String)

        then:
        response.status() == HttpStatus.OK
        response.body() == 'foo'

        ctx.getBean(LogbookFactory).log == [
                'POST /logbook/logged',
                'foo',
                '200',
                'foo',
        ]
    }

    def 'h2c with upgrade'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.http-version': '2.0',
                'spec.name'                    : 'LogbookNettyClientCustomizerSpec'
        ])

        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        def client = ctx.createBean(HttpClient, embeddedServer.URI).toBlocking()

        when:
        def response1 = client.exchange(io.micronaut.http.HttpRequest.GET("/logbook/logged"), String)

        then:
        response1.status() == HttpStatus.OK
        response1.body() == 'hello'

        when:
        def response2 = client.exchange(io.micronaut.http.HttpRequest.POST("/logbook/logged", "bar"), String)

        then:
        response2.status() == HttpStatus.OK
        response2.body() == 'bar'

        ctx.getBean(LogbookFactory).log == [
                'GET /logbook/logged',
                '',
                '200',
                'hello',
                'POST /logbook/logged',
                'bar',
                '200',
                'bar',
        ]
    }

    @Controller("/logbook/logged")
    @Requires(property = 'spec.name', value = 'LogbookNettyClientCustomizerSpec')
    static class LoggedController {
        @Get("/")
        @Produces(MediaType.TEXT_PLAIN)
        String index() {
            return "hello"
        }

        @Post("/")
        @Produces(MediaType.TEXT_PLAIN)
        String index(@Body String body) {
            return body
        }
    }

    @Requires(property = 'spec.name', value = 'LogbookNettyClientCustomizerSpec')
    @Factory
    static class LogbookFactory {
        URI expectedRemote
        List<String> log = new ArrayList<>()

        @Bean
        @Singleton
        Logbook logbook() {
            return new Logbook() {
                @Override
                Logbook.RequestWritingStage process(HttpRequest request) throws IOException {
                    // ignore server messages
                    if (request.origin == Origin.REMOTE) {
                        return new Logbook.RequestWritingStage() {
                            @Override
                            Logbook.ResponseProcessingStage write() throws IOException {
                                return this
                            }

                            @Override
                            Logbook.ResponseWritingStage process(HttpResponse response) throws IOException {
                                return new Logbook.ResponseWritingStage() {
                                    @Override
                                    void write() throws IOException {
                                    }
                                }
                            }
                        }
                    }

                    log.add(request.getMethod() + ' ' + request.getPath())
                    request = request.withBody()
                    return new Logbook.RequestWritingStage() {
                        @Override
                        Logbook.ResponseProcessingStage write() throws IOException {
                            return this
                        }

                        @Override
                        Logbook.ResponseWritingStage process(HttpResponse response) throws IOException {
                            log.add(request.getBodyAsString())
                            log.add(response.getStatus().toString())

                            response = response.withBody()
                            return new Logbook.ResponseWritingStage() {
                                @Override
                                void write() throws IOException {
                                    log.add(response.getBodyAsString())
                                }
                            }
                        }
                    }
                }

                @Override
                Logbook.RequestWritingStage process(HttpRequest request, Strategy strategy) throws IOException {
                    return process(request)
                }
            }
        }
    }
}
