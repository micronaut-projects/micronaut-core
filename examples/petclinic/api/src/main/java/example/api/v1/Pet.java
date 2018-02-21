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

    private String slug;
    private String image;
    private String name;
    protected PetType type = PetType.DOG;
    private String vendor;

    public Pet(String vendor, String name, String slug, String image) {
        this.vendor = vendor;
        this.name = name;
        this.slug = slug;
        this.image = image;
    }

    Pet() {
    }

    @NotBlank
    public String getVendor() {
        return vendor;
    }

    @NotBlank
    public String getName() {
        return name;
    }

    @NotBlank
    public String getSlug() {
        return slug;
    }

    @NotBlank
    public String getImage() {
        return image;
    }

    public PetType getType() {
        return type;
    }

    public Pet type(PetType type) {
        if(type != null) {
            this.type = type;
        }
        return this;
    }

    void setName(String name) {
        this.name = name;
    }

    void setImage(String image) {
        this.image = image;
    }

    void setSlug(String image) {
        this.image = image;
    }

    void setVendor(String vendor) {
        this.vendor = vendor;
    }

    @Override
    public String toString() {
        return "Pet{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", vendor='" + vendor + '\'' +
                ", slug='" + vendor + '\'' +
                ", image='" + image + '\'' +
                '}';
    }
}
