package org.particleframework.inject.generics;

import org.particleframework.core.convert.ConversionService;

import javax.inject.Inject;

public class WildCardInject {
    // tests injecting field
    @Inject
    protected ConversionService<?> conversionService;

    // tests injecting constructor
    public WildCardInject(ConversionService<?> conversionService) {
    }

    // tests injection method
    @Inject
    public void setConversionService(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }
}
