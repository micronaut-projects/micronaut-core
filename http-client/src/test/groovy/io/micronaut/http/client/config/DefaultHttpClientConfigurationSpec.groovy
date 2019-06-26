/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.config

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClientConfiguration
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class DefaultHttpClientConfigurationSpec extends Specification {

    @Unroll
    void "test config for #key"() {
        given:
        def ctx = ApplicationContext.run(
                ("micronaut.http.client.$key".toString()): value
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)


        expect:
        config[property] == expected

        cleanup:
        ctx.close()

        where:
        key                 | property          | value  | expected
        'read-timeout'      | 'readTimeout'     | '15s'  | Optional.of(Duration.ofSeconds(15))
        'proxy-type'        | 'proxyType'       | 'http' | Proxy.Type.HTTP
        'read-idle-timeout' | 'readIdleTimeout' | '-1s'  | Optional.of(Duration.ofSeconds(-1))
        'read-idle-timeout' | 'readIdleTimeout' | '1s'   | Optional.of(Duration.ofSeconds(1))
        'read-idle-timeout' | 'readIdleTimeout' | '-1'   | Optional.empty()
    }


    void "test pool config"() {
        given:
        def ctx = ApplicationContext.run(
                ("micronaut.http.client.pool.$key".toString()): value
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)
        HttpClientConfiguration.ConnectionPoolConfiguration poolConfig = config.getConnectionPoolConfiguration()

        expect:
        poolConfig[property] == expected

        cleanup:
        ctx.close()

        where:
        key               | property         | value   | expected
        'enabled'         | 'enabled'        | 'false' | false
        'max-connections' | 'maxConnections' | '10'    | 10
    }

    void "test overriding logger for the client"() {
        given:
        def ctx = ApplicationContext.run(
                ("micronaut.http.client.loggerName".toString()): "myclient.custom.logger"
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)

        expect:
        config['loggerName'] == Optional.of('myclient.custom.logger')

        cleanup:
        ctx.close()

    }
}
