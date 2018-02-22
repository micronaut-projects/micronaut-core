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

import com.fasterxml.jackson.annotation.JsonIgnore
import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import grails.neo4j.Node
import groovy.transform.ToString

import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
@Entity
@ToString(includes= ['id','poster', 'content'])
class Comment implements Node<Comment>, example.api.v1.Comment {
    Long id
    @NotBlank
    String poster
    @NotBlank
    String content
    Date dateCreated
    
    @JsonIgnore
    List<Comment> replies = []
    static hasMany = [replies: Comment]
}
