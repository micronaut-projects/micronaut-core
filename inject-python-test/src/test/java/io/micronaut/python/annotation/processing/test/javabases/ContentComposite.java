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
package io.micronaut.python.annotation.processing.test.javabases;

/**
 * A generic base whose protected hooks return and take the bounded type variable, like Vaadin's
 * {@code Composite<T extends Component>} and its {@code protected T initContent()}.
 *
 * @param <T> The content type
 */
public class ContentComposite<T extends ContentComposite.Part> {

    private T content;

    protected T initContent() {
        return null;
    }

    protected String label(T part) {
        return part.describe();
    }

    public T getContent() {
        if (content == null) {
            content = initContent();
        }
        return content;
    }

    public String describeContent() {
        return label(getContent());
    }

    /**
     * The bound of the content type.
     */
    public static class Part {

        public String describe() {
            return "part";
        }
    }

    /**
     * A part the Python overrides return.
     */
    public static class Panel extends Part {

        private final String title;

        public Panel(String title) {
            this.title = title;
        }

        @Override
        public String describe() {
            return "panel:" + title;
        }
    }
}
