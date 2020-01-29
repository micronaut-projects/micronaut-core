var BUILD_MAVEN = "maven";
var BUILD_GRADLE = "gradle";
var BUILD_GRADLE_GROOVY = "gradle-groovy";
var BUILD_GRADLE_KOTLIN = "gradle-kotlin";
var LANG_JAVA = "java";
var LANG_GROOVY = "groovy";
var LANG_KOTLIN = "kotlin";
var MICRONAUT_SUPPORTED_BUILDS = [BUILD_GRADLE, BUILD_GRADLE_GROOVY, BUILD_GRADLE_KOTLIN, BUILD_MAVEN];
var MICRONAUT_BUILD_HAS_SUBSET = [BUILD_GRADLE];
var MICRONAUT_BUILD_IS_SUBSET = [BUILD_GRADLE_GROOVY, BUILD_GRADLE_KOTLIN];
var MICRONAUT_BUILD_SUBSET_LOOKUP = {};
MICRONAUT_BUILD_SUBSET_LOOKUP[BUILD_GRADLE_GROOVY] = BUILD_GRADLE;
MICRONAUT_BUILD_SUBSET_LOOKUP[BUILD_GRADLE_KOTLIN] = BUILD_GRADLE;
var MICRONAUT_SUPPORTED_LANGS = [LANG_JAVA, LANG_GROOVY, LANG_KOTLIN];
var DEFAULT_SUPPORTED_LANG = LANG_JAVA;
var DEFAULT_BUILD = BUILD_GRADLE;
var DEFAULT_BUILD_SUBSET = BUILD_GRADLE_GROOVY;
var LOCALSTORAGE_KEY_LANG = "preferred-micronaut-language";
var LOCALSTORAGE_KEY_BUILD = "preferred-micronaut-build";
var LOCALSTORAGE_KEY_BUILD_SUBSET = "preferred-micronaut-build-subset";


function addCopyToClipboardButtons() {
    var elements = document.getElementsByClassName("multi-language-sample");
    console.log("multi-language-sample blocks" + elements.length);
    for (var y = 0; y < elements.length; y++) {
        elements[y].appendChild(createCopyToClipboardElement());
    }
}

