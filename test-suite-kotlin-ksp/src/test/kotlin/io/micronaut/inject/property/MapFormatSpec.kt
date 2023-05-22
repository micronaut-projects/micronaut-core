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
package io.micronaut.inject.property

import io.micronaut.context.ApplicationContext
import junit.framework.TestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


class MapFormatSpec {

    @Test
    fun testMapFormatOnProperty() {
        val context = ApplicationContext.run(mapOf("text.properties.yyy.zzz" to 3, "test.properties.yyy.xxx" to 2, "test.properties.yyy.yyy" to 3))
        val config = context.getBean(ConfigProps::class.java)
        assertEquals(config.properties?.get("yyy.xxx"), 2)
        context.close()
    }

    @Test
    fun testMapProperty() {
        val context = ApplicationContext.run(mapOf("text.other-properties.yyy.zzz" to 3, "test.other-properties.yyy.xxx" to 2, "test.properties.yyy.yyy" to 3))
        val config = context.getBean(ConfigProps::class.java)
        assertTrue(config.otherProperties?.containsKey("yyy") ?: false)
        context.close()
    }

    @Test
    fun testMapPropertySetter() {
        val context = ApplicationContext.run(mapOf("text.setter-properties.yyy.zzz" to 3, "test.setter-properties.yyy.xxx" to 2, "test.properties.yyy.yyy" to 3))
        val config = context.getBean(ConfigProps::class.java)
        assertTrue(config.getSetterProperties()?.containsKey("yyy") ?: false)
        context.close()
    }
}
