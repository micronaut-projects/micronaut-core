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
package example.pets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import example.api.v1.PetType;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import example.api.v1.Pet;

/**
 * @author graemerocher
 * @since 1.0
 */
public class PetEntity extends Pet {
    @BsonCreator
    @JsonCreator
    public PetEntity(
            @JsonProperty("vendor")
            @BsonProperty("vendor") String vendor,
            @JsonProperty("name")
            @BsonProperty("name") String name,
            @JsonProperty("image")
            @BsonProperty("image") String image) {
        super(vendor, name, image);
    }

    @Override
    public PetEntity type(PetType type) {
        return (PetEntity) super.type(type);
    }

    @Override
    public PetEntity slug(String slug) {
        return (PetEntity) super.slug(slug);
    }

    @Override
    public void setSlug(String image) {
        super.setSlug(image);
    }

    @Override
    public void setImage(String image) {
        super.setImage(image);
    }

    @Override
    public void setType(PetType type) {
        super.setType(type);
    }
}
