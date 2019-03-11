var BUILD_MAVEN = "maven";
var BUILD_GRADLE = "gradle";
var LANG_JAVA = "java";
var LANG_GROOVY = "groovy";
var LANG_KOTLIN = "kotlin";
var MICRONAUT_SUPPORTED_BUILDS = [BUILD_MAVEN, BUILD_GRADLE];
var MICRONAUT_SUPPORTED_LANGS = [LANG_JAVA, LANG_GROOVY, LANG_KOTLIN];
var DEFAULT_SUPPORTED_LANG = LANG_JAVA;
var DEFAULT_BUILD = BUILD_GRADLE;
var LOCALSTORAGE_KEY_LANG = "preferred-micronaut-language";
var LOCALSTORAGE_KEY_BUILD = "preferred-micronaut-build";


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
    //  3) There is exactly 1 small set of languages to choose from. This does not allow for multiple language preferences. For example, users cannot prefer both Kotlin and ZSH.
    //  4) Only 1 sample of each language can exist in the same collection.


    var preferredLanguage = initPreferredLanguage();
    var preferredBuild = initPreferredBuild();

    function isBuild(optionId) {
        return MICRONAUT_SUPPORTED_BUILDS.indexOf(optionId) > -1
    }
    function isLang(optionId) {
        return MICRONAUT_SUPPORTED_LANGS.indexOf(optionId) > -1
    }

    // Ensure preferred Language is valid, defaulting to JAVA
    function initPreferredLanguage() {
        var lang = window.localStorage.getItem(LOCALSTORAGE_KEY_LANG);
        if (MICRONAUT_SUPPORTED_LANGS.indexOf(lang) === -1) {
            window.localStorage.setItem(LOCALSTORAGE_KEY_LANG, DEFAULT_SUPPORTED_LANG);
            lang = DEFAULT_SUPPORTED_LANG;
        }
        return lang;
    }

    // Ensure preferred build is valid, defaulting to GRADLE
    function initPreferredBuild() {
        var build = window.localStorage.getItem(LOCALSTORAGE_KEY_BUILD);
        if (MICRONAUT_SUPPORTED_LANGS.indexOf(build) === -1) {
            window.localStorage.setItem(LOCALSTORAGE_KEY_BUILD, DEFAULT_BUILD);
            build = DEFAULT_BUILD;
        }
        return build;
    }

    function capitalizeFirstLetter(string) {
        return string.charAt(0).toUpperCase() + string.slice(1);
    }

    function processSampleEl(sampleEl, prefLangId, prefBuildId) {
        var codeEl = sampleEl.querySelector("code[data-lang]");
        if (codeEl != null) {
            sampleEl.setAttribute("data-lang", codeEl.getAttribute("data-lang"));
            if (codeEl.getAttribute("data-lang") !== prefLangId && codeEl.getAttribute("data-lang") !== prefBuildId) {
                sampleEl.classList.add("hidden");
            } else {
                sampleEl.classList.remove("hidden");
            }
        }
    }

    function switchSampleLanguage(languageId, buildId) {
        var multiLanguageSampleElements = [].slice.call(document.querySelectorAll(".multi-language-sample"));

        // Array of Arrays, each top-level array representing a single collection of samples
        var multiLanguageSets = [];
        for (var i = 0; i < multiLanguageSampleElements.length; i++) {
            var currentCollection = [multiLanguageSampleElements[i]];
            var currentSampleElement = multiLanguageSampleElements[i];
            processSampleEl(currentSampleElement, languageId, buildId);
            while (currentSampleElement.nextElementSibling != null && currentSampleElement.nextElementSibling.classList.contains("multi-language-sample")) {
                currentCollection.push(currentSampleElement.nextElementSibling);
                currentSampleElement = currentSampleElement.nextElementSibling;
                processSampleEl(currentSampleElement, languageId, buildId);
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

                        optionEl.innerText = capitalizeFirstLetter(sampleLanguage);

                        optionEl.addEventListener("click", function updatePreferredLanguage(evt) {
                            var optionId = optionEl.getAttribute("data-lang");
                            if (isBuild(optionId)) {
                                window.localStorage.setItem(LOCALSTORAGE_KEY_BUILD, optionId);
                            }
                            if (isLang(optionId)) {
                                window.localStorage.setItem(LOCALSTORAGE_KEY_LANG, optionId);
                            }
                            // Record how far down the page the clicked element is before switching all samples
                            var beforeOffset = evt.target.offsetTop;

                            switchSampleLanguage(isLang(optionId) ? optionId : initPreferredLanguage(), isBuild(optionId) ? optionId : initPreferredBuild());

                            // Scroll the window to account for content height differences between different sample languages
                            window.scrollBy(0, evt.target.offsetTop - beforeOffset);
                        });
                        multiLanguageSelectorElement.appendChild(optionEl);
                    });
                    sampleCollection[0].parentNode.insertBefore(languageSelectorFragment, sampleCollection[0]);
                }
            }
        });

        [].slice.call(document.querySelectorAll(".multi-language-selector .language-option")).forEach(function (optionEl) {
            if (optionEl.getAttribute("data-lang") === languageId || optionEl.getAttribute("data-lang") === buildId) {
                optionEl.classList.add("selected");
            } else {
                optionEl.classList.remove("selected");
            }
        });

        [].slice.call(document.querySelectorAll(".multi-language-text")).forEach(function (el) {
            if (!el.classList.contains("lang-" + languageId) && !el.classList.contains("lang-" + buildId)) {
                el.classList.add("hidden");
            } else {
                el.classList.remove("hidden");
            }
        });
    }

    switchSampleLanguage(preferredLanguage, preferredBuild);
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
    postProcessCodeCallouts();
});