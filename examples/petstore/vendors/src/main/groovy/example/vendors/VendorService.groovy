/*
 * Copyright 2018 original authors
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
package example.vendors

import grails.gorm.services.Service

import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
@Service(Vendor)
abstract class VendorService {
    /**
     * List all of the vendors
     *
     * @return The vendors
     */
    abstract List<Vendor> list()

    /**
     * @return list the vendor names
     */
    abstract List<String> listVendorName()

    /**
     * Save a new vendor
     * @param name The name of the vendor
     * @return The vendor instance
     */
    abstract Vendor save(@NotBlank String name)

    /**
     * Finds a new vendor
     * @param name The name of the vendor
     * @return The vendor instance
     */
    abstract Vendor find(@NotBlank String name)

    /**
     * Find an existing vendor or create a new one
     * @param name The name of the vendor
     * @return The Vendor
     */
    Vendor findOrCreate(@NotBlank String name) {
        Vendor v = find(name)
        if(v == null) {
            v = save(name)
        }
        return v
    }
}