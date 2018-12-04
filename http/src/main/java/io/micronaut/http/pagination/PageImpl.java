/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.pagination;

/**
 * Default implementation of {@see Pageable} interface.
 *
 * @author boros
 * @since 1.0.1
 */
public class PageImpl implements Pageable {

    private final long offset;
    private final int size;

    /**
     * Creates a Pageable object.
     *
     * @param offset number of the page
     * @param size   size of the page
     * @throws IllegalArgumentException if params have incorrect bounds
     */
    public PageImpl(long offset, int size) {

        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be less then 0");
        }

        if (size < 1) {
            throw new IllegalArgumentException("Page size cannot be less then 1");
        }

        this.offset = offset;
        this.size = size;
    }

    /**
     * @return the collection offset
     */
    public long getOffset() {
        return offset;
    }

    /**
     * @return the page size
     */
    public int getSize() {
        return size;
    }

}
