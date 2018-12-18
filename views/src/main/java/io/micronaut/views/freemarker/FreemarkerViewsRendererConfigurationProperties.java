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

import freemarker.template.Configuration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.views.ViewsConfigurationProperties;

/**
 * {@link ConfigurationProperties} implementation of {@link FreemarkerViewsRendererConfiguration}.
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
 * @since 1.1
 */
@Requires(classes = freemarker.template.Configuration.class)
@ConfigurationProperties(FreemarkerViewsRendererConfigurationProperties.PREFIX)
public class FreemarkerViewsRendererConfigurationProperties implements FreemarkerViewsRendererConfiguration {

    public static final String PREFIX = ViewsConfigurationProperties.PREFIX + ".freemarker";

    /**
     * The default extension.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_EXTENSION = "ftl";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default freemarker incompatible improvements version.
     * 
     * @see freemarker.template.Configuration#Configuration(freemarker.template.Version)
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_INCOMPATIBLE_IMPROVEMENTS = Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS
            .toString();

    private boolean enabled = DEFAULT_ENABLED;

    private String defaultExtension = DEFAULT_EXTENSION;

    /*
     * Configurations from {@link freemarker.core.Configurable}
     */
    private String locale;

    private String numberFormat;

    private String customNumberFormats;

    private String timeFormat;

    private String dateFormat;

    private String customDateFormats;

    private String datetimeFormat;

    private String timeZone;

    private String sqlDateAndTimeTimeZone;

    private String classicCompatible;

    private String templateExceptionHandler;

    private String attemptExceptionReporter;

    private String arithmeticEngine;

    private String objectWrapper;

    private String booleanFormat;

    private String outputEncoding;

    private String urlEscapingCharset;

    private String autoFlush;

    private String newBuiltinClassResolver;

    private String showErrorTips;

    private String apiBuiltinEnabled;

    private String logTemplateExceptions;

    private String wrapUncheckedExceptions;

    private String lazyImports;

    private String lazyAutoImports;

    private String autoImport;

    private String autoInclude;

    /*
     * Configurations from {@link freemarker.template.Configuration}
     */
    private String defaultEncoding;

    private String localizedLookup;

    private String whitespaceStripping;

    private String registeredCustomOutputFormats;

    private String autoEscapingPolicy;

    private String outputFormat;

    private String recognizeStandardFileExtensions;

    private String cacheStorage;

    private String templateUpdateDelay;

    private String tagSyntax;

    private String interpolationSyntax;

    private String namingConvention;

    private String tabSize;

    private String incompatibleImprovements;

    private String templateLoader;

    private String templateLookupStrategy;

    private String templateNameFormat;

    private String templateConfigurations;

    /**
     * enabled getter.
     *
     * @return boolean flag indicating whether {@link FreemarkerViewsRenderer} is enabled.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * @return Default extension for templates. By default {@value #DEFAULT_EXTENSION}.
     */
    @Override
    public String getDefaultExtension() {
        return defaultExtension;
    }

    /**
     * @return the locale used for number and date formatting
     * @see freemarker.core.Configurable#setLocale()
     */
    @Override
    public String getLocale() {
        return locale;
    }

    /**
     * @return the default number format used to convert numbers to strings
     * @see freemarker.core.Configurable#setNumberFormat()
     */
    @Override
    public String getNumberFormat() {
        return numberFormat;
    }

    /**
     * @return associated names with number formatter factories
     * @see freemarker.core.Configurable#setCustomNumberFormats()
     */
    @Override
    public String getCustomNumberFormats() {
        return customNumberFormats;
    }

    /**
     * @return the format used to convert {@link java.util.Date}s that are time values to string
     * @see freemarker.core.Configurable#setTimeFormat()
     */
    @Override
    public String getTimeFormat() {
        return timeFormat;
    }

    /**
     * @return the format used to convert {@link java.util.Date}s that are date-only values to string
     * @see freemarker.core.Configurable#setDateFormat()
     */
    @Override
    public String getDateFormat() {
        return dateFormat;
    }

