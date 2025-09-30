package io.micronaut.test.lombok.importtest;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class VersionManifest {
    @NonNull
    List<VersionManifestEntry> workflows;

    @Value
    @Builder
    @Jacksonized
    public static class VersionManifestEntry {
        @NonNull
        String name;

        @NonNull
        int majorVersion;

        @NonNull
        int minorVersion;

    }
}
