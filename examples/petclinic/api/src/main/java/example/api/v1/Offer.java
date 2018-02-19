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

import javax.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * @author graemerocher
 * @since 1.0
 */
public class Offer {

    private Pet pet;
    private String description;
    private BigDecimal price;
    private Currency currency = Currency.getInstance(Locale.US);

    public Offer(Pet pet, String description, BigDecimal price) {
        this.pet = pet;
        this.description = description;
        this.price = price;
    }

    protected Offer() {
    }

    public Pet getPet() {
        return pet;
    }

    public String getDescription() {
        return description;
    }

    @Digits(integer = 4, fraction = 2)
    public BigDecimal getPrice() {
        return price;
    }

    public Currency getCurrency() {
        return currency;
    }

    void setPet(Pet pet) {
        this.pet = pet;
    }

    void setDescription(String description) {
        this.description = description;
    }

    void setPrice(BigDecimal price) {
        this.price = price;
    }

    void setCurrency(Currency currency) {
        this.currency = currency;
    }
}
