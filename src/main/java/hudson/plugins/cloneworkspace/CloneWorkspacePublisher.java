/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Andrew Bayer
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.cloneworkspace;

import hudson.WorkspaceSnapshot;
import hudson.FileSystemProvisioner;
import hudson.Util;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.DirScanner;
import hudson.util.FormValidation;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import net.sf.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * {@link Recorder} that archives a build's workspace (or subset thereof) as a {@link WorkspaceSnapshot},
 * for use by another project using {@link CloneWorkspaceSCM}.
 *
 * @author Andrew Bayer
 */
public class CloneWorkspacePublisher extends Recorder {
    /**
     * The glob we'll archive.
     */
    private final String workspaceGlob;

    /**
     * The glob we'll exclude from the archive.
     */
    private final String workspaceExcludeGlob;

    /**
     * The criteria which determines whether we'll archive a given build's workspace.
     * Can be "Any" (meaning most recent completed build), "Not Failed" (meaning most recent unstable/stable build),
     * or "Successful" (meaning most recent stable build).
     */
    private final String criteria;

    /**
     * The method by which the SCM will be archived.
     * Can by "TAR" or "ZIP".
     */
    private final String archiveMethod;

    /**
     * If true, don't use the Ant default file glob excludes.
     */
    private final boolean overrideDefaultExcludes;
    @DataBoundConstructor
    public CloneWorkspacePublisher(String workspaceGlob, String workspaceExcludeGlob, String criteria, String archiveMethod, boolean overrideDefaultExcludes) {
        this.workspaceGlob = workspaceGlob.trim();
        this.workspaceExcludeGlob = Util.fixEmptyAndTrim(workspaceExcludeGlob);
        this.criteria = criteria;
        this.archiveMethod = archiveMethod;
        this.overrideDefaultExcludes = overrideDefaultExcludes;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }
    
    public String getWorkspaceGlob() {
        return workspaceGlob;
    }

    public String getWorkspaceExcludeGlob() {
        return workspaceExcludeGlob;
    }

    public String getCriteria() {
        return criteria;
    }

    public String getArchiveMethod() {
        return archiveMethod;
    }

