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

import org.grails.datastore.mapping.validation.ValidationException
import org.neo4j.driver.v1.types.Path
import org.particleframework.configuration.neo4j.bolt.Neo4jBoltSettings
import org.particleframework.context.ApplicationContext
import org.particleframework.runtime.server.EmbeddedServer
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
            ['neo4j.uri': Neo4jBoltSettings.DEFAULT_URI,
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
        def c2 = commentRepository.saveComment(
                "Some Topic", "Joe", "Some content"
        )
        def r1 = commentRepository.saveReply(c1, "Barney", "I Agree!")
        def r2 = commentRepository.saveReply(c1, "Jeff", "Absolutely!")
        commentRepository.saveReply(r1, "John", "Yup!")
        commentRepository.saveReply(c2, "Ed", "Superb!")


        then:
        commentRepository.findComments("Some Topic").size() == 2
        commentRepository.findComments("Some Topic").first().dateCreated
        commentRepository.findComments("Some Topic").first().poster == "Fred"

        when:
        List<Path> paths = commentRepository.findCommentReplies(c1.id)

        println paths
        then:
        paths
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
    }
}
