package io.micronaut.docs.annotation.requestattributes

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/story")
class StoryController {

    @Get("/{id}")
    HttpResponse<Story> get(String id) {
        Story story = new Story()
        story.setId(id)
        return HttpResponse.ok(story)
    }
}
