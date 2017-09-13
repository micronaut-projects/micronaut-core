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
import org.particleframework.core.io.socket.SocketUtils
import org.particleframework.runtime.ParticleApplication
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractParticleSpec extends Specification {

    @Shared int serverPort = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup ApplicationContext applicationContext =
                            ParticleApplication.build('-port',String.valueOf(serverPort))
                                               .include(configurationNames() as String[])
                                               .run()

    @Shared String server = "http://localhost:$serverPort"
    @Shared OkHttpClient client = new OkHttpClient()

    Collection<String> configurationNames() {
        ['org.particleframework.configuration.jackson','org.particleframework.web.router']
    }
}
