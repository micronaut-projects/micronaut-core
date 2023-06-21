/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.docs.server.binding

import io.micronaut.core.annotation.Introspected

@Introspected
class Point {
    var x: Int? = null
    var y: Int? = null

    constructor(x: Int?, y: Int?) {
        this.x = x
        this.y = y
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val point = o as Point
        return if (x != point.x) {
            false
        } else y == point.y
    }

    override fun hashCode(): Int {
        var result = if (x != null) x.hashCode() else 0
        result = 31 * result + if (y != null) y.hashCode() else 0
        return result
    }
}

