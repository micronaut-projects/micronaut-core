/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * TODO: Javadoc description
 *
 * @author graemerocher
 * @since 1.0
 */
class ClientIntroductionAdviceSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()


    void "test implement HTTP client"() {
        given:
        MyClient myService = context.getBean(MyClient)

        expect:
        myService.index() == 'success'
    }

    @Controller('/aop')
    static class AopController implements MyApi {
        @Override
        String index() {
            return "success"
        }
    }


    static interface MyApi {
        @Get(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        String index()
    }

    @Client('/aop')
    static interface MyClient extends MyApi {
    }
}