    /**
     * @return associated names with date formatter factories
     * @see freemarker.core.Configurable#setCustomDateFormats()
     */
    @Override
    public String getCustomDateFormats() {
        return customDateFormats;
    }

    /**
     * @return the format used to convert {@link java.util.Date}s that are date-time values to string
     * @see freemarker.core.Configurable#setDateTimeFormat()
     */
    @Override
    public String getDatetimeFormat() {
        return datetimeFormat;
    }

    /**
     * @return the time zone to use when formatting date/time values
     * @see freemarker.core.Configurable#setTimeZone()
     */
    @Override
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * @return the time zone used when dealing with SQL Date and Time values
     * @see freemarker.core.Configurable#setSQLDateAndTimeTimeZone()
     */
    @Override
    public String getSqlDateAndTimeTimeZone() {
        return sqlDateAndTimeTimeZone;
    }

    /**
     * @return get the "Classic Compatible" mode
     * @see freemarker.core.Configurable#setClassicCompatible()
     */
    @Override
    public String getClassicCompatible() {
        return classicCompatible;
    }

    /**
     * @return the exception handler used to handle exceptions occurring inside templates
     * @see freemarker.core.Configurable#setTemplateExceptionHandler()
     */
    @Override
    public String getTemplateExceptionHandler() {
        return templateExceptionHandler;
    }

    /**
     * @return how exceptions handled by an {@code #attempt} blocks
     * @see freemarker.core.Configurable#setAttemptExceptionReporter()
     */
    @Override
    public String getAttemptExceptionReporter() {
        return attemptExceptionReporter;
    }

    /**
     * @return the arithmetic engine used to perform arithmetic operations
     * @see freemarker.core.Configurable#setArithmeticEngine()
     */
    @Override
    public String getArithmeticEngine() {
        return arithmeticEngine;
    }

    /**
     * @return the object wrapper used to wrap objects to TemplateModels
     * @see freemarker.core.Configurable#setObjectWrapper()
     */
    @Override
    public String getObjectWrapper() {
        return objectWrapper;
    }

    /**
     * @return the string value for the boolean {@code true} and {@code false} values, intended for human audience
     * @see freemarker.core.Configurable#setBooleanFormat()
     */
    @Override
    public String getBooleanFormat() {
        return booleanFormat;
    }

    /**
     * @return the charset used for the output
     * @see freemarker.core.Configurable#setOutputEncoding()
     */
    @Override
    public String getOutputEncoding() {
        return outputEncoding;
    }

    /**
     * @return the URL escaping charset
     * @see freemarker.core.Configurable#setURLEscapingCharset()
     */
    @Override
    public String getUrlEscapingCharset() {
        return urlEscapingCharset;
    }

    /**
     * @return whether the output {@link Writer} is automatically flushed at the end of Template render
     * @see freemarker.core.Configurable#setAutoFlush()
     */
    @Override
    public String getAutoFlush() {
        return autoFlush;
    }

    /**
     * @return the TemplateClassResolver that is used when the <code>new</code> built-in is called in a template
     * @see freemarker.core.Configurable#setNewBuiltinClassResolver()
     */
    @Override
    public String getNewBuiltinClassResolver() {
        return newBuiltinClassResolver;
    }

    /**
     * @return if tips should be shown in error messages of errors arising during template processing.
     * @see freemarker.core.Configurable#setShowErrorTips()
     */
    @Override
    public String getShowErrorTips() {
        return showErrorTips;
    }

    /**
     * @return if {@code ?api} can be used in templates
     * @see freemarker.core.Configurable#setAPIBuiltinEnabled()
     */
    @Override
    public String getApiBuiltinEnabled() {
        return apiBuiltinEnabled;
    }

