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
package org.particleframework.docs.server.filters

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.particleframework.context.ApplicationContext
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TraceFilterSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)

    void "test trace filter"() {
        given:
        // TODO: Replace with Particle HTTP client when written
        OkHttpClient client = new OkHttpClient()
        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/hello"))
        Response response = client.newCall(request.build()).execute()


        expect:
        response.header('X-Trace-Enabled') == 'true'
    }
}
