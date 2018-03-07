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
package example;

import io.micronaut.function.executor.FunctionInitializer;

import javax.inject.Inject;
import java.io.IOException;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class UpperCaseTitleFunction extends FunctionInitializer {

    @Inject UpperCaseTitleService upperCaseTitleService;

    public Book toUpperCase(Book book) {
        return upperCaseTitleService.toUpperCase(book);
    }

    public static void main(String...args) throws IOException {
        UpperCaseTitleFunction function = new UpperCaseTitleFunction();
        function.run(args, (context)-> function.toUpperCase(context.get(Book.class)));
    }
}
