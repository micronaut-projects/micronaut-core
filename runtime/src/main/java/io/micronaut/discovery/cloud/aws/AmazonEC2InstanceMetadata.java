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
package io.micronaut.discovery.cloud.aws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.context.env.ComputePlatform;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.discovery.cloud.AbstractComputeInstanceMetadata;

/**
 * Represents {@link io.micronaut.discovery.cloud.ComputeInstanceMetadata} for Amazon's EC2.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
@Introspected
public class AmazonEC2InstanceMetadata extends AbstractComputeInstanceMetadata {

    private final ComputePlatform computePlatform = ComputePlatform.AMAZON_EC2;

    @Override
    @JsonIgnore
    public ComputePlatform getComputePlatform() {
        return computePlatform;
    }
}
