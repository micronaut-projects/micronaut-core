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

import grails.gorm.transactions.Rollback
import org.particleframework.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class TopicRepositorySpec extends Specification{

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run()


    @Rollback
    void "test save and retrieve topic comments"() {
        given:
        TopicRepistory topicRepistory = applicationContext.getBean(TopicRepistory)

        when:"A new topic with comments is saved"
        new Topic(title: "Some topic")
                .addToComments(new Comment(poster: "Fred", content: "Some comment"))
                .addToComments(new Comment(poster: "Joe", content: "Some comment"))
                .save(flush:true)

        then:
        topicRepistory.findComments("Some topic").size() == 2
        topicRepistory.findComments("Some topic").first().dateCreated
        topicRepistory.findComments("Some topic").first().poster == "Fred"
    }

}
