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

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.validation.ValidationException
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Error
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Post
import org.particleframework.http.hateos.VndError

import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller('/${comments.api.version}/topics')
@CompileStatic
class TopicController {

    final TopicRepistory topicRepistory

    TopicController(TopicRepistory topicRepistory) {
        this.topicRepistory = topicRepistory
    }

    @Get('/{vendor}/comments')
    List<Comment> listComments(String vendor) {
        return topicRepistory.findComments(vendor)
    }
    
    @Post('/{vendor}/comments')
    HttpStatus addComment(
            @NotBlank String vendor,
            @NotBlank String poster,
            @NotBlank String content) {
        Comment c = topicRepistory.saveComment(
                vendor, poster, content
        )
        if(c != null) {
            return HttpStatus.OK
        }
        return HttpStatus.NOT_FOUND
    }

    @Error(ValidationException.class)
    HttpResponse<VndError> validationError(ValidationException e) {

    }
}
