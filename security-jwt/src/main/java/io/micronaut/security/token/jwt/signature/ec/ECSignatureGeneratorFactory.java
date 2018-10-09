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

package io.micronaut.security.token.jwt.signature.ec;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration;

/**
 * Creates {@link io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration} for each {@link ECSignatureGeneratorConfiguration} bean.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Factory
public class ECSignatureGeneratorFactory {

    /**
     *
     * @param configuration {@link ECSignatureGeneratorConfiguration} bean.
     * @return The {@link SignatureGeneratorConfiguration}
     */
    @EachBean(ECSignatureGeneratorConfiguration.class)
    public SignatureGeneratorConfiguration signatureGeneratorConfiguration(ECSignatureGeneratorConfiguration configuration) {
        return new ECSignatureGenerator(configuration);
    }
}
