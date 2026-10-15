// The LiveReload client Micronaut development mode injects: connects to the launcher's server,
// reloads the page when the application restarted or a template changed, and swaps a stylesheet in
// place when only a CSS file changed.
(function () {
  'use strict';
  var script = document.currentScript || (function () { var s = document.getElementsByTagName('script'); return s[s.length - 1]; })();
  var origin = script && script.src ? new URL(script.src) : { hostname: 'localhost', port: '35729' };
  var url = 'ws://' + origin.hostname + ':' + origin.port + '/livereload';
  var delay = 1000;
  function swap(link) {
    // only the cache-busting parameter changes: a query the page relies on, such as a theme, stays
    var fresh = link.cloneNode();
    var address = new URL(link.href, window.location.href);
    address.searchParams.set('livereload', String(Date.now()));
    fresh.href = address.href;
    fresh.onload = function () { if (link.parentNode) { link.parentNode.removeChild(link); } };
    link.parentNode.insertBefore(fresh, link.nextSibling);
  }
  function swapStylesheets(path) {
    // the changed file is matched by its path under the static root, so two stylesheets of the same
    // name in different directories are told apart; a page that serves it elsewhere falls back to a reload
    var links = document.querySelectorAll('link[rel="stylesheet"]');
    var swapped = false;
    for (var i = 0; i < links.length; i++) {
      var pathname = new URL(links[i].href, window.location.href).pathname;
      if (pathname === path || pathname.slice(-path.length) === path) {
        swap(links[i]);
        swapped = true;
      }
    }
    return swapped;
  }
  function connect() {
    var socket = new WebSocket(url);
    socket.onopen = function () {
      delay = 1000;
      socket.send(JSON.stringify({ command: 'hello', protocols: ['http://livereload.com/protocols/official-7'] }));
    };
    socket.onmessage = function (event) {
      var message;
      try { message = JSON.parse(event.data); } catch (e) { return; }
      if (message.command !== 'reload') { return; }
      if (message.liveCSS && /\.css$/i.test(message.path || '') && swapStylesheets(message.path)) { return; }
      window.location.reload();
    };
    socket.onclose = function () {
      // the launcher is restarting or gone: come back with a growing delay
      setTimeout(connect, delay);
      delay = Math.min(delay * 2, 10000);
    };
    socket.onerror = function () { socket.close(); };
  }
  connect();
})();
