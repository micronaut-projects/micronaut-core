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
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.annotation.RequestAttributes
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RequestAttributesSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test send and receive header"() {
        given:
        StoryClient storyClient = context.getBean(StoryClient)
        Story story = storyClient.get()

        expect:
        story.title == 'The Ugly Duckling'
        story.releaseDate == 'Nov 11, 1843'
    }

    @RequestAttributes([
        @RequestAttribute(name='story-title',value='The Ugly Duckling'),
        @RequestAttribute(name='release-date',value='Nov 11, 1843')
    ])
    @Client('/request_attributes')
    static interface StoryClient extends StoryApi {

    }

    @Controller('/request_attributes')
    static class StoryController{

        @Get('/story')
        Story get(@RequestAttribute('story-title') String storyTitle, @RequestAttribute('release-date') String releaseDate) {
            return new Story(title:storyTitle, releaseDate:releaseDate)
        }
    }

    static interface StoryApi {

        @Get('/story')
        Story get()
    }

    static class Story {
        String title
        String releaseDate
    }
}
