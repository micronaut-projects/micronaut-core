/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

@Controller("/api")
public class BookmarkController {

    @Get("/bookmarks{?offset,max,sort,order}")
    public HttpStatus index(@PositiveOrZero @Nullable Integer offset,
                            @Positive @Nullable Integer max,
                            @Nullable @Pattern(regexp = "name|href|title") String sort,
                            @Nullable @Pattern(regexp = "asc|desc|ASC|DESC") String order) {
        return HttpStatus.OK;
    }

    @Get("/bookmarks/list{?paginationCommand*}")
    public PaginationCommand list(@Valid @Nullable PaginationCommand paginationCommand) {
        return paginationCommand;
    }

}
