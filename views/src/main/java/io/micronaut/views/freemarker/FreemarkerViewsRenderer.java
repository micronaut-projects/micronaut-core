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

package io.micronaut.views.freemarker;

import static freemarker.core.Configurable.*;
import static freemarker.core.Configurable.AUTO_IMPORT_KEY;
import static freemarker.core.Configurable.AUTO_INCLUDE_KEY;
import static freemarker.template.Configuration.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.inject.Singleton;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.io.Writable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.views.ViewsConfiguration;
import io.micronaut.views.ViewsRenderer;
import io.micronaut.views.exceptions.ViewRenderingException;

/**
 * Renders Views with FreeMarker Java template engine.
 *
 * @author Jerónimo López
 * @see <a href= "https://freemarker.apache.org/">freemarker.apache.org</a>
 * @since 1.1
 */
@Produces(MediaType.TEXT_HTML)
@Requires(property = FreemarkerViewsRendererConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Requires(classes = Configuration.class)
@Singleton
public class FreemarkerViewsRenderer implements ViewsRenderer {

    protected final ViewsConfiguration viewsConfiguration;
    protected final FreemarkerViewsRendererConfiguration freemarkerMicronautConfiguration;
    protected final Configuration freemarkerConfiguration;
    protected final String extension;

    /**
     * @param viewsConfiguration               Views Configuration
     * @param freemarkerMicronautConfiguration Freemarker Configuration
     * @throws TemplateException               When template doesn't exist or can not be parsed
     */
    FreemarkerViewsRenderer(ViewsConfiguration viewsConfiguration,
            FreemarkerViewsRendererConfiguration freemarkerMicronautConfiguration) throws TemplateException {
        this.viewsConfiguration = viewsConfiguration;
        this.freemarkerMicronautConfiguration = freemarkerMicronautConfiguration;
        this.freemarkerConfiguration = createConfiguration(freemarkerMicronautConfiguration);
        this.extension = extension(freemarkerMicronautConfiguration);
    }

    @Override
    public Writable render(String view, @Nullable Object data) {
        return (writer) -> {
            Map<String, Object> context = context(data);
            String viewName = viewName(view);
            Template template = freemarkerConfiguration.getTemplate(viewName);
            try {
                template.process(context, writer);
            } catch (TemplateException e) {
                throw new ViewRenderingException(
                        "Error rendering Freemarker view [" + viewName + "]: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public boolean exists(String view) {
        try {
            freemarkerConfiguration.getTemplate(viewName(view));
        } catch (ParseException | MalformedTemplateNameException e) {
            return true;
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private Map<String, Object> context(@Nullable Object data) {
        if (data == null) {
            return new HashMap<>();
        }
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return BeanMap.of(data);
    }

    private String viewName(String name) {
        if (!name.endsWith(extension)) {
            return name + extension;
        }
        return name;
    }

    private String extension(FreemarkerViewsRendererConfiguration freemarkerMicronautConfiguration) {
        return EXTENSION_SEPARATOR + freemarkerMicronautConfiguration.getDefaultExtension();
    }

    private Configuration createConfiguration(FreemarkerViewsRendererConfiguration micronautConfig)
            throws TemplateException {
        
        Map<String, Supplier<String>> mapper = createConfigurationMapper(micronautConfig);
        
        String incompatibleImprovements = micronautConfig.getIncompatibleImprovements();
        Version version = DEFAULT_INCOMPATIBLE_IMPROVEMENTS;
        if (incompatibleImprovements != null) {
            version = new Version(incompatibleImprovements);
        }
        Configuration freemarkerConfiguration = new Configuration(version);
        freemarkerConfiguration.setClassForTemplateLoading(this.getClass(), "/" + viewsConfiguration.getFolder());

        for (Entry<String, Supplier<String>> mapEntry : mapper.entrySet()) {
            String value = mapEntry.getValue().get();
            if (value != null) {
                freemarkerConfiguration.setSetting(mapEntry.getKey(), value);
            }
        }
        return freemarkerConfiguration;
    }
    
    private Map<String, Supplier<String>> createConfigurationMapper(
            FreemarkerViewsRendererConfiguration micronautConfig) {
        Map<String, Supplier<String>> mapper = new HashMap<>();
        mapper.put(LOCALE_KEY, micronautConfig::getLocale);
        mapper.put(NUMBER_FORMAT_KEY, micronautConfig::getNumberFormat);
        mapper.put(CUSTOM_NUMBER_FORMATS_KEY, micronautConfig::getCustomNumberFormats);
        mapper.put(TIME_FORMAT_KEY, micronautConfig::getTimeFormat);
        mapper.put(DATE_FORMAT_KEY, micronautConfig::getDateFormat);
        mapper.put(CUSTOM_DATE_FORMATS_KEY, micronautConfig::getCustomDateFormats);
        mapper.put(DATETIME_FORMAT_KEY, micronautConfig::getDatetimeFormat);
        mapper.put(TIME_ZONE_KEY, micronautConfig::getTimeZone);
        mapper.put(SQL_DATE_AND_TIME_TIME_ZONE_KEY, micronautConfig::getSqlDateAndTimeTimeZone);
        mapper.put(CLASSIC_COMPATIBLE_KEY, micronautConfig::getClassicCompatible);
        mapper.put(TEMPLATE_EXCEPTION_HANDLER_KEY, micronautConfig::getTemplateExceptionHandler);
        mapper.put(ATTEMPT_EXCEPTION_REPORTER_KEY, micronautConfig::getAttemptExceptionReporter);
        mapper.put(ARITHMETIC_ENGINE_KEY, micronautConfig::getArithmeticEngine);
        mapper.put(OBJECT_WRAPPER_KEY, micronautConfig::getObjectWrapper);
        mapper.put(BOOLEAN_FORMAT_KEY, micronautConfig::getBooleanFormat);
        mapper.put(OUTPUT_ENCODING_KEY, micronautConfig::getOutputEncoding);
        mapper.put(URL_ESCAPING_CHARSET_KEY, micronautConfig::getUrlEscapingCharset);
        mapper.put(AUTO_FLUSH_KEY, micronautConfig::getAutoFlush);
        mapper.put(NEW_BUILTIN_CLASS_RESOLVER_KEY, micronautConfig::getNewBuiltinClassResolver);
        mapper.put(SHOW_ERROR_TIPS_KEY, micronautConfig::getShowErrorTips);
        mapper.put(API_BUILTIN_ENABLED_KEY, micronautConfig::getApiBuiltinEnabled);
        mapper.put(LOG_TEMPLATE_EXCEPTIONS_KEY, micronautConfig::getLogTemplateExceptions);
        mapper.put(WRAP_UNCHECKED_EXCEPTIONS_KEY, micronautConfig::getWrapUncheckedExceptions);
        mapper.put(LAZY_IMPORTS_KEY, micronautConfig::getLazyImports);
        mapper.put(LAZY_AUTO_IMPORTS_KEY, micronautConfig::getLazyAutoImports);
        mapper.put(AUTO_IMPORT_KEY, micronautConfig::getAutoImport);
        mapper.put(AUTO_INCLUDE_KEY, micronautConfig::getAutoInclude);
        mapper.put(DEFAULT_ENCODING_KEY, micronautConfig::getDefaultEncoding);
        mapper.put(LOCALIZED_LOOKUP_KEY, micronautConfig::getLocalizedLookup);
        mapper.put(WHITESPACE_STRIPPING_KEY, micronautConfig::getWhitespaceStripping);
        mapper.put(REGISTERED_CUSTOM_OUTPUT_FORMATS_KEY, micronautConfig::getRegisteredCustomOutputFormats);
        mapper.put(AUTO_ESCAPING_POLICY_KEY, micronautConfig::getAutoEscapingPolicy);
        mapper.put(OUTPUT_FORMAT_KEY, micronautConfig::getOutputFormat);
        mapper.put(RECOGNIZE_STANDARD_FILE_EXTENSIONS_KEY, micronautConfig::getRecognizeStandardFileExtensions);
        mapper.put(CACHE_STORAGE_KEY, micronautConfig::getCacheStorage);
        mapper.put(TEMPLATE_UPDATE_DELAY_KEY, micronautConfig::getTemplateUpdateDelay);
        mapper.put(TAG_SYNTAX_KEY, micronautConfig::getTagSyntax);
        mapper.put(INTERPOLATION_SYNTAX_KEY, micronautConfig::getInterpolationSyntax);
        mapper.put(NAMING_CONVENTION_KEY, micronautConfig::getNamingConvention);
        mapper.put(TAB_SIZE_KEY, micronautConfig::getTabSize);
        mapper.put(TEMPLATE_LOADER_KEY, micronautConfig::getTemplateLoader);
        mapper.put(TEMPLATE_LOOKUP_STRATEGY_KEY, micronautConfig::getTemplateLookupStrategy);
        mapper.put(TEMPLATE_NAME_FORMAT_KEY, micronautConfig::getTemplateNameFormat);
        mapper.put(TEMPLATE_CONFIGURATIONS_KEY, micronautConfig::getTemplateConfigurations);
        return mapper;
    }

}
