package io.micronaut.http.client.jdk

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import spock.lang.Specification

@MicronautTest
@Property(name = 'spec.name', value = 'RequestAttributeSpec')
class RequestAttributeSpec extends Specification {

    @Inject
    StoryClient storyClient

    @Inject
    ReceiveClient receiveClient

    void "test send and receive request attribute"() {
        given:
        Story story = storyClient.get()

        expect:
        story.storyId == "ABC123"
        story.title == "The Hungry Caterpillar"
    }

    def 'request attributes should not be forwarded'() {
        when:
        def uri = receiveClient.get('foo')
        then:
        uri.path == '/request_attribute_not_forwarded'
        uri.query == null
    }

    @Client('/request_attribute')
    @Requires(property = 'spec.name', value = 'RequestAttributeSpec')
    static interface StoryClient {
        @Get('/story')
        Story get()
    }

    @Controller('/request_attribute')
    @Requires(property = 'spec.name', value = 'RequestAttributeSpec')
    static class StoryController {

        @Get("/story")
        Story get(@RequestAttribute("x-story-id") Object storyId) {
            return new Story(storyId: storyId, title: 'The Hungry Caterpillar')
        }
    }

    static class Story {
        String storyId
        String title
    }

    @Filter("/**")
    @Requires(property = 'spec.name', value = 'RequestAttributeSpec')
    static class ServerFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            request.setAttribute("x-story-id", "ABC123")
            return chain.proceed(request)
        }
    }

    @Client('/request_attribute_not_forwarded')
    @Requires(property = 'spec.name', value = 'RequestAttributeSpec')
    static interface ReceiveClient {
        @Get
        URI get(@RequestAttribute("example") String value)
    }

    @Controller('/request_attribute_not_forwarded')
    @Requires(property = 'spec.name', value = 'RequestAttributeSpec')
    static class ReceiveController {
        @Get
        URI get(HttpRequest<?> request) {
            return request.uri
        }
    }
}
