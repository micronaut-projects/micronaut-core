/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.ioc.beans;

// tag::class[]
import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public class Catalog<T> {

    private List<? extends Number> prices;
    private List<T> items;
    private T[] samples;

    public List<? extends Number> getPrices() {
        return prices;
    }

    public void setPrices(List<? extends Number> prices) {
        this.prices = prices;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public T[] getSamples() {
        return samples;
    }

    public void setSamples(T[] samples) {
        this.samples = samples;
    }
}
// end::class[]
