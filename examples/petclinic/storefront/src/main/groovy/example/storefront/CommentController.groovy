package example.storefront

import example.storefront.client.v1.Comment
import example.storefront.client.v1.CommentClient
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Parameter
import org.particleframework.http.annotation.Post

import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author zacharyklein
 * @since 1.0
 */
@Singleton
@Controller("/comment")
class CommentController {

    @Inject protected CommentClient commentClient

    @Get('/{topic}/comments')
    List<Comment> comments(@Parameter String topic) {
        commentClient.list topic
    }

    @Get('/{topic}/thread/{id}')
    Map<String, Object> thread(Long id) {
        commentClient.expand id
    }

    @Post('/{topic}/comments')
    HttpStatus addTopic(@Parameter String topic, @Body String poster, @Body String content) {
        commentClient.add topic, poster, content
    }

    @Post('/{topic}/comments')
    HttpStatus addReply(@Parameter String topic, @Body String poster, @Body String content) {
        commentClient.add topic, poster, content
    }
}
