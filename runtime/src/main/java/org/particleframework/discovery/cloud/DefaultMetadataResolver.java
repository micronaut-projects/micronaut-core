package org.particleframework.discovery.cloud;

import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.ComputePlatform;
import org.particleframework.context.env.Environment;

import javax.inject.Singleton;
import java.util.Optional;

import static java.util.Optional.of;

@Singleton
public class DefaultMetadataResolver implements MetadataResolver {
    @Override
    public Optional<? extends ComputeInstanceMetadata> resolve(Environment environment) {

        if (environment.getActiveNames().contains(Environment.AMAZON_EC2)) {
            Optional<? extends ComputeInstanceMetadata> computeInstanceMetadata = new AmazonMetadataResolver().resolve(environment);
            if (computeInstanceMetadata.isPresent()) {
                return computeInstanceMetadata;
            }
        }
        if (environment.getActiveNames().contains(Environment.GOOGLE_COMPUTE)) {
            Optional<? extends ComputeInstanceMetadata> computeInstanceMetadata = new GoogleComputeMetadataResolver().resolve(environment);
            if (computeInstanceMetadata.isPresent()) {
                return computeInstanceMetadata;
            }
        }

        // do default localhost type stuff
        return Optional.empty();
    }
}
