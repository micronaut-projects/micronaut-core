/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.client.docs.annotation.attributes;

import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.reactivex.Single;

// tag::class[]
@Client("/story")
@RequestAttribute(name = "client-name", value = "storyClient")
public interface StoryClient {

    @Get("/{storyId}")
    Single<Story> getById(@RequestAttribute(name = "x-story-id") String story, @QueryValue("storyId") String myStoryId);
}
// end::class[]
