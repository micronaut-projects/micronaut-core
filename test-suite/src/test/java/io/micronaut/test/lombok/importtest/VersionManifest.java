package io.micronaut.test.lombok.importtest;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
//import lombok.extern.jackson.Jacksonized; TODO: bring back once lombok Jacksonized supports jackson 3

import java.util.List;

@Value
@Builder
//@Jacksonized TODO: bring back once lombok Jacksonized supports jackson 3
public class VersionManifest {
    @NonNull
    List<VersionManifestEntry> workflows;

    @Value
    @Builder
    //@Jacksonized TODO: bring back once lombok Jacksonized supports jackson 3
    public static class VersionManifestEntry {
        @NonNull
        String name;

        @NonNull
        int majorVersion;

        @NonNull
        int minorVersion;

    }
}
