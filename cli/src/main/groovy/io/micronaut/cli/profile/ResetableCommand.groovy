package io.micronaut.cli.profile;

interface ResetableCommand extends Command {

    /**
     * Resets the command state
     */
    void reset()
}
