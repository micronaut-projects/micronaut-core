/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev.compile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class KotlinDeclarationTextTest {

    private static final String SOURCE = """
        @file:JvmName("Owners")
        package example

        /**
         * Finds owners. /* nested */
         */
        @Controller("/owners")
        class OwnerController(@param:Named("db") private val repository: Repository) {
            // @NotAnAnnotation in a comment
            /* @NotOneEither */
            @Get(uri = "/{id}", produces = [")", "]"])
            fun show(@PathVariable id: Long): String {
                val email = "someone@example.com"
                val at = '@'
                val text = \"""raw @Raw ${ id }\"""
                listOf(1).forEach { if (it > 0) return@forEach }
                loop@ for (i in 0..1) { break@loop }
                return "${'$'}{id} ${ repository.find(id) }"
            }
        }
        """;

    @Test
    void theDocumentationAndTheAnnotationsAreTheTextInTheOrderTheyAppear() {
        assertEquals("""
            @file:JvmName("Owners")
            /**
             * Finds owners. /* nested */
             */
            @Controller("/owners")
            @param:Named("db")
            @Get(uri = "/{id}", produces = [")", "]"])
            @PathVariable
            """, KotlinDeclarationText.of(SOURCE));
    }

    @Test
    void aChangeToABodyLeavesTheTextAsItWas() {
        String edited = SOURCE.replace("someone@example.com", "other@example.com").replace("forEach { if (it > 0)", "forEach { if (it > 1)")
            .replace("// @NotAnAnnotation in a comment", "// edited");
        assertEquals(KotlinDeclarationText.of(SOURCE), KotlinDeclarationText.of(edited));
    }

    @Test
    void aChangeToTheDocumentationOrToAnAnnotationChangesTheText() {
        String text = KotlinDeclarationText.of(SOURCE);
        assertNotEquals(text, KotlinDeclarationText.of(SOURCE.replace("Finds owners.", "Finds the owners.")));
        assertNotEquals(text, KotlinDeclarationText.of(SOURCE.replace("@Get(uri = \"/{id}\"", "@Get(uri = \"/{ownerId}\"")));
        assertNotEquals(text, KotlinDeclarationText.of(SOURCE.replace("val at = '@'", "@Suppress(\"UNUSED\") val at = '@'")));
    }
}
