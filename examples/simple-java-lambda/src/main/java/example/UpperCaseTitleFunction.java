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


import org.particleframework.function.Function;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Function("uppercase")
public class UpperCaseTitleFunction implements java.util.function.Function<Book, Book> {
    @Override
    public Book apply(Book book) {
        String title = book.getTitle();
        if(title != null) {
            book.setTitle(title.toUpperCase());
        }
        return book;
    }
}
