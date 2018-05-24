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
package io.micronaut.inject.method

import groovy.transform.PackageScope
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Created by graemerocher on 26/05/2017.
 */
class SetterStreamSpec extends Specification {
    void "test injection via field that takes a stream"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b =  context.getBean(B)

        then:
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(AImpl))
        b.another.count() == 2
        b.another2.count() == 2
    }

    static interface A {

    }

    @Singleton
    static class AImpl implements A {

    }

    @Singleton
    static class AnotherImpl implements A {

    }

    static class B {
        private Stream<A> all
        private Stream<A> another
        private List<A> allList

        private Stream<A> another2

        @Inject
        private void setAll(Stream<A> all) {
            this.all = all
        }

        @Inject
        protected void setAnother(Stream<A> all) {
            this.another = all
        }
        @Inject
        @PackageScope
        void setAnother2(Stream<A> all) {
            this.another2 = all
        }

        Stream<A> getAnother() {
            return another
        }

        Stream<A> getAnother2() {
            return another2
        }

        List<A> getAll() {
            if(allList == null) {
                allList = this.all.collect(Collectors.toList())
            }
            return allList
        }
    }
}

