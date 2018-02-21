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

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import grails.neo4j.Node
import grails.neo4j.mapping.MappingBuilder

/**
 * @author graemerocher
 * @since 1.0
 */
@Entity
class Topic implements Node<Topic> {
    
    String title
    List<Comment> comments
    static hasMany = [comments:Comment]

    static mapping = MappingBuilder.node {
        id generator:'assigned', name:'title'
        version false
    }
}
