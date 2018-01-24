/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty

import okhttp3.OkHttpClient
import org.particleframework.context.ApplicationContext
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractParticleSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'


    @Shared File uploadDir = File.createTempDir()
    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            getConfiguration() << [(SPEC_NAME_PROPERTY):getClass().simpleName]
    )
    @Shared int serverPort = embeddedServer.getPort()
    @Shared URL server = embeddedServer.getURL()
    @Shared OkHttpClient client = new OkHttpClient()
                                            .newBuilder()
                                            .readTimeout(1, TimeUnit.MINUTES)
                                            .build()

    Collection<String> configurationNames() {
        ['org.particleframework.configuration.jackson','org.particleframework.web.router']
    }

    Map<String, Object> getConfiguration() {
        ['particle.server.multipart.location':uploadDir.absolutePath]
    }


    void cleanupSpec()  {
        uploadDir.delete()
    }
}
