package org.particleframework.management.endpoint.stop;

import org.particleframework.context.ApplicationContext;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Write;

import java.util.LinkedHashMap;
import java.util.Map;

@Endpoint(id = "stop", defaultEnabled = false)
public class ServerStopEndpoint {

    private final ApplicationContext context;
    private final Map<String, String> message;

    ServerStopEndpoint(ApplicationContext context) {
        this.context = context;
        this.message = new LinkedHashMap<>(1);
        this.message.put("message", "Server shutdown started");
    }

    @Write(consumes = {})
    public Object stop() {
        try {
            return message;
        }
        finally {
            Thread thread = new Thread(this::stopServer);
            thread.setContextClassLoader(getClass().getClassLoader());
            thread.start();
        }
    }

    private void stopServer() {
        try {
            Thread.sleep(500L);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        this.context.stop();
    }

}
