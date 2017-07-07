/*
 * Copyright 2017 original authors
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
package org.particleframework.runtime

import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Bean
import org.particleframework.context.annotation.Factory
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ParticleApplicationSpec extends Specification {

    void "test particle application"() {
        when:
        ApplicationContext applicationContext = ParticleApplication.run(Application)

        then:
        applicationContext != null
        applicationContext.containsBean(Application)
        applicationContext.containsBean(A)
    }

    @Factory
    static class Application  {
        static void main(String[] args) {
            ParticleApplication.run(Application, args)
        }

        @Bean
        A a() { new A() }
    }

    static class A {}


}
