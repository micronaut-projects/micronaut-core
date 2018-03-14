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
package example.api.v1;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * @author graemerocher
 * @since 1.0
 */
public interface CommentOperations<T extends Comment> {

    /**
     * List comments for a given topic
     * @param topic The topic
     * @return The comments
     */
    @Get("/{topic}/comments")
    List<T> list(String topic);

    /**
     * A map of comments and nested replies
     *
     * @param id The comment to expand
     * @return The comments
     */
    @Get("/comment/{id}")
    Map<String, Object> expand(Long id);
    /**
     * Add a new comment under a given topic
     * @param topic The topic
     * @param poster The poster
     * @param content The content
     * @return An {@link HttpStatus#CREATED} if the comment was created
     */
    @Post("/{topic}/comments")
    HttpStatus add(
            @NotBlank String topic,
            @NotBlank String poster,
            @NotBlank String content);

    /**
     * Add a new comment under a given topic
     * @param id The id of the comment
     * @param poster The poster
     * @param content The content
     * @return An {@link HttpStatus#CREATED} if the comment was created
     */
    @Post("/comment/{id}/reply")
    HttpStatus addReply(
            @NotBlank Long id,
            @NotBlank String poster,
            @NotBlank String content);
}
