package io.micronaut.docs.annotation.requestattributes

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single

// tag::class[]
@Client("/story")
@RequestAttribute(name = "client-name", value = "storyClient")
@RequestAttribute(name = "version", value = "1")
interface StoryClient {

    @Get("/{storyId}")
    Single<Story> getById(@RequestAttribute String storyId)
}
// end::class[]
