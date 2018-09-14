function wrapElementsInLinks(x) {
    var i;
    for (i = 0; i < x.length; i++) {
        if (x[i].id !== '') {
            var link = document.createElement('a');
            link.innerHTML = x[i].outerHTML;
            link.setAttribute('href', 'index.html#'+x[i].id);
            x[i].parentNode.insertBefore(link, x[i]);
            x[i].remove();
        }
    }
}
wrapElementsInLinks(document.querySelectorAll("h1"));
wrapElementsInLinks(document.querySelectorAll("h2"));
wrapElementsInLinks(document.querySelectorAll("h3"));

var tocId = "table-of-content";
var tocLink = "table-of-content-nav-link";
var mainId = "main";
function hideTableOfContents() {
    document.getElementById(tocId).style.display = "none";
    document.getElementById(mainId).style.paddingLeft = "0";
    var aEl = document.getElementById(tocLink).getElementsByTagName("a")[0];
    replaceLink(aEl, "javascript:showTableOfContents();", "[ - ]", 'Show Table of Contents');
    goToLocation();
}

function goToLocation() {
    if(location.hash != '') {
        window.location = location;
    }
}

function replaceLink(anchorElement, href, text, titleAttr) {
    anchorElement.setAttribute("href", href);
    anchorElement.setAttribute("title", titleAttr);
    anchorElement.innerText = text;
}

function showTableOfContents() {
    document.getElementById(tocId).style.display = "block";
    document.getElementById(mainId).style.paddingLeft = "25em";
    var aEl = document.getElementById(tocLink).getElementsByTagName("a")[0];
    replaceLink(aEl, "javascript:hideTableOfContents();", "[ + ]", 'Hide Table of Contents');
    goToLocation();
}

function scrollToTop() {
    document.getElementById(tocId).style.display = "block";
    document.body.scrollTop = 0; // For Safari
    document.documentElement.scrollTop = 0; // For Chrome, Firefox, IE and Opera
}

function highlightMenu() {
    var cssClass = 'toc-item-highlighted';
    var els = document.getElementsByClassName(cssClass);
    for (var x = 0; x < els.length; x++) {
        els[x].classList.remove(cssClass);
    }
    console.log("highlighting hash" + location.hash);
    if(location.hash != '') {
        var elId = "toc-item-"+location.hash.replace('#', '');
        if(document.getElementById(elId)) {
            document.getElementById(elId).getElementsByTagName('a')[0].classList.add(cssClass);
            document.getElementById(elId).scrollIntoView(true);
        }
    }
}

goToLocation();
highlightMenu();
