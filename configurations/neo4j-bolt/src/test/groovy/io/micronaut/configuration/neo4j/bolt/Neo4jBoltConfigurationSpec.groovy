/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.neo4j.bolt

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class Neo4jBoltConfigurationSpec extends Specification {

    void "test neo4j configuration"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                'neo4j.uri':'bolt://someserver:7687',
                'neo4j.embedded.enabled':false
        )

        then:
        applicationContext.containsBean(Neo4jBoltConfiguration)
        applicationContext.getBean(Neo4jBoltConfiguration).uris.size() == 1
        applicationContext.getBean(Neo4jBoltConfiguration).uris[0] == URI.create('bolt://someserver:7687')

        cleanup:
        applicationContext?.stop()
    }
}
