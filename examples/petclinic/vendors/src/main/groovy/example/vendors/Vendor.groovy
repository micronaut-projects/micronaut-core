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

import example.api.v1.Pet
import grails.gorm.annotation.Entity

/**
 * @author graemerocher
 * @since 1.0
 */
@Entity
class Vendor extends example.api.v1.Vendor {

    @Override
    void setName(String name) {
        super.setName(name)
    }

    Vendor pets(List<Pet> pets) {
        setPets(pets)
        return this
    }
}
