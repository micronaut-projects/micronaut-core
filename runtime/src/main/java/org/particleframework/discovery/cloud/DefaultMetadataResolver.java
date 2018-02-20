package org.particleframework.discovery.cloud;

import org.particleframework.context.env.Environment;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * @author rvanderwerf
 * @since 1.0
 */
@Singleton
public class DefaultMetadataResolver implements MetadataResolver {

    @Inject
    Optional<AmazonMetadataResolver> amazonMetadataResolver;

    @Inject
    Optional<GoogleComputeMetadataResolver> googleComputeMetadataResolver;

    @Override
    public Optional<? extends ComputeInstanceMetadata> resolve(Environment environment) {

        if (environment.getActiveNames().contains(Environment.AMAZON_EC2) && amazonMetadataResolver.isPresent()) {
            Optional<? extends ComputeInstanceMetadata> computeInstanceMetadata = amazonMetadataResolver.get().resolve(environment);
            if (computeInstanceMetadata.isPresent()) {
                return computeInstanceMetadata;
            }
        }
        if (environment.getActiveNames().contains(Environment.GOOGLE_COMPUTE) && googleComputeMetadataResolver.isPresent()) {
            Optional<? extends ComputeInstanceMetadata> computeInstanceMetadata = googleComputeMetadataResolver.get().resolve(environment);
            if (computeInstanceMetadata.isPresent()) {
                return computeInstanceMetadata;
            }
        }

        // do default localhost type stuff
        return Optional.empty();
    }
}
