package org.particleframework.management.endpoint.stop;

import org.particleframework.context.ApplicationContext;
import org.particleframework.context.env.Environment;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Write;
import org.particleframework.runtime.server.EmbeddedServer;

import java.util.LinkedHashMap;
import java.util.Map;

@Endpoint(id = "stop", defaultEnabled = false)
public class ServerStopEndpoint {

    private final ApplicationContext context;
    private final Map message;

    ServerStopEndpoint(ApplicationContext context) {
        this.context = context;
        this.message = new LinkedHashMap(1);
        this.message.put("message", "Server shutdown started");
    }

    @Write
    public Object stop() {
        try {
            return message;
        }
        finally {
            context.stop();
        }
    }
}