    /**
     * @return if TemplateExceptions thrown by template processing are logged by FreeMarker or not
     * @see freemarker.core.Configurable#setLogTemplateExceptions()
     */
    @Override
    public String getLogTemplateExceptions() {
        return logTemplateExceptions;
    }

    /**
     * @return if unchecked exceptions thrown during expression evaluation or during executing custom directives
     *         will be wrapped into TemplateExceptions, or will bubble up to the caller
     * @see freemarker.core.Configurable#setWrapUncheckedExceptions()
     */
    @Override
    public String getWrapUncheckedExceptions() {
        return wrapUncheckedExceptions;
    }

    /**
     * @return if {@code <#import ...>} should delay the loading and processing of the imported templates
     * @see freemarker.core.Configurable#setLazyImports()
     */
    @Override
    public String getLazyImports() {
        return lazyImports;
    }

    /**
     * @return lazy auto imports configuration
     * @see freemarker.core.Configurable#setLazyAutoImports()
     */
    @Override
    public String getLazyAutoImports() {
        return lazyAutoImports;
    }

    /**
     * @return autoimport configuration
     * @see freemarker.core.Configurable#setAutoImports()
     */
    @Override
    public String getAutoImport() {
        return autoImport;
    }

    /**
     * @return autoinclude configuration
     * @see freemarker.core.Configurable#setAutoIncludes()
     */
    @Override
    public String getAutoInclude() {
        return autoInclude;
    }

    /**
     * @return default encoding for templates
     * @see freemarker.template.Configuration#setDefaultEncoding()
     */
    @Override
    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    /**
     * @return if localized template lookup is enabled or not
     * @see freemarker.template.Configuration#setLocalizedLookup()
     */
    @Override
    public String getLocalizedLookup() {
        return localizedLookup;
    }

    /**
     * @return if the FTL parser will try to remove superfluous white-space around certain FTL tags
     * @see freemarker.template.Configuration#setWhitespaceStripping()
     */
    @Override
    public String getWhitespaceStripping() {
        return whitespaceStripping;
    }

    /**
     * @return the custom output formats that can be referred by their unique name from templates
     * @see freemarker.template.Configuration#setRegisteredCustomOutputFormats()
     */
    @Override
    public String getRegisteredCustomOutputFormats() {
        return registeredCustomOutputFormats;
    }

    /**
     * @return auto escaping policy
     * @see freemarker.template.Configuration#setAutoEscapingPolicy()
     */
    @Override
    public String getAutoEscapingPolicy() {
        return autoEscapingPolicy;
    }

    /**
     * @return the default output format
     * @see freemarker.template.Configuration#setOutputFormat()
     */
    @Override
    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * @return if the "file" extension part of the source name will influence certain parsing settings
     * @see freemarker.template.Configuration#setRecognizeStandardFileExtensions()
     */
    @Override
    public String getRecognizeStandardFileExtensions() {
        return recognizeStandardFileExtensions;
    }

    /**
     * @return the CacheStorage used for caching Templates
     * @see freemarker.template.Configuration#setCacheStorage()
     */
    @Override
    public String getCacheStorage() {
        return cacheStorage;
    }

    /**
     * @return the time that must elapse before checking whether there is a newer version of a template "file"
     *         than the cached one
     * @see freemarker.template.Configuration#setTemplateUpdateDelayMilliseconds()
     */
    @Override
    public String getTemplateUpdateDelay() {
        return templateUpdateDelay;
    }

    /**
     * @return the tag syntax of the template files that has no {@code #ftl} header to decide that
     * @see freemarker.template.Configuration#setTagSyntax()
     */
    @Override
    public String getTagSyntax() {
        return tagSyntax;
    }

    /**
     * @return the interpolation syntax of the template files
     * @see freemarker.template.Configuration#setInterpolationSyntax()
     */
    @Override
    public String getInterpolationSyntax() {
        return interpolationSyntax;
    }

    /**
     * @return the naming convention used for the identifiers that are part of the template language
     * @see freemarker.template.Configuration#setNamingConvention()
     */
    @Override
    public String getNamingConvention() {
        return namingConvention;
    }

