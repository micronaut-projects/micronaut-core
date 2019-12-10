/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.xml.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jackson.codec.AbstractJacksonMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * A jackson based {@link io.micronaut.http.codec.MediaTypeCodec} that handles XML requests/responses.
 *
 * @since 1.3
 */
@Named("xml")
@Singleton
@BootstrapContextCompatible
public class XmlMediaTypeCodec extends AbstractJacksonMediaTypeCodec {

    public static final String CONFIGURATION_QUALIFIER = "xml";

    /**
     * @param xmlMapper                Object mapper for xml. If null, retrieved from beanContext
     * @param applicationConfiguration The common application configurations
     * @param beanContext              Bean context that will be used to retrieve object mapper if one was not provided
     * @param codecConfiguration       The configuration for the codec
     */
    @Inject
    public XmlMediaTypeCodec(@Nullable @Parameter ObjectMapper xmlMapper,
                             ApplicationConfiguration applicationConfiguration,
                             BeanContext beanContext,
                             @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        super(setupXmlMapper(xmlMapper, beanContext), applicationConfiguration, codecConfiguration,
              MediaType.APPLICATION_XML_TYPE);
    }

    private static ObjectMapper setupXmlMapper(ObjectMapper mapper, BeanContext beanContext) {
        mapper = Optional.ofNullable(mapper).orElse(beanContext.getBean(ObjectMapper.class, Qualifiers.byName("xml"))).copy();
        mapper.registerModule(new JaxbAnnotationModule());

        return mapper;
    }
}
