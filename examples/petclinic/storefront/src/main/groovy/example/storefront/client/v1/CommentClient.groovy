package example.storefront.client.v1

import example.api.v1.CommentOperations
import org.particleframework.http.HttpStatus
import org.particleframework.http.client.Client

/**
 * @author zacharyklein
 * @since 1.0
 */
@Client(id = "comments", path = "/v1/topics")
interface CommentClient extends CommentOperations<Comment> {

    @Override
    List<Comment> list(String topic)

    @Override
    Map<String, Object> expand(Long id)

    @Override
    HttpStatus add(String topic, String poster, String content)

    @Override
    HttpStatus addReply(Long id, String poster, String content)
}
