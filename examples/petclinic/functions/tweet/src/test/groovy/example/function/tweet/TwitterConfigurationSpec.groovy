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

import io.micronaut.context.ApplicationContext
import spock.lang.IgnoreIf
import spock.lang.Specification
import twitter4j.conf.Configuration

/**
 * @author graemerocher
 * @since 1.0
 */
class TwitterConfigurationSpec extends Specification {

    void "test twitter config manually supplied"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'twitter.OAuth2AccessToken': 'xxxxxx'
        )
        TwitterConfiguration config = applicationContext.getBean(TwitterConfiguration)
        Configuration configuration = config.builder.build()

        expect:
        configuration.OAuth2AccessToken == 'xxxxxx'

    }

    @IgnoreIf({
        !System.getenv('TWITTER_OAUTH_ACCESS_TOKEN') ||
                !System.getenv('TWITTER_OAUTH_ACCESS_TOKEN_SECRET') ||
                !System.getenv('TWITTER_OAUTH_CONSUMER_KEY') ||
                !System.getenv('TWITTER_OAUTH_CONSUMER_SECRET')
    })
    void "test twitter config supplied from environment"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        TwitterConfiguration config = applicationContext.getBean(TwitterConfiguration)
        Configuration configuration = config.builder.build()

        expect:
        configuration.OAuthAccessToken == System.getenv('TWITTER_OAUTH_ACCESS_TOKEN')
        configuration.OAuthAccessTokenSecret == System.getenv('TWITTER_OAUTH_ACCESS_TOKEN_SECRET')
        configuration.OAuthConsumerKey == System.getenv('TWITTER_OAUTH_CONSUMER_KEY')
        configuration.OAuthConsumerSecret == System.getenv('TWITTER_OAUTH_CONSUMER_SECRET')
    }
}
