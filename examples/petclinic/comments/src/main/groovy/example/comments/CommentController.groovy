/*
 * Copyright 2018 original authors
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
package example.comments

import example.api.v1.CommentOperations
import groovy.transform.CompileStatic
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller

import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller('/${comments.api.version}/topics')
@CompileStatic
class CommentController implements CommentOperations<Comment> {

    final CommentRepository commentRepository

    CommentController(CommentRepository commentRepository) {
        this.commentRepository = commentRepository
    }

    @Override
    HttpStatus add(
            @NotBlank String topic,
            @NotBlank String poster,
            @NotBlank String content) {
        Comment c = commentRepository.saveComment(
                topic, poster, content
        )
        if(c != null) {
            return HttpStatus.CREATED
        }
        return HttpStatus.NOT_FOUND
    }

    @Override
    HttpStatus addReply(
            @NotBlank Long id,
            @NotBlank String poster,
            @NotBlank String content) {
        Comment c = commentRepository.saveReply(
                id, poster, content
        )
        if(c != null) {
            return HttpStatus.CREATED
        }
        return HttpStatus.NOT_FOUND
    }

    @Override
    List<Comment> list(String topic) {
        return commentRepository.findComments(topic)
    }

    @Override
    Map<String, Object> expand(Long id) {
        return commentRepository.findCommentReplies(id)
    }
}
