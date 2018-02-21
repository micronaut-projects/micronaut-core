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

import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import grails.neo4j.services.Cypher
import org.neo4j.driver.v1.types.Path

import javax.inject.Inject
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * @author graemerocher
 * @since 1.0
 */
@Service(Comment)
abstract class CommentRepository {

    @Inject TopicRepository topicRepository

    /**
     * Finds all of the comments for the given topic name
     *
     * @param title The topic title
     * @return The comments
     */
    @Cypher("""MATCH ${Topic t}-[:COMMENTS]->${Comment c} 
               WHERE ${t.title} = $title 
               RETURN $c 
               ORDER BY $c.dateCreated""")
    abstract List<Comment> findComments(String title)

    /**
     * Finds all of the paths for the given comment
     * @param commentId The comment id
     * @return The paths
     */
    @Cypher("""MATCH p=${Comment from}-[:REPLIES*..]->${Comment leaf}
               WHERE ID($from) = $commentId 
               RETURN DISTINCT p""")
    abstract List<Path> findCommentPaths(Long commentId)



    /**
     * Saves a comment
     * @param topic The topic
     * @param poster The poster
     * @param content The content
     * @return The comment
     */
    @Transactional
    Comment saveComment(@NotNull String topic, @NotBlank String poster, @NotBlank String content) {
        Topic t = topicRepository.findTopic(topic)
        if(t == null) {
            t = new Topic(title: topic)
            t.save(failOnError:true)
        }


        def comment = new Comment(poster: poster, content: content).save(failOnError:true)
        t.addToComments(comment)
        t.save(failOnError:true)

        return comment
    }

    /**
     * Saves a comment
     * @param topic The topic
     * @param poster The poster
     * @param content The content
     * @return The comment
     */
    @Transactional
    Comment saveReply(@NotNull Comment replyTo, @NotBlank String poster, @NotBlank String content) {
        def comment = new Comment(poster: poster, content: content).save(failOnError:true)
        replyTo.addToReplies(comment)
        replyTo.save(failOnError:true)
        return comment
    }
}
