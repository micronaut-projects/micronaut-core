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

package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RequestAttributeSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test send and receive request attribute"() {
        given:
        StoryClient storyClient = context.getBean(StoryClient)
        Story story = storyClient.get()

        expect:
        story.storyId == "ABC123"
        story.title == "The Hungry Caterpillar"
    }

    @Client('/request_attribute')
    static interface StoryClient {
        @Get('/story')
        Story get()
    }

    @Controller('/request_attribute')
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
    static class ServerFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            request.setAttribute("x-story-id", "ABC123")
            return chain.proceed(request)
        }
    }
}
