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
package io.micronaut.http.graal;

import com.oracle.svm.core.annotate.AutomaticFeature;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequestFactory;
import io.micronaut.http.HttpResponseFactory;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

@Internal
@AutomaticFeature
public class HttpFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        System.out.println("XXX HttpRequestFactory: " + HttpRequestFactory.INSTANCE);
        System.out.println("XXX HttpResponseFactory: " + HttpResponseFactory.INSTANCE);
        RuntimeClassInitialization.initializeAtBuildTime(HttpRequestFactory.class, HttpResponseFactory.class);
    }

}

