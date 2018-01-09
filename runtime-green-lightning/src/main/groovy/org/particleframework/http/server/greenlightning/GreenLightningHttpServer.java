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
import org.particleframework.runtime.server.EmbeddedServer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URL;

@Singleton
public class GreenLightningHttpServer implements EmbeddedServer {

    private final ParticleGreenLightningApp particleGreenLightningApp;

    @Inject
    public GreenLightningHttpServer(ParticleGreenLightningApp particleGreenLightningApp) {
        this.particleGreenLightningApp = particleGreenLightningApp;
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public EmbeddedServer start() {
        GreenRuntime.run(particleGreenLightningApp);
        return this;
    }

    @Override
    public EmbeddedServer stop() {
        particleGreenLightningApp.stop();
        return this;
    }

    @Override
    public int getPort() {
        return particleGreenLightningApp.getPort();
    }

    @Override
    public String getHost() {
        return particleGreenLightningApp.getHost();
    }

    @Override
    public URL getURL() {
        return null;
    }

    @Override
    public URI getURI() {
        return null;
    }
}
