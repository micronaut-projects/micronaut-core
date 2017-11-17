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
import org.particleframework.core.io.socket.SocketUtils;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.runtime.server.EmbeddedServer;
import org.particleframework.web.router.Router;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.Optional;

@Singleton
public class GreenLightningHttpServer implements EmbeddedServer {

    private final HttpServerConfiguration serverConfiguration;
    private ParticleGreenLightningApp app;
    private final int serverPort;
    private final Optional<Router> router;

    @Inject
    public GreenLightningHttpServer(
        HttpServerConfiguration serverConfiguration,
        Optional<Router> router
    ) {
        this.serverConfiguration = serverConfiguration;
        this.router = router;
        int port = serverConfiguration.getPort();
        this.serverPort = port == -1 ? SocketUtils.findAvailableTcpPort() : port;

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public EmbeddedServer start() {
        app = new ParticleGreenLightningApp(
                router,
                getHost(),
                getPort());
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
        return serverPort;
    }

    @Override
    public String getHost() {
        return serverConfiguration.getHost().orElse("localhost");
    }

    @Override
    public URL getURL() {
        return null;
    }
}
