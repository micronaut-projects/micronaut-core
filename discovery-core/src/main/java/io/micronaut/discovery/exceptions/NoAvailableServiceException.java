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
package io.micronaut.discovery.exceptions;

/**
 * An exception thrown when no service is available.
 *
 * @author graemerocher
 * @since 1.0
 */
public class NoAvailableServiceException extends DiscoveryException {

    private final String serviceID;

    /**
     * @param serviceID The service ID
     */
    public NoAvailableServiceException(String serviceID) {
        super("No available services for ID: " + serviceID);
        this.serviceID = serviceID;
    }

    /**
     * @return The service ID
     */
    public String getServiceID() {
        return serviceID;
    }
}
