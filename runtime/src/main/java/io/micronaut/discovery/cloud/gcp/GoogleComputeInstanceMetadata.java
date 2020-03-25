/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.cloud.gcp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.context.env.ComputePlatform;
import io.micronaut.discovery.cloud.AbstractComputeInstanceMetadata;

/**
 * Represents {@link io.micronaut.discovery.cloud.ComputeInstanceMetadata} for Google Cloud Platform.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public class GoogleComputeInstanceMetadata extends AbstractComputeInstanceMetadata {

    private final ComputePlatform computePlatform = ComputePlatform.GOOGLE_COMPUTE;

    @Override
    @JsonIgnore
    public ComputePlatform getComputePlatform() {
        return computePlatform;
    }

    @Override
    public String getRegion() {
        if (availabilityZone != null) {
            return availabilityZone.substring(0, availabilityZone.length() - 2);
        }
        return region;
    }

}