function postProcessCodeBlocks() {
    // Assumptions:
    //  1) All siblings that are marked with class="multi-language-sample" should be grouped
    //  2) Only one language can be selected per domain (to allow selection to persist across all docs pages)
    //  3) There is exactly 1 small set of languages to choose from. This does not allow for multiple language preferences. For example, users cannot prefer both Kotlin and Groovy.
    //     (Exception: With the build languages, we can have common gradle, which works w/ the groovy and kotlin DSLs, as well as DSL preferences.)
    //  4) Only 1 sample of each language can exist in the same collection.


    var preferredLanguage = getOrSetDefaultPreferredLanguage();
    var preferredBuild = getOrSetDefaultPreferredBuild();
    var preferredBuildSubset = getOrSetDefaultPreferredBuildSubset();

    function isBuild(optionId) {
        return MICRONAUT_SUPPORTED_BUILDS.indexOf(optionId) > -1;
    }

    function isBuildSubset(optionId) {
        return MICRONAUT_BUILD_IS_SUBSET.indexOf(optionId) > -1;
    }

    function isBuildSupertype(optionId) {
        return MICRONAUT_BUILD_HAS_SUBSET.indexOf(optionId) > -1;
    }

    function isLang(optionId) {
        return MICRONAUT_SUPPORTED_LANGS.indexOf(optionId) > -1;
    }

    // Ensure preferred Language is valid, defaulting to JAVA
    function getOrSetDefaultPreferredLanguage() {
        var lang = window.localStorage.getItem(LOCALSTORAGE_KEY_LANG);
        if (MICRONAUT_SUPPORTED_LANGS.indexOf(lang) === -1) {
            window.localStorage.setItem(LOCALSTORAGE_KEY_LANG, DEFAULT_SUPPORTED_LANG);
            lang = DEFAULT_SUPPORTED_LANG;
        }
        return lang;
    }

    // Ensure preferred build is valid, defaulting to GRADLE
    function getOrSetDefaultPreferredBuild() {
        var build = window.localStorage.getItem(LOCALSTORAGE_KEY_BUILD);
        if (MICRONAUT_SUPPORTED_BUILDS.indexOf(build) === -1) {
            window.localStorage.setItem(LOCALSTORAGE_KEY_BUILD, DEFAULT_BUILD);
            build = DEFAULT_BUILD;
        }
        return build;
    }

    function getOrSetDefaultPreferredBuildSubset() {
        var buildSubset = window.localStorage.getItem(LOCALSTORAGE_KEY_BUILD_SUBSET);
        if (MICRONAUT_BUILD_IS_SUBSET.indexOf(buildSubset) === -1) {
            window.localStorage.setItem(LOCALSTORAGE_KEY_BUILD_SUBSET, DEFAULT_BUILD_SUBSET);
            buildSubset = DEFAULT_BUILD_SUBSET;
        }
        return buildSubset;
    }

    // This makes the dash separated sub-langs display better
    function makeTitleForSnippetSelector(string) {
        var langSlices = string.split("-");
        var title = capitalizeWord(langSlices[0]);
        if(langSlices.length == 2) {
            title += " (" + capitalizeWord(langSlices[1]) + ")";
        }
        return title;
    }

    function capitalizeWord(string) {
        if (typeof string !== 'string') return '';
        return string.charAt(0).toUpperCase() + string.slice(1);
    }

    function shouldHideCodeEl(codeEl, prefLangId, prefBuildId, prefBuildSubsetId) {
        var doesElementMatchLangPref = codeEl.getAttribute("data-lang") === prefLangId;
        var doesElementMatchBuildPref = codeEl.getAttribute("data-lang") === prefBuildId;
        var doesElementMatchBuildSubsetPref = codeEl.getAttribute("data-lang") === prefBuildSubsetId && MICRONAUT_BUILD_SUBSET_LOOKUP[prefBuildSubsetId] === prefBuildId;
        var isElementMatch = doesElementMatchLangPref || doesElementMatchBuildPref || doesElementMatchBuildSubsetPref;
        return !isElementMatch
    }

    function shouldAddSelectedToOptionEl(optionEl, prefLangId, prefBuildId, prefBuildSubsetId) {
        var doesElementMatchLangPref = optionEl.getAttribute("data-lang") === prefLangId;
        var doesElementMatchBuildPref = optionEl.getAttribute("data-lang") === prefBuildId;
        var doesElementMatchBuildSubsetPref = optionEl.getAttribute("data-lang") === prefBuildSubsetId && MICRONAUT_BUILD_SUBSET_LOOKUP[prefBuildSubsetId] === prefBuildId;
        var isElementMatch = doesElementMatchLangPref || doesElementMatchBuildPref || doesElementMatchBuildSubsetPref;
        return isElementMatch;
    }

    function processSampleEl(sampleEl, prefLangId, prefBuildId, prefBuildSubsetId) {
        var codeEl = sampleEl.querySelector("code[data-lang]");
        if (codeEl != null) {
            sampleEl.setAttribute("data-lang", codeEl.getAttribute("data-lang"));
            if (shouldHideCodeEl(codeEl, prefLangId, prefBuildId, prefBuildSubsetId)) {
                sampleEl.classList.add("hidden");
            } else {
                sampleEl.classList.remove("hidden");
            }
            // This block corrects highlighting issues with our dash-separated languages (like gradle-groovy and gradle-kotlin)
            if(codeEl.classList.contains("language-" + BUILD_GRADLE_GROOVY) || codeEl.classList.contains("language-" + BUILD_GRADLE_KOTLIN)) {
                codeEl.classList.remove('language-' + BUILD_GRADLE_GROOVY);
                codeEl.classList.remove('language-' + BUILD_GRADLE_KOTLIN);
                codeEl.classList.add('language-' + BUILD_GRADLE);
                hljs.highlightBlock(codeEl);
            }
            // This block corrects highlighting issues for Maven, which isn't supported by hljs as maven but as XML
            if(codeEl.classList.contains("language-" + BUILD_MAVEN)) {
                codeEl.classList.remove('language-' + BUILD_MAVEN);
                codeEl.classList.add('language-xml');
                hljs.highlightBlock(codeEl);
            }
        }
    }

    function switchSampleLanguage(languageId, buildId, buildSubsetId) {

        // First make sure all the code sample sections are created
        ensureMultiLanguageSampleSectionsHydrated(languageId, buildId, buildSubsetId);

        [].slice.call(document.querySelectorAll(".multi-language-selector .language-option")).forEach(function (optionEl) {
            if (shouldAddSelectedToOptionEl(optionEl, languageId, buildId, buildSubsetId)) {
                optionEl.classList.add("selected");
            } else {
                optionEl.classList.remove("selected");
            }
        });

        // What project is this used in? The core docs don't use this...
        [].slice.call(document.querySelectorAll(".multi-language-text")).forEach(function (el) {
            if (!el.classList.contains("lang-" + languageId) && !el.classList.contains("lang-" + buildId)) {
                el.classList.add("hidden");
            } else {
                el.classList.remove("hidden");
            }
        });
    }

    function updatePreferredLanguage(event) {
        var optionId = event.target.getAttribute("data-lang");
        var isOptionBuild = isBuild(optionId);
        var isOptionBuildSubset = isBuildSubset(optionId);
        var isOptionLang = isLang(optionId);
        if (isOptionBuild) {
            if(isOptionBuildSubset) {
                window.localStorage.setItem(LOCALSTORAGE_KEY_BUILD_SUBSET, optionId);
                window.localStorage.setItem(LOCALSTORAGE_KEY_BUILD, MICRONAUT_BUILD_SUBSET_LOOKUP[optionId]);
            } else {
                window.localStorage.setItem(LOCALSTORAGE_KEY_BUILD, optionId);
            }
        }
        if (isOptionLang) {
            window.localStorage.setItem(LOCALSTORAGE_KEY_LANG, optionId);
        }

        switchSampleLanguage(
            isOptionLang ? optionId : getOrSetDefaultPreferredLanguage(),
            isOptionBuild && !isOptionBuildSubset ? optionId : getOrSetDefaultPreferredBuild(),
            isOptionBuildSubset ? optionId : getOrSetDefaultPreferredBuildSubset()
        );

        // scroll to multi-lange selector. Offset the scroll a little bit to focus.
        event.target.scrollIntoView();
        var offset = 150;
        window.scrollBy(0, -offset);
    }

    function ensureMultiLanguageSampleSectionsHydrated(languageId, buildId, buildSubsetId) {
        var multiLanguageSampleElements = [].slice.call(document.querySelectorAll(".multi-language-sample"));
        // Array of Arrays, each top-level array representing a single collection of samples
        var multiLanguageSets = [];
        for (var i = 0; i < multiLanguageSampleElements.length; i++) {
            var currentCollection = [multiLanguageSampleElements[i]];
            var currentSampleElement = multiLanguageSampleElements[i];
            processSampleEl(currentSampleElement, languageId, buildId, buildSubsetId);
            while (currentSampleElement.nextElementSibling != null && currentSampleElement.nextElementSibling.classList.contains("multi-language-sample")) {
                currentCollection.push(currentSampleElement.nextElementSibling);
                currentSampleElement = currentSampleElement.nextElementSibling;
                processSampleEl(currentSampleElement, languageId, buildId, buildSubsetId);
                i++;
            }

            multiLanguageSets.push(currentCollection);
        }

        multiLanguageSets.forEach(function (sampleCollection) {
            // Create selector element if not existing
            if (sampleCollection.length > 1) {

                if (sampleCollection.every(function(element) {
                    return element.classList.contains("hidden");
                })) {
                    sampleCollection[0].classList.remove("hidden");
                }

                // Add the multi-lang selector
                if (sampleCollection[0].previousElementSibling == null ||
                    !sampleCollection[0].previousElementSibling.classList.contains("multi-language-selector")) {

                    var languageSelectorFragment = document.createDocumentFragment();
                    var multiLanguageSelectorElement = document.createElement("div");
                    multiLanguageSelectorElement.classList.add("multi-language-selector");
                    languageSelectorFragment.appendChild(multiLanguageSelectorElement);

                    sampleCollection.forEach(function (sampleEl) {
                        var optionEl = document.createElement("code");
                        var sampleLanguage = sampleEl.getAttribute("data-lang");
                        optionEl.setAttribute("data-lang", sampleLanguage);
                        optionEl.setAttribute("role", "button");
                        optionEl.classList.add("language-option");

                        optionEl.innerText = makeTitleForSnippetSelector(sampleLanguage);

                        optionEl.addEventListener("click", updatePreferredLanguage);
                        multiLanguageSelectorElement.appendChild(optionEl);
                    });
                    sampleCollection[0].parentNode.insertBefore(languageSelectorFragment, sampleCollection[0]);
                    // Insert title node prior to selector if title is present in sample collections, and remove duplicate title nodes
                    if (sampleCollection[0].getElementsByClassName("title").length > 0) {
                        var titleFragment =  document.createDocumentFragment();
                        var titleContainerFragment = document.createElement("div");
                        titleContainerFragment.classList.add("paragraph");
                        titleFragment.appendChild(titleContainerFragment);
                        var titleEl = sampleCollection[0].getElementsByClassName("title")[0].cloneNode(true);
                        titleContainerFragment.appendChild(titleEl);
                        sampleCollection.forEach(function(element) {
                            var titleElementsToRemove = element.getElementsByClassName("title");
                            if(titleElementsToRemove.length > 0) {
                                for (var i = 0; i < titleElementsToRemove.length; i++) {
                                    titleElementsToRemove[i].parentNode.removeChild(titleElementsToRemove[i]);
                                }
                            }
                        });
                        sampleCollection[0].parentNode.insertBefore(titleFragment, multiLanguageSelectorElement);
                    }
                }
            }
        });
    }

    switchSampleLanguage(preferredLanguage, preferredBuild, preferredBuildSubset);
}

function createCopyToClipboardElement() {
    var copyToClipboardDiv = document.createElement("div");
    var copyToClipboardSpan = document.createElement("span");
    copyToClipboardSpan.setAttribute("class", "copytoclipboard");
    copyToClipboardSpan.setAttribute("onclick", "copyToClipboard(this);");
    copyToClipboardSpan.innerText = "Copy to Clipboard";
    copyToClipboardDiv.appendChild(copyToClipboardSpan);
    return copyToClipboardDiv;
}

function postProcessCodeCallouts() {
    var calloutClass = "conum";
    var matches = document.querySelectorAll("b."+calloutClass);
    if (matches != null) {
        matches.forEach(function(item) {
            var number = item.textContent.replace("(", "").replace(")", "");
            var i = document.createElement('i');
            i.setAttribute("class","conum");
            i.setAttribute("data-value", number);
            item.parentNode.insertBefore(i, item);
            item.removeAttribute("class");
        });
    }
}

document.addEventListener("DOMContentLoaded", function(event) {
    addCopyToClipboardButtons();
    postProcessCodeBlocks();
    // What project uses these "code callouts"? They don't seem to be in the core docs
    postProcessCodeCallouts();
});
