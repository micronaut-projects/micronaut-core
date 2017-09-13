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
package org.particleframework.http.util;

import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;

/**
 * Utility methods for HTTP handling
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpUtil {

    /**
     * Return whether the given request features {@link MediaType#APPLICATION_FORM_URLENCODED} or {@link MediaType#MULTIPART_FORM_DATA}
     *
     * @param request The request
     * @return True if it is form data
     */
    public static boolean isFormData(HttpRequest request) {
        MediaType contentType = request.getContentType();
        return contentType != null && (contentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) || contentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE));
    }
}
