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

    @Get('/{topic}')
    List<Comment> topics(@Parameter String topic) {
        commentClient.list topic
    }

    @Get('/{topic}/{id}')
    Map<String, Object> thread(Long id) {
        commentClient.expand id
    }

    @Post('/{topic}')
    HttpStatus addTopic(String topic, @Body Comment comment) {
        commentClient.add topic, comment.poster, comment.content
    }

    @Post('/{topic}/{id}')
    HttpStatus addReply(@Parameter Long id, @Body Comment comment) {
        commentClient.addReply id, comment.poster, comment.content
    }
}
