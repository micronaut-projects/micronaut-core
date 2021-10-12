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
package io.micronaut.retry.intercept

import io.micronaut.core.order.OrderUtil
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class InterceptorOrderSpec extends Specification {

    void "test interceptors orders"() {
        given:
        List interceptors = [new DefaultRetryInterceptor(null, null), new RecoveryInterceptor() ]
        OrderUtil.sort(interceptors)

        expect:
        interceptors[0] instanceof RecoveryInterceptor
        interceptors[1] instanceof DefaultRetryInterceptor
    }

    void "test interceptors orders 2"() {
        given:
        List interceptors = [new RecoveryInterceptor(), new DefaultRetryInterceptor(null, null) ]
        OrderUtil.sort(interceptors)

        expect:
        interceptors[0] instanceof RecoveryInterceptor
        interceptors[1] instanceof DefaultRetryInterceptor
    }
}
