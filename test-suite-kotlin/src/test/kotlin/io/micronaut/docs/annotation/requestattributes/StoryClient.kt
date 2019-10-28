package io.micronaut.docs.annotation.requestattributes

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.annotation.RequestAttributes
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single

// tag::class[]
@Client("/story")
@RequestAttributes(RequestAttribute(name = "client-name", value = "storyClient"), RequestAttribute(name = "version", value = "1"))
interface StoryClient {

    @Get("/{storyId}")
    fun getById(@RequestAttribute storyId: String): Single<Story>
}
// end::class[]
