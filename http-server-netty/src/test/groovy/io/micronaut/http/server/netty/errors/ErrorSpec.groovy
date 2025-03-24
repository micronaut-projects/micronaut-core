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
package io.micronaut.http.server.netty.errors

import groovy.json.JsonSlurper
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.codec.CodecException
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseDecoder
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Timeout

import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Tests for different kinds of errors and the expected responses
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ErrorSpec extends AbstractMicronautSpec {

    static final String SPEC = "ErrorSpec"

    void "test 500 server error"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/server-error')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: bad'
    }

    void "test 500 server error IOException"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/io-error')

        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: null'
    }

    void "test an error route throwing the same exception it handles"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/loop')

        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: null'
    }

    void "test an exception handler throwing the same exception it handles"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/loop/handler')

        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message == 'Internal Server Error: null'
    }

    void "test 404 error"() {
        when:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/blah')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        then:
        response.code() == HttpStatus.NOT_FOUND.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json._embedded.errors[0].message == 'Page Not Found'
        json._links.self.href == '/errors/blah'
    }

    void "test 405 error"() {
        when:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.POST('/errors/server-error', 'blah')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        then:
        response.code() == HttpStatus.METHOD_NOT_ALLOWED.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON

        when:
        def json = new JsonSlurper().parseText(response.getBody(String).orElse(null))

        then:
        json._embedded.errors[0].message.matches('Method \\[POST\\] not allowed for URI \\[/errors/server-error\\]. Allowed methods: \\[(GET|HEAD), (GET|HEAD)\\]')
        json._links.self.href == '/errors/server-error'
    }

    void "test content type for error handler"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/handler-content-type-error')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.TEXT_HTML
        response.getBody(String).get() == '<div>Error</div>'
    }

    void "test encoding error"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/encoding-error')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message.contains('foo')
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/7786')
    void "test encoding error with handler"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/encoding-error/handled')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message.contains('foo')
    }

    void "test encoding error with handler loop"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/encoding-error/handled/loop')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
        response.getBody(Map).get()._embedded.errors[0].message.contains('foo')
    }

    void "test calling a controller that fails to inject with a local error handler"() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/injection')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.getBody(Map).get()._embedded.errors[0].message.contains("Failed to inject value for parameter [prop]")
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6526')
    def 'test bad request from incorrect use of array'() {
        when:
        httpClient.toBlocking().exchange(HttpRequest.POST('/errors/feedBirds', '[ { "name": "eagle" }, { "name": "hen" } ]').contentType(MediaType.APPLICATION_JSON))
        then:
        def e = thrown HttpClientResponseException
        e.status == HttpStatus.BAD_REQUEST
    }

    @Timeout(5)
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6925')
    void "test error for invalid headers with body"() {
        given:
        def eventLoopGroup = new NioEventLoopGroup(1)
        FullHttpResponse response = null
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpResponseDecoder())
                                .addLast(new HttpObjectAggregator(8192))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                        response = msg
                                    }
                                })
                    }
                })
                .remoteAddress(embeddedServer.host, embeddedServer.port)
        def longString = 'a' * 9000

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.writeAndFlush(Unpooled.wrappedBuffer("""POST /test HTTP/1.0\r
Host: localhost\r
Connection: close\r
Content-Length: 26\r
Accept: */*\r
X-Long-Header: $longString\r
\r
{"message":"Hello World!"}""".getBytes(StandardCharsets.UTF_8)))
        channel.read()
        channel.closeFuture().await()

        then:
        response.status().code() == 413

        cleanup:
        response.release()
    }

    def 'missing writer'() {
        given:
        HttpResponse response = Flux.from(httpClient.exchange(
                HttpRequest.GET('/errors/media-type')
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        expect:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        response.header(HttpHeaders.CONTENT_TYPE) == MediaType.APPLICATION_JSON
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11607")
    def 'text-plain with invalid json body'() {
        when:
        httpClient.toBlocking().retrieve(HttpRequest.POST("/errors/plain-json", '{"en": "BAR"}').contentType(MediaType.TEXT_PLAIN))

        then:
        def e = thrown HttpClientResponseException
        e.status == HttpStatus.BAD_REQUEST
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors')
    static class ErrorController {

        @Get('/server-error')
        String serverError() {
            throw new RuntimeException("bad")
        }

        @Get("/io-error")
        @SingleResult
        Publisher<String> ioError() {
            return Mono.create({ emitter ->
                emitter.error(new IOException())
            })
        }

        @Get("/handler-content-type-error")
        String handlerContentTypeError() {
            throw new ContentTypeExceptionHandlerException()
        }

        @Post(value = "/feedBirds", processes = MediaType.APPLICATION_JSON)
        void feedBirds(Flock flock) {
        }

        @Get('/encoding-error')
        ErrorThrowingBean encodingError() {
            return new ErrorThrowingBean()
        }
    }

    static class Flock {
        private Bird[] birds;

        public Bird[] getBirds() { return birds; }

        public void setBirds(Bird[] birds) { this.birds = birds; }
    }

    static class Bird {
        private String name;

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }
    }

    static class ErrorThrowingBean {
        public String getName() {
            throw new RuntimeException("foo")
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors/loop')
    static class ErrorLoopController {

        @Get()
        String serverError() {
            throw new LoopingException()
        }

        @Error(LoopingException)
        String loop() {
            throw new LoopingException()
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors/loop/handler')
    static class ErrorLoopHandlerController {

        @Get()
        String serverError() {
            throw new LoopingException()
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors/injection')
    static class ErrorInjectionController {

        ErrorInjectionController(@Property(name = "does.not.exist") String prop) {}

        @Get
        String ok() {
            "OK"
        }

        @Error
        HttpResponse<String> error(HttpRequest<?> request, Throwable e) {
            HttpResponse.serverError("Server error")
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors/encoding-error/handled')
    static class ErrorEncodingHandlerController {
        @Get
        ErrorThrowingBean ok() {
            return new ErrorThrowingBean()
        }

        @Error
        HttpResponse<String> error(HttpRequest<?> request, Throwable e) {
            HttpResponse.serverError("Handled server error")
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors/encoding-error/handled/loop')
    static class ErrorEncodingHandlerLoopController {
        @Get
        ErrorThrowingBean ok() {
            return new ErrorThrowingBean()
        }

        @Error
        ErrorThrowingBean error(HttpRequest<?> request, Throwable e) {
            return new ErrorThrowingBean()
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors/media-type')
    static class MediaTypeErrorController {
        @Get
        HttpResponse<Instant> ok() {
            return HttpResponse.ok(Instant.now()).contentType("my/mediatype")
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    @Produces("my/mediatype")
    static class ThrowingWriter implements MessageBodyWriter<Instant> {
        @Override
        void writeTo(@NonNull Argument<Instant> type, @NonNull MediaType mediaType, Instant object, @NonNull MutableHeaders outgoingHeaders, @NonNull OutputStream outputStream) throws CodecException {
            throw new RuntimeException("foo")
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Produces(value = MediaType.TEXT_HTML)
    @Singleton
    static class ContentTypeExceptionHandler implements ExceptionHandler<ContentTypeExceptionHandlerException, HttpResponse<String>> {

        @Override
        HttpResponse<String> handle(HttpRequest r, ContentTypeExceptionHandlerException exception) {
            HttpResponse.serverError("<div>Error</div>")
        }
    }

    static class ContentTypeExceptionHandlerException extends RuntimeException {}

    static class LoopingException extends RuntimeException {}

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    static class LoopingExceptionHandler implements ExceptionHandler<LoopingException, String> {
        @Override
        String handle(HttpRequest request, LoopingException exception) {
            throw new LoopingException()
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/errors/plain-json')
    static class PlainJsonError {

        @Post(processes = MediaType.TEXT_PLAIN)
        String ok(@Body MyRecord rec) {
            rec.toString()
        }

        record MyRecord(MyEnum en) {}

        enum MyEnum {
            FOO
        }
    }
}
