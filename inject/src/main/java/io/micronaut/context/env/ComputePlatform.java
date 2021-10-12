/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.env;

/**
 * @author Ryan Vanderwerf
 * @since 1.0
 */
public enum ComputePlatform {

    /**
     * Google Compute Platform.
     */
    GOOGLE_COMPUTE,

    /**
     * Amazon EC2.
     */
    AMAZON_EC2,

    /**
     * Microsoft Azure.
     */
    AZURE,

    /**
     * Oracle Cloud.
     */
    ORACLE_CLOUD,

    /**
     * Digital Ocean.
     */
    DIGITAL_OCEAN,

    /**
     * Cloud or non cloud provider on bare metal (unknown).
     */
    BARE_METAL,

    /**
     * IBM Cloud.
     */
    IBM,

    /**
     * Other.
     */
    OTHER
}
