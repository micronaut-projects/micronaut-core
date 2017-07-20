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
package org.particleframework.http.server.greenlightning;

import com.ociweb.gl.api.GreenRuntime;
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.runtime.server.EmbeddedServer;

import javax.inject.Inject;

public class GreenLightningHttpServer implements EmbeddedServer {

    private final HttpServerConfiguration serverConfiguration;
    private final ApplicationContext applicationContext;
    private ParticleGreenLightningApp app;

    @Inject
    public GreenLightningHttpServer(
        HttpServerConfiguration serverConfiguration,
        ApplicationContext applicationContext
    ) {
        this.serverConfiguration = serverConfiguration;
        this.applicationContext = applicationContext;
    }

    @Override
    public EmbeddedServer start() {
        app = new ParticleGreenLightningApp(applicationContext,
                serverConfiguration.getHost(),
                serverConfiguration.getPort());
        GreenRuntime.run(app);
        return this;
    }

    @Override
    public EmbeddedServer stop() {
        app.stop();
        return this;
    }

    @Override
    public int getPort() {
        return serverConfiguration.getPort();
    }
}
