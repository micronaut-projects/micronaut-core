package io.micronaut.jackson.modules.testclasses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.core.annotation.Introspected;

import java.util.Objects;

@JsonRootName("instance")
@Introspected
public class InstanceInfo {
    private final String hostName;

    @JsonCreator
    InstanceInfo(
        @JsonProperty("hostName") String hostName) {
        this.hostName = hostName;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceInfo that = (InstanceInfo) o;
        return hostName.equals(that.hostName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostName);
    }
}