    public boolean getOverrideDefaultExcludes() {
        return overrideDefaultExcludes;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Result criteriaResult = CloneWorkspaceUtil.getResultForCriteria(criteria);
        
        String realIncludeGlob;
        // Default to **/* if no glob is specified.
        if (workspaceGlob.length()==0) {
            realIncludeGlob = "**/*";
        }
        else {
            try {
                realIncludeGlob = build.getEnvironment(listener).expand(workspaceGlob);
            } catch (IOException e) {
                // We couldn't get an environment for some reason, so we'll just use the original.
                realIncludeGlob = workspaceGlob;
            }
        }
        
        String realExcludeGlob = null;
        // Default to empty if no glob is specified.
        if (Util.fixNull(workspaceExcludeGlob).length()!=0) {
            try {
                realExcludeGlob = build.getEnvironment(listener).expand(workspaceExcludeGlob);
            } catch (IOException e) {
                // We couldn't get an environment for some reason, so we'll just use the original.
                realExcludeGlob = workspaceExcludeGlob;
            }
        }

        if (build.getResult().isBetterOrEqualTo(criteriaResult)) {
            listener.getLogger().println(Messages.CloneWorkspacePublisher_ArchivingWorkspace());
            FilePath ws = build.getWorkspace();
            if (ws==null) { // #3330: slave down?
                return true;
            }
            
            try {
                
                String includeMsg = ws.validateAntFileMask(realIncludeGlob);
                String excludeMsg = null;
                if (realExcludeGlob != null) {
                    ws.validateAntFileMask(realExcludeGlob);
                }
                // This means we found something.
                if((includeMsg==null) && (excludeMsg==null)) {
                    DirScanner globScanner = new DirScanner.Glob(realIncludeGlob, realExcludeGlob, !overrideDefaultExcludes);
                    build.addAction(snapshot(build, ws, globScanner, listener, archiveMethod));

                    // Find the next most recent build meeting this criteria with an archived snapshot.
                    AbstractBuild<?,?> previousArchivedBuild = CloneWorkspaceUtil.getMostRecentBuildForCriteriaWithSnapshot(build.getPreviousBuild(), criteria);
                    
                    if (previousArchivedBuild!=null) {
                        listener.getLogger().println(Messages.CloneWorkspacePublisher_DeletingOld(previousArchivedBuild.getDisplayName()));
                        try {
                            File oldWss = new File(previousArchivedBuild.getRootDir(), CloneWorkspaceUtil.getFileNameForMethod(archiveMethod));
                            Util.deleteFile(oldWss);
                        } catch (IOException e) {
                           e.printStackTrace(listener.error(e.getMessage()));
                        }
                    }

                    return true;
                }
                else {
                    listener.getLogger().println(Messages.CloneWorkspacePublisher_NoMatchFound(realIncludeGlob,includeMsg));
                    return true;
                }
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.error(
                                                 Messages.CloneWorkspacePublisher_FailedToArchive(realIncludeGlob)));
                return true;
            } catch (InterruptedException e) {
                e.printStackTrace(listener.error(
                                                 Messages.CloneWorkspacePublisher_FailedToArchive(realIncludeGlob)));
                return true;
            }

        }
        else {
            listener.getLogger().println(Messages.CloneWorkspacePublisher_CriteriaNotMet(criteriaResult));
            return true;
        }
    }        

    public WorkspaceSnapshot snapshot(AbstractBuild<?,?> build, FilePath ws, DirScanner scanner, TaskListener listener, String archiveMethod) throws IOException, InterruptedException {
        File wss = new File(build.getRootDir(), CloneWorkspaceUtil.getFileNameForMethod(archiveMethod));
        if (archiveMethod.equals("ZIP")) {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(wss));
            try {
                ws.zip(os, scanner);
            } finally {
                os.close();
            }

            return new WorkspaceSnapshotZip();
        } else if (archiveMethod.equals("TARONLY")) {
            OutputStream os = new BufferedOutputStream(FilePath.TarCompression.NONE.compress(new FileOutputStream(wss)));
            try {
                ws.tar(os, scanner);
            } finally {
                os.close();
            }

            return new WorkspaceSnapshotTarOnly();
        } else {
            OutputStream os = new BufferedOutputStream(FilePath.TarCompression.GZIP.compress(new FileOutputStream(wss)));
            try {
                ws.tar(os, scanner);
            } finally {
                os.close();
            }

            return new WorkspaceSnapshotTar();
        }
    }

    public static final class WorkspaceSnapshotTar extends WorkspaceSnapshot {
        public void restoreTo(AbstractBuild<?,?> owner, FilePath dst, TaskListener listener) throws IOException, InterruptedException {
            File wss = new File(owner.getRootDir(), CloneWorkspaceUtil.getFileNameForMethod("TAR"));
            new FilePath(wss).untar(dst, FilePath.TarCompression.GZIP);
        }
    }

    public static final class WorkspaceSnapshotTarOnly extends WorkspaceSnapshot {
        public void restoreTo(AbstractBuild<?,?> owner, FilePath dst, TaskListener listener) throws IOException, InterruptedException {
            File wss = new File(owner.getRootDir(), CloneWorkspaceUtil.getFileNameForMethod("TARONLY"));
            new FilePath(wss).untar(dst, FilePath.TarCompression.NONE);
        }
    }

    public static final class WorkspaceSnapshotZip extends WorkspaceSnapshot {
        public void restoreTo(AbstractBuild<?,?> owner, FilePath dst, TaskListener listener) throws IOException, InterruptedException {
            File wss = new File(owner.getRootDir(), CloneWorkspaceUtil.getFileNameForMethod("ZIP"));
            new FilePath(wss).unzip(dst);
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            super(CloneWorkspacePublisher.class);
        }

        public String getDisplayName() {
            return Messages.CloneWorkspacePublisher_DisplayName();
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheckWorkspaceGlob(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return FilePath.validateFileMask(project.getSomeWorkspace(),value);
        }

        @Override
        public CloneWorkspacePublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(CloneWorkspacePublisher.class,formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }


    private static final Logger LOGGER = Logger.getLogger(CloneWorkspacePublisher.class.getName());

}
