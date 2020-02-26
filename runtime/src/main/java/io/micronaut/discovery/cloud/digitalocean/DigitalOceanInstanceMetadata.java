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
package io.micronaut.discovery.cloud.digitalocean;

import io.micronaut.context.env.ComputePlatform;
import io.micronaut.discovery.cloud.AbstractComputeInstanceMetadata;

/**
 * Represents {@link io.micronaut.discovery.cloud.ComputeInstanceMetadata} for Digital Ocean.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 1.1
 */
public class DigitalOceanInstanceMetadata extends AbstractComputeInstanceMetadata {

    private final ComputePlatform computePlatform = ComputePlatform.DIGITAL_OCEAN;

    private String userData;
    private String vendorData;

    @Override
    public ComputePlatform getComputePlatform() {
        return computePlatform;
    }

    /**
     * @return the user data
     */
    public String getUserData() {
        return userData;
    }

    /**
     * @param userData the user data
     */
    public void setUserData(String userData) {
        this.userData = userData;
    }

    /**
     * @return the vendor data
     */
    public String getVendorData() {
        return vendorData;
    }

    /**
     * @param vendorData the vendor data
     */
    public void setVendorData(String vendorData) {
        this.vendorData = vendorData;
    }

}
