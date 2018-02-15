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
package example.api.v1;

import javax.validation.constraints.NotBlank;

/**
 * @author graemerocher
 * @since 1.0
 */
public class Pet {

    private final String name;
    protected PetType type = PetType.DOG;
    private final String vendor;

    public Pet(String vendor, String name) {
        this.vendor = vendor;
        this.name = name;
    }

    @NotBlank
    public String getVendor() {
        return vendor;
    }

    @NotBlank
    public String getName() {
        return name;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        if(type != null) {
            this.type = type;
        }
    }

    @Override
    public String toString() {
        return "Pet{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", vendor='" + vendor + '\'' +
                '}';
    }
}
