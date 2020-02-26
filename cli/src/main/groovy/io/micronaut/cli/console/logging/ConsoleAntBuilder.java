/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cli.console.logging;

import groovy.util.AntBuilder;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.types.LogLevel;
import org.apache.tools.ant.util.StringUtils;

/**
 * Silences ant builder output.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConsoleAntBuilder extends AntBuilder {

    /**
     * @param project The Ant {@link Project}
     */
    public ConsoleAntBuilder(Project project) {
        super(project);
    }

    /**
     * Default constructor.
     */
    public ConsoleAntBuilder() {
        super(createAntProject());
    }

    /**
     * @return Factory method to create new Project instances
     */
    @SuppressWarnings("unchecked")
    protected static Project createAntProject() {
        final Project project = new Project();

        final ProjectHelper helper = ProjectHelper.getProjectHelper();
        project.addReference(ProjectHelper.PROJECTHELPER_REFERENCE, helper);
        helper.getImportStack().addElement("AntBuilder"); // import checks that stack is not empty

        addMicronautConsoleBuildListener(project);

        project.init();
        project.getBaseDir();
        return project;
    }

    /**
     * @param project The Ant {@link Project}
     */
    public static void addMicronautConsoleBuildListener(Project project) {
        final BuildLogger logger = new MicronautConsoleLogger();

        logger.setMessageOutputLevel(Project.MSG_INFO);
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);

        project.addBuildListener(logger);

        MicronautConsole instance = MicronautConsole.getInstance();
        project.addBuildListener(new ConsoleBuildListener(instance));

        if (!instance.isVerbose()) {
            for (Object buildListener : project.getBuildListeners()) {
                if (buildListener instanceof BuildLogger) {
                    ((BuildLogger) buildListener).setMessageOutputLevel(LogLevel.ERR.getLevel());
                }
            }
        }
    }

    /**
     * Micronaut console logger.
     */
    private static class MicronautConsoleLogger extends DefaultLogger {
        /**
         * Name of the current target, if it should
         * be displayed on the next message. This is
         * set when a target starts building, and reset
         * to <code>null</code> after the first message for
         * the target is logged.
         */
        protected String targetName;
        protected MicronautConsole console = MicronautConsole.getInstance();

        /**
         * Notes the name of the target so it can be logged
         * if it generates any messages.
         *
         * @param event A BuildEvent containing target information.
         *              Must not be <code>null</code>.
         */
        @Override
        public void targetStarted(BuildEvent event) {
            targetName = event.getTarget().getName();
        }

        /**
         * Resets the current target name to <code>null</code>.
         *
         * @param event Ignored in this implementation.
         */
        @Override
        public void targetFinished(BuildEvent event) {
            targetName = null;
        }

        /**
         * Logs a message for a target if it is of an appropriate
         * priority, also logging the name of the target if this
         * is the first message which needs to be logged for the
         * target.
         *
         * @param event A BuildEvent containing message information.
         *              Must not be <code>null</code>.
         */
        @Override
        public void messageLogged(BuildEvent event) {
            if (event.getPriority() > msgOutputLevel ||
                null == event.getMessage() ||
                "".equals(event.getMessage().trim())) {
                return;
            }

            if (null != targetName) {
                console.verbose(StringUtils.LINE_SEP + targetName + ":");
                targetName = null;
            }
        }
    }
}
