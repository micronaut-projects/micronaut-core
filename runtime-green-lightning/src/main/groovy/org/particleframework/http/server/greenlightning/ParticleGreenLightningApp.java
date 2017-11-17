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

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.RestListener;
import org.particleframework.web.router.Router;

import java.util.Optional;


public class ParticleGreenLightningApp implements GreenApp {
    protected final int port;
    protected final String host;
    protected GreenRuntime runtime;
    protected final Optional<Router> router;

    public ParticleGreenLightningApp(Optional<Router> router, String host, int port) {
        this.router = router;
        this.port = port;
        this.host = host;
    }

    @Override
    public void declareConfiguration(final Builder builder) {
        builder.enableServer(false, false, host, port);
    }

    public void declareBehavior(final GreenRuntime runtime) {
        final RestListener adder = new GreenLightningParticleDispatcher(runtime, router);
        runtime.addRestListener(adder).includeAllRoutes();
        this.runtime = runtime;
    }

    public void stop() {
        runtime.shutdownRuntime();
    }
}
