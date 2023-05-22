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
package io.micronaut.inject.requires

import io.micronaut.context.ApplicationContext
import junit.framework.TestCase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequiresSdkSpec {

    @Test
    fun testRequiresKotlinSDKworks() {
        val context = ApplicationContext.run()
        assertFalse(context.containsBean(RequiresFuture::class.java))
        assertTrue(context.containsBean(RequiresOld::class.java))
        context.close()
    }

}