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

import io.micronaut.core.util.Toggleable;

/**
 * Configuration for {@link FreemarkerViewsRenderer}.
 * 
 * All configured properties are extracted from {@link freemarker.template.Configuration} and
 * {@link freemarker.core.Configurable}. All Freemarker properties names are reused in the micronaut
 * configuration.
 * 
 * If a value is not declared and is null, the default configuration from Freemarker is used. The expected
 * format of each value is the same from Freemarker, and no conversion or validation is done by Micronaut.
 * 
 * All Freemarker configuration documentation is published in their
 * <a href="https://freemarker.apache.org/docs/pgui_config.html">site</a>.
 *
 * @author Jerónimo López
 * @since 1.0.2
 */
public interface FreemarkerViewsRendererConfiguration extends Toggleable {

    /**
     * @return Default extension for templates
     */
    String getDefaultExtension();

    /*
     * Configurations from {@link freemarker.core.Configurable}
     */

    /**
     * @return the locale used for number and date formatting
     * @see freemarker.core.Configurable#setLocale()
     */
    String getLocale();

    /**
     * @return the default number format used to convert numbers to strings
     * @see freemarker.core.Configurable#setNumberFormat()
     */
    String getNumberFormat();

    /**
     * @return associated names with number formatter factories
     * @see freemarker.core.Configurable#setCustomNumberFormats()
     */
    String getCustomNumberFormats();

    /**
     * @return the format used to convert {@link java.util.Date}s that are time values to string
     * @see freemarker.core.Configurable#setTimeFormat()
     */
    String getTimeFormat();

    /**
     * @return the format used to convert {@link java.util.Date}s that are date-only values to string
     * @see freemarker.core.Configurable#setDateFormat()
     */
    String getDateFormat();

    /**
     * @return associated names with date formatter factories
     * @see freemarker.core.Configurable#setCustomDateFormats()
     */
    String getCustomDateFormats();

    /**
     * @return the format used to convert {@link java.util.Date}s that are date-time values to string
     * @see freemarker.core.Configurable#setDateTimeFormat()
     */
    String getDatetimeFormat();

    /**
     * @return the time zone to use when formatting date/time values
     * @see freemarker.core.Configurable#setTimeZone()
     */
    String getTimeZone();

    /**
     * @return the time zone used when dealing with Date and Time values
     * @see freemarker.core.Configurable#setSQLDateAndTimeTimeZone()
     */
    String getSqlDateAndTimeTimeZone();

    /**
     * @return get the "Classic Compatible" mode
     * @see freemarker.core.Configurable#setClassicCompatible()
     */
    String getClassicCompatible();

    /**
     * @return the exception handler used to handle exceptions occurring inside templates
     * @see freemarker.core.Configurable#setTemplateExceptionHandler()
     */
    String getTemplateExceptionHandler();

    /**
     * @return how exceptions handled by an {@code #attempt} blocks
     * @see freemarker.core.Configurable#setAttemptExceptionReporter()
     */
    String getAttemptExceptionReporter();

    /**
     * @return the arithmetic engine used to perform arithmetic operations
     * @see freemarker.core.Configurable#setArithmeticEngine()
     */
    String getArithmeticEngine();

    /**
     * @return the object wrapper used to wrap objects to TemplateModels
     * @see freemarker.core.Configurable#setObjectWrapper()
     */
    String getObjectWrapper();

    /**
     * @return the string value for the boolean {@code true} and {@code false} values, intended for human audience
     * @see freemarker.core.Configurable#setBooleanFormat()
     */
    String getBooleanFormat();

    /**
     * @return the charset used for the output
     * @see freemarker.core.Configurable#setOutputEncoding()
     */
    String getOutputEncoding();

    /**
     * @return the URL escaping charset
     * @see freemarker.core.Configurable#setURLEscapingCharset()
     */
    String getUrlEscapingCharset();

    /**
     * @return whether the output {@link Writer} is automatically flushed at the end of Template render
     * @see freemarker.core.Configurable#setAutoFlush()
     */
    String getAutoFlush();

    /**
     * @return the TemplateClassResolver that is used when the <code>new</code> built-in is called in a template
     * @see freemarker.core.Configurable#setNewBuiltinClassResolver()
     */
    String getNewBuiltinClassResolver();

    /**
     * @return if tips should be shown in error messages of errors arising during template processing.
     * @see freemarker.core.Configurable#setShowErrorTips()
     */
    String getShowErrorTips();

    /**
     * @return if {@code ?api} can be used in templates
     * @see freemarker.core.Configurable#setAPIBuiltinEnabled()
     */
    String getApiBuiltinEnabled();

    /**
     * @return if TemplateExceptions thrown by template processing are logged by FreeMarker or not
     * @see freemarker.core.Configurable#setLogTemplateExceptions()
     */
    String getLogTemplateExceptions();

