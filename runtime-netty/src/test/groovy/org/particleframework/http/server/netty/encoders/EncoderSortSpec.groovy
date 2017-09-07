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
package org.particleframework.http.server.netty.encoders

import org.particleframework.core.order.OrderUtil
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class EncoderSortSpec extends Specification {

    void "test sort order"() {
        given:
        def one = new ObjectToStringFallbackEncoder()
        def two = new CompletableFutureEncoder()
        def three = new HttpResponseEncoder()


        when:
        def list = [one, two, three]
        OrderUtil.sort(list)

        then:
        list == [three, two, one]

        when:
        list = [three, two, one]
        OrderUtil.sort(list)

        then:
        list == [three, two, one]
    }
}
