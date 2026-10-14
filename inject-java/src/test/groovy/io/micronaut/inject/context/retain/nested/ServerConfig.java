package io.micronaut.inject.context.retain.nested;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

import java.util.ArrayList;
import java.util.List;

/**
 * A server with streams of its own, as a messaging connection configures its streams: the streams are entries nested
 * in the server's entry, which the server's configuration receives.
 */
@EachProperty("nested-servers")
@Requires(property = "spec.name", value = "NestedEachPropertyRetentionSpec")
public class ServerConfig {
    public final String name;
    private List<StreamConfig> streams = new ArrayList<>();

    public ServerConfig(@Parameter String name) {
        this.name = name;
    }

    public List<StreamConfig> getStreams() {
        return streams;
    }

    public void setStreams(List<StreamConfig> streams) {
        this.streams = streams;
    }

    @EachProperty("streams")
    public static class StreamConfig {
        public final String name;
        private int size;

        public StreamConfig(@Parameter String name) {
            this.name = name;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
