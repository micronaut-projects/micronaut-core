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

import example.api.v1.VendorOperations
import io.reactivex.Single
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class VendorControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, Collections.singletonMap(
                    "consul.client.registration.enabled",false
            ))

    @Shared VendorOperations vendorOperations = embeddedServer
                                                    .getApplicationContext()
                                                    .getBean(VendorOperations)

    void 'test list vendors'() {
        when:
        List<example.api.v1.Vendor> vendors = vendorOperations.list().blockingGet()

        then:
        vendors.size() == 0
    }

    void 'test save vendor'() {
        when:
        example.api.v1.Vendor v = vendorOperations.save("Bob").blockingGet()

        then:
        v != null
        v.name == "Bob"
        vendorOperations.list().blockingGet().size() == 1
        vendorOperations.names().blockingGet().contains("Bob")

    }

    @Client('/${vendors.api.version}/vendors')
    static interface TestVendorOperations extends VendorOperations {
        @Override
        Single<example.api.v1.Vendor> save(String name)
    }
}
