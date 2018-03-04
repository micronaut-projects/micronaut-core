package example.storefront.client.v1

import example.api.v1.CommentOperations
import example.api.v1.HealthStatusOperation
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Post
import org.particleframework.http.client.Client

/**
 * @author zacharyklein
 * @since 1.0
 */
@Client(id = "comments")
interface CommentClient extends CommentOperations<Comment>, HealthStatusOperation {

    @Override
    @Get("/v1/topics/{topic}/comments") //TODO: Hard-coding the topics/ paths (instead of using @Client(path)) in order to allow /health endpoint to be accessed
    List<Comment> list(String topic)

    @Override
    @Get("/v1/topics/comment/{id}")
    Map<String, Object> expand(Long id)

    @Override
    @Post("/v1/topics/{topic}/comments")
    HttpStatus add(String topic, String poster, String content)

    @Override
    @Post("/v1/topics/comment/{id}/reply")
    HttpStatus addReply(Long id, String poster, String content)
}
