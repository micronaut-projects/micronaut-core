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
package example.function.tweet

import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpRequest
import org.particleframework.http.MediaType
import org.particleframework.http.client.RxHttpClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import twitter4j.Status

import java.time.LocalDateTime

/**
 * @author graemerocher
 * @since 1.0
 */

@IgnoreIf({
    !System.getenv('TWITTER_OAUTH_ACCESS_TOKEN') ||
    !System.getenv('TWITTER_OAUTH_ACCESS_TOKEN_SECRET') ||
    !System.getenv('TWITTER_OAUTH_CONSUMER_KEY') ||
    !System.getenv('TWITTER_OAUTH_CONSUMER_SECRET')
})
class UpdateStatusSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared RxHttpClient httpClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test post tweet"() {
        when:
        UpdateResult result = httpClient.toBlocking()
                                        .retrieve(HttpRequest.POST('/update-status', "My Tweet")
                                                             .contentType(MediaType.TEXT_PLAIN_TYPE), UpdateResult)

        then:
        result.url != null
    }
}
