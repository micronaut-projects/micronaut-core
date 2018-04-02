<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'synthesizer.label', default: 'Synthesizer')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
        <a href="#list-synthesizer" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
        <div class="nav" role="navigation">
            <ul>
                <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
            </ul>
        </div>
        <div id="list-synthesizer" class="content scaffold-list" role="main">
            <h1><g:message code="default.list.label" args="[entityName]" /></h1>
            <g:if test="${flash.message}">
                <div class="message" role="status">${flash.message}</div>
            </g:if>
            <table>
                <tr>
                    <th>Manufacturer</th>
                    <th>Model</th>
                    <th>Polyphonic?</th>
                </tr>
                <g:each var="synth" in="${synthesizerList}">
                    <tr>
                        <td>${synth.manufacturer}</td>
                        <td>${synth.model}</td>
                        <td>${synth.polyphonic ? 'yes' : 'no'}</td>
                    </tr>
                </g:each>
            </table>
            <f:table collection="${synthesizerList}" />
        </div>
    </body>
</html>