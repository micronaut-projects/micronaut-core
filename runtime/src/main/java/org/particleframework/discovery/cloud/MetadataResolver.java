package org.particleframework.discovery.cloud;

import org.particleframework.context.env.ComputePlatform;
import org.particleframework.context.env.Environment;

import java.util.Optional;

/**
 * @author rvanderwerf
 * @since 1.0
 */
public interface MetadataResolver {

    public Optional<? extends ComputeInstanceMetadata> resolve(Environment environment);
}
