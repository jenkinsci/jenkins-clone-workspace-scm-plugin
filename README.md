# Clone workspace SCM plugin

Archive the workspace from builds of one job and reuse them as the SCM source for another job.

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

See [GitHub releases]() for recent releases.

Older releases are described in the [changelog](CHANGELOG.md].