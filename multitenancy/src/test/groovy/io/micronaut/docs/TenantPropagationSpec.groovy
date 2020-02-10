/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.multitenancy.propagation.TenantPropagationHttpClientFilter
import io.micronaut.multitenancy.tenantresolver.TenantResolver
import io.micronaut.multitenancy.writer.TenantWriter
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.testutils.YamlAsciidocTagCleaner
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.See
import spock.lang.Shared
import spock.lang.Specification

@See("src/main/docs/resources/images/multitenancy.svg")
class TenantPropagationSpec  extends Specification implements YamlAsciidocTagCleaner {

    String gatewayConfig = '''\
//tag::gatewayConfig[]
micronaut:
  multitenancy:
    propagation: 
      enabled: true
      service-id-regex: 'catalogue'
    tenantresolver:
      subdomain:
        enabled: true
    tenantwriter:
      httpheader:
        enabled: true                     
'''//end::gatewayConfig[]

    String catalogueConfig = '''\
//tag::catalogueConfig[]
micronaut:
  multitenancy:
    tenantresolver:
      httpheader:
        enabled: true                         
'''//end::catalogueConfig[]

    @Shared
    Map<String, Object> gatewayConfMap = [
            'micronaut': [
                    'multitenancy': [
                            'propagation': [
                                    'enabled': true,
                                    'service-id-regex': 'catalogue'
                            ],
                            'tenantresolver': [
                                    'subdomain': [
                                            'enabled': true,
                                    ]
                            ],
                            'tenantwriter': [
                                    'httpheader': [
                                            'enabled': true,
                                    ]
                            ]

                    ]
            ]
    ]

    @Shared
    Map<String, Object> catalogueConfMap = [
            'micronaut': [
                    'multitenancy': [
                            'tenantresolver': [
                                    'httpheader': [
                                            'enabled': true,
                                    ]
                            ],

                    ]
            ]
    ]

    @Shared
    Map<String, Object> gatewayconfig = [
            'spec.name' : 'docstenantpropagationgateway',
    ] << flatten(gatewayConfMap)

    @Shared
    Map<String, Object> catalogueconfig = [
            'spec.name' : 'docstenantpropagationcatalogue',
    ] << flatten(catalogueConfMap)

    @Shared
    @AutoCleanup
    EmbeddedServer gatewayEmbeddedServer = ApplicationContext.run(EmbeddedServer, gatewayconfig as Map<String, Object>, Environment.TEST)

    @Shared
    @AutoCleanup
    EmbeddedServer catalogueEmbeddedServer = ApplicationContext.run(EmbeddedServer, catalogueconfig as Map<String, Object>, Environment.TEST)

    void "document tenant propagation"() {
        when:
        Map m = new Yaml().load(cleanYamlAsciidocTag(gatewayConfig, 'gatewayConfig'))

        then:
        m == gatewayConfMap

        when:
        m = new Yaml().load(cleanYamlAsciidocTag(catalogueConfig, 'catalogueConfig'))

        then:
        m == catalogueConfMap

        when:
        for (Class clazz : [TenantWriter, TenantResolver, TenantPropagationHttpClientFilter]) {
            gatewayEmbeddedServer.applicationContext.getBean(clazz)
        }

        then:
        noExceptionThrown()

        when:
        catalogueEmbeddedServer.applicationContext.getBean(TenantResolver)

        then:
        noExceptionThrown()

        when:
        catalogueEmbeddedServer.applicationContext.getBean(TenantWriter)

        then:
        thrown(NoSuchBeanException)

        when:
        catalogueEmbeddedServer.applicationContext.getBean(TenantPropagationHttpClientFilter)

        then:
        thrown(NoSuchBeanException)
    }
}
