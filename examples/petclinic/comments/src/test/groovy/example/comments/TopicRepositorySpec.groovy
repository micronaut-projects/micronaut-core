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
import org.particleframework.configuration.neo4j.bolt.Neo4jBoltSettings
import org.particleframework.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.ConstraintViolationException

/**
 * @author graemerocher
 * @since 1.0
 */
class TopicRepositorySpec extends Specification{

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run(
            'neo4j.uri': Neo4jBoltSettings.DEFAULT_URI,
            'neo4j.embedded.ephemeral':true
    )


    void "test save and retrieve topic comments"() {
        given:
        TopicRepistory topicRepistory = applicationContext.getBean(TopicRepistory)

        when:"An invalid title is used"
        topicRepistory.saveTopic("")

        then:
        thrown(ValidationException)

        when:"A new topic with comments is saved"
        topicRepistory.saveTopic("Some Topic")
        topicRepistory.saveComment(
                "Some Topic", "Fred", "Some content"
        )
        topicRepistory.saveComment(
                "Some Topic", "Joe", "Some content"
        )

        then:
        topicRepistory.findComments("Some Topic").size() == 2
        topicRepistory.findComments("Some Topic").first().dateCreated
        topicRepistory.findComments("Some Topic").first().poster == "Fred"
    }

}
