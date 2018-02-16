package org.particleframework.discovery.cloud;

import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.ComputePlatform;
import org.particleframework.context.env.Environment;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
@Requires(env="gc")
public class GoogleComputeMetadataResolver implements MetadataResolver {
    @Override
    public Optional<? extends ComputeInstanceMetadata> resolve(Environment environment) {
        // not implemented yet
        return Optional.empty();
    }
}
