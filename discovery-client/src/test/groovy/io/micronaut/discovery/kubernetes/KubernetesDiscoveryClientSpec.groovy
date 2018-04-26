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
package io.micronaut.discovery.kubernetes

import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class KubernetesDiscoveryClientSpec extends Specification {

    void "test kubernetes discovery client"() {
        given:
        KubernetesDiscoveryClient client = new KubernetesDiscoveryClient() {
            @Override
            protected Map<String, String> resolveEnvironment() {
                [
                        "FOO_BAR_SERVICE_HOST":"foobar",
                        "FOO_BAR_SERVICE_PORT":"8080",
                        "FOO_SERVICE_PORT_HTTPS":"8443",
                        "FOO_SERVICE_HOST":"foo"
                ]
            }
        }

        expect:
        client.getServiceIds().blockingFirst() == ['foo-bar', 'foo']
        client.getInstances('foo-bar').blockingFirst()[0].id == 'foo-bar'
        client.getInstances('foo-bar').blockingFirst()[0].getURI() == URI.create("http://foobar:8080")
        client.getInstances('foo').blockingFirst()[0].id == 'foo'
        client.getInstances('foo').blockingFirst()[0].getURI() == URI.create("https://foo:8443")

    }
}
