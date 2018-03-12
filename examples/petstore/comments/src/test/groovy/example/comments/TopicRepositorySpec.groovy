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

import grails.neo4j.Path
import groovy.json.JsonOutput
import org.grails.datastore.mapping.validation.ValidationException
import io.micronaut.configuration.neo4j.bolt.Neo4jBoltSettings
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * @author graemerocher
 * @since 1.0
 */
@Stepwise
class TopicRepositorySpec extends Specification{

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ["consul.client.registration.enabled":false,
            'neo4j.uri': "bolt://localhost:${SocketUtils.findAvailableTcpPort()}",
            'neo4j.embedded.ephemeral':true]
    )



    void "test save and retrieve topic comments"() {
        given:
        TopicRepository topicRepistory = embeddedServer.applicationContext.getBean(TopicRepository)
        CommentRepository commentRepository = embeddedServer.applicationContext.getBean(CommentRepository)

        when:"An invalid title is used"
        topicRepistory.saveTopic("")

        then:
        thrown(ValidationException)

        when:"A new topic with comments is saved"
        topicRepistory.saveTopic("Some Topic")
        def c1 = commentRepository.saveComment(
                "Some Topic", "Fred", "Some content"
        )
        def r1 = commentRepository.saveReply(c1, "Barney", "I Agree!")
        def r2 = commentRepository.saveReply(c1, "Jeff", "Absolutely!")
        def r3 = commentRepository.saveReply(r1, "John", "Yup!")

        def c2 = commentRepository.saveComment(
                "Some Topic", "Joe", "Some content"
        )
        def r4 = commentRepository.saveReply(c2, "Ed", "Superb!")


        then:
        commentRepository.findComments("Some Topic").size() == 2
        commentRepository.findComments("Some Topic").first().dateCreated
        commentRepository.findComments("Some Topic").first().poster == "Fred"

        when:
        def replies = commentRepository.findCommentReplies(c1.id)

        then:
        replies.id == c1.id
        replies.replies.size() == 2
        replies.replies.first().replies.size() == 1
        !replies.replies.last().replies
    }

    void "test read topic response"() {
        given:
        TestCommentClient client = embeddedServer.applicationContext.getBean(TestCommentClient)

        when:
        List<Comment> comments = client.list("Some Topic")

        then:
        comments != null
        comments.size() == 2
        comments.first().poster == "Fred"


        when:
        def first = comments.first()
        Map<String,Object> data = client.expand(first.id)

        then:
        data.id == first.id
        data.replies.size() == 2


        when:"A new reply is added"
        HttpStatus status = client.addReply(
                first.id,
                "Edward",
                "More stuff"
        )

        then:"The reply is created"
        status == HttpStatus.CREATED

        when:
        data = client.expand(first.id)

        then:
        data.replies.size() == 3

    }
}
