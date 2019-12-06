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
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class XmlMapperSetupSpec extends Specification {

    void 'verify can retrieve xml mapper'() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run('test').start()

        then:
        applicationContext.containsBean(ObjectMapper, Qualifiers.byName('xml'))
    }

    void 'verify can encode/decode xml'() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run('test').start()
        ObjectMapper mapper = applicationContext.getBean(ObjectMapper, Qualifiers.byName('xml'))

        then:
        mapper.readTree('<person><name>test</name></person>').get('name').textValue() == 'test'
        mapper.writeValueAsString(['1', '2', '3']) == '<ArrayList><item>1</item><item>2</item><item>3</item></ArrayList>'
    }
}
