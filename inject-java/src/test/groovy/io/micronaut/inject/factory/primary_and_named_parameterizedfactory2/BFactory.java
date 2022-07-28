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
package io.micronaut.inject.factory.primary_and_named_parameterizedfactory2;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class BFactory {

    @Named("default")
    @Primary
    @Singleton
    public MyBean primaryBean(MyAssocBean myAssocBean) {
        return new MyBean("myPrimary", myAssocBean);
    }

    @Primary
    @Singleton
    public MyAssocBean myAssocBean() {
        return new MyAssocBean("myPrimary");
    }

    @EachBean(MyBean.class)
    public MyBeanUser2 myBeanUser2(MyBean myBean) {
        return new MyBeanUser2("xxx", myBean);
    }

    @EachBean(MyBeanUser2.class)
    public MyBeanUser myBeanUser(MyBeanUser2 myBeanUser2) {
        return new MyBeanUser("xxx", myBeanUser2);
    }

}
