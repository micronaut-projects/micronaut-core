function nextElement(el) {
    el = el.nextSibling;
    while (el && el.nodeType != 1) {
        el = el.nextSibling;
    }
    return el;
}
function indexOf(arr, o) {
    for (var i = 0; i < arr.length; i++) {
        if (arr[i] == o) return i;
    }
    return -1;
}
function contains(arr, o) { return indexOf(arr, o) != -1 }
function getClasses(el) { return el.className.split(" "); }
function pushClass(el, cls) {
    var classes = getClasses(el);
    classes.push(cls);
    el.className = classes.join(" ");
    return el.className;
}
function removeClass(el, cls) {
    var classes = getClasses(el);
    classes.splice(indexOf(classes, "selected"), 1)
    el.className = classes.join(" ");
    return el.className;
}
function toggleRef(el) {
    if (contains(getClasses(el), "selected")) {
        removeClass(el, "selected");
    }
    else {
        pushClass(el, "selected");
    }
}

var show = true;
function localToggle() {
    document.getElementById("col2").style.display = show ? "none" : "";
    document.getElementById("toggle-col1").style.display = show ? "inline" : "none";
    document.getElementById("ref-button").parentNode.className = (show = !show) ? "separator selected" : "separator";
    return false;
}
function toggleNavSummary(hide) {
    document.getElementById("nav-summary-childs").style.display = !hide ? "block" : "none";
    document.getElementById("nav-summary").className = hide ? "" : "active";
}

var hiddenBlocksShown = false;
function toggleHidden() {
    var elements = document.getElementsByClassName("hidden-block");
    for (var i = 0; i < elements.length; i++) {
        elements[i].style.display = hiddenBlocksShown ? "none" : "block";
    }

    hiddenBlocksShown = !hiddenBlocksShown
}
