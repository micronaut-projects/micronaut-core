package io.micronaut.http.server.netty.loom;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

public class VirtualThreadTest {
    public static void main(String[] args) {
        ApplicationContext.run(Map.of("spec.name", "VirtualThreadTest"), args);
    }

    @Controller("/urandom")
    @Requires(property = "spec.name", value = "VirtualThreadTest")
    static class RandomController {
        private final Path urandom = Paths.get("/dev/urandom");

        @Get("/blocking")
        @ExecuteOn(TaskExecutors.IO)
        public String getBytes(@QueryValue(value = "n", defaultValue = "128") int n) throws IOException {
            byte[] bytes;
            try (InputStream stream = Files.newInputStream(urandom)) {
                bytes = stream.readNBytes(n);
            }
            return Base64.getEncoder().encodeToString(bytes);
        }
    }
}
