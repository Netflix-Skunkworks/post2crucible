package com.netflix.postreview.git;

import com.netflix.postreview.Runner;

import java.io.File;

/**
 * Variant of Runner tailored for invoking git
 */
public class GitRunner extends Runner {

    public final String gitPath;

    /**
     * Git runner sets the working dir, and remembers the path to the git executable.
     */
    public GitRunner(String git, File dir) {
        gitPath = git;
        workingDir = dir;
    }

}