    /**
     * @return the assumed display width of the tab character (ASCII 9), which influences the column number shown
     *         in error messages
     * @see freemarker.template.Configuration#setTabSize()
     */
    @Override
    public String getTabSize() {
        return tabSize;
    }

    /**
     * @return which version of the non-backward-compatible bugfixes/improvements should be enabled
     * @see freemarker.template.Configuration#setIncompatibleImprovements()
     */
    @Override
    public String getIncompatibleImprovements() {
        return incompatibleImprovements;
    }

    /**
     * @return the TemplateLoader that is used to look up and load templates
     * @see freemarker.template.Configuration#setTemplateLoader()
     */
    @Override
    public String getTemplateLoader() {
        return templateLoader;
    }

    /**
     * @return the TemplateLookupStrategy that is used to look up templates based on the requested name
     * @see freemarker.template.Configuration#setTemplateLookupStrategy()
     */
    @Override
    public String getTemplateLookupStrategy() {
        return templateLookupStrategy;
    }

    /**
     * @return the template name format used. The setup follows the properties configuration explained in
     *         <a href= "https://freemarker.apache.org/docs/pgui_config_templateloading.html#autoid_42">
     *         freemarker documentation</a>
     * @see freemarker.template.Configuration#setTemplateNameFormat()
     */
    @Override
    public String getTemplateNameFormat() {
        return templateNameFormat;
    }

    /**
     * @return the TemplateConfigurationFactory that will configure individual templates where their settings
     *         differ from those coming from the common Configuration object. The setup follows the properties
     *         configuration explained in
     *         <a href= "https://freemarker.apache.org/docs/pgui_config_templateconfigurations.html"> freemarker
     *         documentation</a>
     * @see freemarker.template.Configuration#setTemplateConfigurations()
     */
    @Override
    public String getTemplateConfigurations() {
        return templateConfigurations;
    }

    /**
     * Whether freemarker views are enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if they are
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the default extension to use for freemarker templates. Default value ({@value #DEFAULT_EXTENSION}).
     *
     * @param defaultExtension The default extension
     */
    public void setDefaultExtension(String defaultExtension) {
        this.defaultExtension = defaultExtension;
    }

    /**
     * @param locale The locale to use for number and date formatting
     */
    public void setLocale(String locale) {
        this.locale = locale;
    }

    /**
     * @param numberFormat The default number format used to convert numbers to strings
     */
    public void setNumberFormat(String numberFormat) {
        this.numberFormat = numberFormat;
    }

    /**
     * @param customNumberFormats The associated names with number formatter factories
     */
    public void setCustomNumberFormats(String customNumberFormats) {
        this.customNumberFormats = customNumberFormats;
    }

    /**
     * @param timeFormat The format used to convert {@link java.util.Date}s that are time values to string
     */
    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    /**
     * @param dateFormat The format used to convert {@link java.util.Date}s that are date-only values to string
     */
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    /**
     * @param customDateFormats The associated names with date formatter factories
     */
    public void setCustomDateFormats(String customDateFormats) {
        this.customDateFormats = customDateFormats;
    }

    /**
     * @param datetimeFormat The format used to convert {@link java.util.Date}s that are date-time values to
     *                       string
     */
    public void setDatetimeFormat(String datetimeFormat) {
        this.datetimeFormat = datetimeFormat;
    }

    /**
     * @param timeZone The time zone to use when formatting date/time values
     */
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * @param sqlDateAndTimeTimeZone The time zone used when dealing with SQL Date and Time values
     */
    public void setSqlDateAndTimeTimeZone(String sqlDateAndTimeTimeZone) {
        this.sqlDateAndTimeTimeZone = sqlDateAndTimeTimeZone;
    }

    /**
     * @param classicCompatible The "Classic Compatible" mode
     */
    public void setClassicCompatible(String classicCompatible) {
        this.classicCompatible = classicCompatible;
    }