    /**
     * @return if unchecked exceptions thrown during expression evaluation or during executing custom directives
     *         will be wrapped into TemplateExceptions, or will bubble up to the caller
     * @see freemarker.core.Configurable#setWrapUncheckedExceptions()
     */
    String getWrapUncheckedExceptions();

    /**
     * @return if {@code <#import ...>} should delay the loading and processing of the imported templates
     * @see freemarker.core.Configurable#setLazyImports()
     */
    String getLazyImports();

    /**
     * @return lazy auto imports configuration
     * @see freemarker.core.Configurable#setLazyAutoImports()
     */
    String getLazyAutoImports();

    /**
     * @return autoimport configuration
     * @see freemarker.core.Configurable#setAutoImports()
     */
    String getAutoImport();

    /**
     * @return autoinclude configuration
     * @see freemarker.core.Configurable#setAutoIncludes()
     */
    String getAutoInclude();

    /*
     * Configurations from {@link freemarker.template.Configuration}
     */

    /**
     * @return Default encoding for templates
     * @see freemarker.template.Configuration#setDefaultEncoding()
     */
    String getDefaultEncoding();

    /**
     * @return if localized template lookup is enabled or not
     * @see freemarker.template.Configuration#setLocalizedLookup()
     */
    String getLocalizedLookup();

    /**
     * @return if the FTL parser will try to remove superfluous white-space around certain FTL tags
     * @see freemarker.template.Configuration#setWhitespaceStripping()
     */
    String getWhitespaceStripping();

    /**
     * @return the custom output formats that can be referred by their unique name from templates
     * @see freemarker.template.Configuration#setRegisteredCustomOutputFormats()
     */
    String getRegisteredCustomOutputFormats();

    /**
     * @return auto escaping policy
     * @see freemarker.template.Configuration#setAutoEscapingPolicy()
     */
    String getAutoEscapingPolicy();

    /**
     * @return the default output format
     * @see freemarker.template.Configuration#setOutputFormat()
     */
    String getOutputFormat();

    /**
     * @return if the "file" extension part of the source name will influence certain parsing settings
     * @see freemarker.template.Configuration#setRecognizeStandardFileExtensions()
     */
    String getRecognizeStandardFileExtensions();

    /**
     * @return the CacheStorage used for caching Templates
     * @see freemarker.template.Configuration#setCacheStorage()
     */
    String getCacheStorage();

    /**
     * @return the time that must elapse before checking whether there is a newer version of a template "file"
     *         than the cached one
     * @see freemarker.template.Configuration#setTemplateUpdateDelayMilliseconds()
     */
    String getTemplateUpdateDelay();

    /**
     * @return the tag syntax of the template files that has no {@code #ftl} header to decide that
     * @see freemarker.template.Configuration#setTagSyntax()
     */
    String getTagSyntax();

    /**
     * @return the interpolation syntax of the template files
     * @see freemarker.template.Configuration#setInterpolationSyntax()
     */
    String getInterpolationSyntax();

    /**
     * @return the naming convention used for the identifiers that are part of the template language
     * @see freemarker.template.Configuration#setNamingConvention()
     */
    String getNamingConvention();

    /**
     * @return the assumed display width of the tab character (ASCII 9), which influences the column number shown
     *         in error messages
     * @see freemarker.template.Configuration#setTabSize()
     */
    String getTabSize();

    /**
     * @return which the version of the non-backward-compatible bugfixes/improvements should be enabled
     * @see freemarker.template.Configuration#setIncompatibleImprovements()
     */
    String getIncompatibleImprovements();

    /**
     * @return the TemplateLoader that is used to look up and load templates
     * @see freemarker.template.Configuration#setTemplateLoader()
     */
    String getTemplateLoader();

    /**
     * @return the TemplateLookupStrategy that is used to look up templates based on the requested name
     * @see freemarker.template.Configuration#setTemplateLookupStrategy()
     */
    String getTemplateLookupStrategy();

    /**
     * @return the template name format used. The setup follows the properties configuration explained in
     *         <a href= "https://freemarker.apache.org/docs/pgui_config_templateloading.html#autoid_42">
     *         freemarker documentation</a>
     * @see freemarker.template.Configuration#setTemplateNameFormat()
     */
    String getTemplateNameFormat();

    /**
     * @return the TemplateConfigurationFactory that will configure individual templates where their settings
     *         differ from those coming from the common Configuration object. The setup follows the properties
     *         configuration explained in
     *         <a href= "https://freemarker.apache.org/docs/pgui_config_templateconfigurations.html"> freemarker
     *         documentation</a>
     * @see freemarker.template.Configuration#setTemplateConfigurations()
     */
    String getTemplateConfigurations();

}
