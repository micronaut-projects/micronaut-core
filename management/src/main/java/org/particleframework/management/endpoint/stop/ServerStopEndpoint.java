package org.particleframework.management.endpoint.stop;

import org.particleframework.context.ApplicationContext;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Write;

@Endpoint(id = "stop", defaultEnabled = false)
public class ServerStopEndpoint {

    private ApplicationContext applicationContext;

    ServerStopEndpoint(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Write
    public void stop() {
        applicationContext.stop();
    }
}
