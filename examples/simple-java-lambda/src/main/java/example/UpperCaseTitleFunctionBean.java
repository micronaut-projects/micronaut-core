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


import org.particleframework.function.FunctionBean;

import java.util.function.Function;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@FunctionBean("uppercase")
public class UpperCaseTitleFunctionBean implements Function<Book, Book> {
    UpperCaseTitleService upperCaseTitleService;

    public UpperCaseTitleFunctionBean(UpperCaseTitleService upperCaseTitleService) {
        this.upperCaseTitleService = upperCaseTitleService;
    }

    @Override
    public Book apply(Book book) {
        return upperCaseTitleService.toUpperCase(book);
    }
}
