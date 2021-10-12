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
package io.micronaut.http.multipart;

/**
 * Represents a completed part of a multipart request.
 * <p>
 * When used as an argument to an {@link io.micronaut.http.annotation.Controller} instance method, the route
 * is not executed until the part has been fully received. Provides access to metadata about the file as
 * well as the contents.
 *
 * @author Zachary Klein
 * @since 1.0.0
 */
public interface CompletedFileUpload extends FileUpload, CompletedPart {
}
