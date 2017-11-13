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
package org.particleframework.inject.bind;

import org.particleframework.core.bind.ArgumentBinder;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.type.Argument;
import org.particleframework.core.value.PropertyResolver;

import java.net.URI;
import java.util.Optional;

/**
 * Example of compile time generated binder
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class TestAuthorBinder implements ArgumentBinder<Author, PropertyResolver> {
    @Override
    public Optional<Author> bind(ArgumentConversionContext<Author> context, PropertyResolver source) {
        Author author = new Author();
        if(source.containsProperty("name")) {
            ArgumentConversionContext<String> conversionContext = context.with(Argument.of(String.class, "name"));
            Optional<String> converted = source.getProperty("name", String.class, conversionContext);
            if(converted.isPresent()) {
                author.setName(converted.get());
            }
        }
        if(source.containsProperty("website")) {
            ArgumentConversionContext<Optional> conversionContext = context.with(
                    Argument.of(Optional.class, "website",
                            Argument.of(URI.class, "T")
                    )
            );
            Optional converted = source.getProperty("website", Optional.class, conversionContext);
            if(converted.isPresent()) {
                author.setWebsite((Optional<URI>) converted.get());
            }
        }
        return Optional.of(author);
    }
}
