/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.factory.primary_and_named_parameterizedfactory;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;

@Factory
public class BFactory {

    @Primary
    @Singleton
    public MyBean primaryBean(MyAssocBean myAssocBean) {
        return new MyBean("myPrimary", myAssocBean);
    }

    @EachProperty("confbeans")
    public MyBean namedBean(@Parameter String name, @Parameter MyAssocBean myAssocBean) {
        return new MyBean(name, myAssocBean);
    }

    @Primary
    @Singleton
    public MyAssocBean myAssocBean() {
        return new MyAssocBean("myPrimary");
    }

    @EachProperty("confbeans")
    public MyAssocBean myPrimaryBean(@Parameter String name) {
        return new MyAssocBean(name);
    }

    @EachBean(MyBean.class)
    public MyBeanUser myBeanUser(@Parameter String name, MyBean myBean) {
        return new MyBeanUser(name, myBean);
    }

}
