package io.micronaut.docs.annotation.requestattributes

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/story")
class StoryController {

    @Get("/{id}")
    operator fun get(id: String): HttpResponse<Story> {
        val story = Story()
        story.id = id
        return HttpResponse.ok(story)
    }
}