    /**
     * @param templateExceptionHandler The exception handler used to handle exceptions occurring inside templates
     */
    public void setTemplateExceptionHandler(String templateExceptionHandler) {
        this.templateExceptionHandler = templateExceptionHandler;
    }

    /**
     * @param attemptExceptionReporter How exceptions handled by an {@code #attempt} blocks
     */
    public void setAttemptExceptionReporter(String attemptExceptionReporter) {
        this.attemptExceptionReporter = attemptExceptionReporter;
    }

    /**
     * @param arithmeticEngine The arithmetic engine used to perform arithmetic operations
     */
    public void setArithmeticEngine(String arithmeticEngine) {
        this.arithmeticEngine = arithmeticEngine;
    }

    /**
     * @param objectWrapper The object wrapper used to wrap objects to TemplateModels
     */
    public void setObjectWrapper(String objectWrapper) {
        this.objectWrapper = objectWrapper;
    }

    /**
     * @param booleanFormat The string value for the boolean {@code true} and {@code false} values, intended for
     *                      human audience
     */
    public void setBooleanFormat(String booleanFormat) {
        this.booleanFormat = booleanFormat;
    }

    /**
     * @param outputEncoding The charset used for the output
     */
    public void setOutputEncoding(String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    /**
     * @param urlEscapingCharset The URL escaping charset
     */
    public void setUrlEscapingCharset(String urlEscapingCharset) {
        this.urlEscapingCharset = urlEscapingCharset;
    }

    /**
     * @param autoFlush Whether the output {@link Writer} is automatically flushed at the end of Template render
     */
    public void setAutoFlush(String autoFlush) {
        this.autoFlush = autoFlush;
    }

    /**
     * @param newBuiltinClassResolver The TemplateClassResolver that is used when the <code>new</code> built-in is
     *                                called in a template
     */
    public void setNewBuiltinClassResolver(String newBuiltinClassResolver) {
        this.newBuiltinClassResolver = newBuiltinClassResolver;
    }

    /**
     * @param showErrorTips If tips should be shown in error messages of errors arising during template
     *                      processing.
     */
    public void setShowErrorTips(String showErrorTips) {
        this.showErrorTips = showErrorTips;
    }

    /**
     * @param apiBuiltinEnabled If {@code ?api} can be used in templates
     */
    public void setApiBuiltinEnabled(String apiBuiltinEnabled) {
        this.apiBuiltinEnabled = apiBuiltinEnabled;
    }

    /**
     * @param logTemplateExceptions If TemplateExceptions thrown by template processing are logged by FreeMarker
     *                              or not
     */
    public void setLogTemplateExceptions(String logTemplateExceptions) {
        this.logTemplateExceptions = logTemplateExceptions;
    }

    /**
     * @param wrapUncheckedExceptions If unchecked exceptions thrown during expression evaluation or during
     *                                executing custom directives will be wrapped into TemplateExceptions, or will
     *                                bubble up to the caller
     */
    public void setWrapUncheckedExceptions(String wrapUncheckedExceptions) {
        this.wrapUncheckedExceptions = wrapUncheckedExceptions;
    }

    /**
     * @param lazyImports If {@code <#import ...>} should delay the loading and processing of the imported
     *                    templates
     */
    public void setLazyImports(String lazyImports) {
        this.lazyImports = lazyImports;
    }

    /**
     * @param lazyAutoImports Lazy auto imports configuration
     */
    public void setLazyAutoImports(String lazyAutoImports) {
        this.lazyAutoImports = lazyAutoImports;
    }

    /**
     * @param autoImport Autoimport configuration
     */
    public void setAutoImport(String autoImport) {
        this.autoImport = autoImport;
    }

    /**
     * @param autoInclude Autoinclude configuration
     */
    public void setAutoInclude(String autoInclude) {
        this.autoInclude = autoInclude;
    }

    /**
     * @param defaultEncoding Default encoding for templates
     */
    public void setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    /**
     * @param localizedLookup If localized template lookup is enabled or not
     */
    public void setLocalizedLookup(String localizedLookup) {
        this.localizedLookup = localizedLookup;
    }

    /**
     * @param whitespaceStripping If the FTL parser will try to remove superfluous white-space around certain FTL
     *                            tags
     */
    public void setWhitespaceStripping(String whitespaceStripping) {
        this.whitespaceStripping = whitespaceStripping;
    }

    /**
     * @param registeredCustomOutputFormats The custom output formats that can be referred by their unique name
     *                                      from templates
     */
    public void setRegisteredCustomOutputFormats(String registeredCustomOutputFormats) {
        this.registeredCustomOutputFormats = registeredCustomOutputFormats;
    }

    /**
     * @param autoEscapingPolicy Auto escaping policy
     */
    public void setAutoEscapingPolicy(String autoEscapingPolicy) {
        this.autoEscapingPolicy = autoEscapingPolicy;
    }

    /**
     * @param outputFormat The default output format
     */
    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    /**
     * @param recognizeStandardFileExtensions If the "file" extension part of the source name will influence
     *                                        certain parsing settings
     */
    public void setRecognizeStandardFileExtensions(String recognizeStandardFileExtensions) {
        this.recognizeStandardFileExtensions = recognizeStandardFileExtensions;
    }

    /**
     * @param cacheStorage The CacheStorage used for caching Templates
     */
    public void setCacheStorage(String cacheStorage) {
        this.cacheStorage = cacheStorage;
    }

    /**
     * @param templateUpdateDelay The time that must elapse before checking whether there is a newer version of a
     *                            template "file" than the cached one
     */
    public void setTemplateUpdateDelay(String templateUpdateDelay) {
        this.templateUpdateDelay = templateUpdateDelay;
    }

    /**
     * @param tagSyntax The tag syntax of the template files that has no {@code #ftl} header to decide that
     */
    public void setTagSyntax(String tagSyntax) {
        this.tagSyntax = tagSyntax;
    }

    /**
     * @param interpolationSyntax The interpolation syntax of the template files
     */
    public void setInterpolationSyntax(String interpolationSyntax) {
        this.interpolationSyntax = interpolationSyntax;
    }

    /**
     * @param namingConvention The naming convention used for the identifiers that are part of the template
     *                         language
     */
    public void setNamingConvention(String namingConvention) {
        this.namingConvention = namingConvention;
    }

    /**
     * @param tabSize The assumed display width of the tab character (ASCII 9), which influences the column number
     *                shown in error messages
     */
    public void setTabSize(String tabSize) {
        this.tabSize = tabSize;
    }

    /**
     * @param incompatibleImprovements Which version of the non-backward-compatible bugfixes/improvements should
     *                                 be enabled
     */
    public void setIncompatibleImprovements(String incompatibleImprovements) {
        this.incompatibleImprovements = incompatibleImprovements;
    }

    /**
     * @param templateLoader The TemplateLoader that is used to look up and load templates
     */
    public void setTemplateLoader(String templateLoader) {
        this.templateLoader = templateLoader;
    }

    /**
     * @param templateLookupStrategy The TemplateLookupStrategy that is used to look up templates based on the
     *                               requested name
     */
    public void setTemplateLookupStrategy(String templateLookupStrategy) {
        this.templateLookupStrategy = templateLookupStrategy;
    }

    /**
     * @param templateNameFormat The template name format used
     */
    public void setTemplateNameFormat(String templateNameFormat) {
        this.templateNameFormat = templateNameFormat;
    }

    /**
     * @param templateConfigurations The TemplateConfigurationFactory that will configure individual templates
     *                               where their settings differ from those coming from the common Configuration
     *                               object
     */
    public void setTemplateConfigurations(String templateConfigurations) {
        this.templateConfigurations = templateConfigurations;
    }

}
