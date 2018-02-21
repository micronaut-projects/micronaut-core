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
import groovy.transform.CompileStatic

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * @author graemerocher
 * @since 1.0
 */
@Service(Topic)
@CompileStatic
interface TopicRepository {

  

    /**
     * Finds a topic for the given title
     * @param title The title
     * @return The topic
     */
    Topic findTopic(@NotBlank String title)

    /**
     * Finds a topic for the given title
     * @param title The title
     * @return The topic
     */
    Topic saveTopic(@NotBlank String title)

}