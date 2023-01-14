# Clone workspace SCM plugin

Archive the workspace from builds of one job and reuse them as the SCM source for another job.
This plugin makes it possible to archive the workspace from builds of one project and reuse them as the SCM source for another project.

## Usage

The plugin provides a "publisher" that freestyle job can use to clone the workspace of one job for use by another job.

-   In the configuration for a project whose workspace you want to clone and re-use in other projects, select "Archive for Clone Workspace SCM" in the list of publishers
-   If desired, specify the files to include in the archive - by default, this will be "\***/**". Use Ant-style globs
-   Specify the criteria a build needs to meet in order to be archived
-   Run a build.
    If it meets the criteria, its workspace will be archived, until a new build meeting the criteria has run, at which point the old archive will be deleted

## SCM

-   In the configuration for a project which you wish to have re-use another project's workspace, select "Clone Workspace" from the list of possible SCMs
-   Choose the parent project whose workspace you wish to re-use from the drop-down list - if no projects have the clone workspace publisher enabled, the drop-down will be empty
-   Choose the parent build criteria you wish to use
-   Run a build - assuming the parent project has an archived workspace meeting the criteria in question, it'll be expanded and used as the workspace for this build
-   Additionally, the changelog from the parent project build that archived workspace came from will be re-used as the changelog for this build

## Version history

See [GitHub releases](https://github.com/jenkinsci/jenkins-clone-workspace-scm-plugin/releases) for recent releases.


------------------------------------------------------------------------

## Changelog

##### Version 0.6 (Sept 6. 2013)

-   Add support for Matrix jobs
    ([JENKINS-6890](https://issues.jenkins-ci.org/browse/JENKINS-6890))
-   Add null check in CloneWorkspaceSCM.createChangeLogParser() to
    prevent NPE.
    ([JENKINS-13160](https://issues.jenkins-ci.org/browse/JENKINS-13160))
-   Fix Switching archive type of cloned workspace leads to Exception in
    further jobs
    ([JENKINS-15038](https://issues.jenkins-ci.org/browse/JENKINS-15038))
-   Use full name for jobs
-   Don't only consider jenkins root jobs as eligible parents

##### Version 0.5 (Aug 17, 2012)

-   Add option to turn off default Ant excludes from workspace archive.
    ([JENKINS-13888](https://issues.jenkins-ci.org/browse/JENKINS-13888))

##### Version 0.4 (Feb 3, 2012)

-   Exclude files from archive
    ([JENKINS-9582](https://issues.jenkins-ci.org/browse/JENKINS-9582))
-   Allow use of zip or tar.gz for archives.
    ([JENKINS-9577](https://issues.jenkins-ci.org/browse/JENKINS-9577))
-   Parent project can be specified as a parameter.
    ([JENKINS-9779](https://issues.jenkins-ci.org/browse/JENKINS-9779))

##### Version 0.3 (March 4, 2011)

-   Some minor fixes

##### Version 0.2 (also March 14, 2010)

-   Clarifying display name text for CloneWorkspacePublisher

##### Version 0.1 (March 14, 2010)

-   Initial release.
