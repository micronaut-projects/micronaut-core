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
package example.storefront.client.v1.fallback;

import example.api.v1.Vendor;
import example.api.v1.VendorOperations;
import io.reactivex.Single;
import io.micronaut.retry.annotation.Fallback;

import java.util.Collections;
import java.util.List;

/**
 * @author graemerocher
 * @since 1.0
 */
@Fallback
public class VendorClientFallback implements VendorOperations {
    @Override
    public Single<List<Vendor>> list() {
        return Single.just(Collections.emptyList());
    }

    @Override
    public Single<List<String>> names() {
        return Single.just(Collections.emptyList());
    }

    @Override
    public Single<Vendor> save(String name) {
        return Single.just(new Vendor(name));
    }
}
