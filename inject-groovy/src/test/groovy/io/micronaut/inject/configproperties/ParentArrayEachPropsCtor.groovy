/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.order.Ordered

import javax.annotation.Nullable

@EachProperty(value = "teams", list = true)
class ParentArrayEachPropsCtor implements Ordered {

    Integer wins
    private final Integer index
    private ManagerProps managerProps

    ParentArrayEachPropsCtor(@Parameter Integer index, @Nullable ManagerProps managerProps) {
        this.index = index
        this.managerProps = managerProps
    }

    @Override
    int getOrder() {
        index
    }

    ManagerProps getManager() {
        managerProps
    }

    @ConfigurationProperties("manager")
    static class ManagerProps implements Ordered {

        private final Integer index
        Integer age

        ManagerProps(@Parameter Integer index) {
            this.index = index
        }

        @Override
        int getOrder() {
            index
        }
    }
}
