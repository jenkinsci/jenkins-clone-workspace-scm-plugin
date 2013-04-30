/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Andrew Bayer
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

import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import static hudson.scm.PollingResult.BUILD_NOW;
import static hudson.scm.PollingResult.NO_CHANGES;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.model.ParametersAction;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.Launcher;
import hudson.FilePath;
import hudson.WorkspaceSnapshot;
import hudson.PermalinkList;
import hudson.Extension;
import static hudson.Util.fixEmptyAndTrim;

import java.io.IOException;
import java.io.File;
import java.io.Serializable;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link SCM} that inherits the workspace from another build through {@link WorkspaceSnapshot}
 * Derived from {@link WorkspaceSnapshotSCM}.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public class CloneWorkspaceSCM extends SCM {
    /**
     * The job name from which we inherit the workspace.
     */
    public String parentJobName;
    
    /**
     * The criteria by which to choose the build to inherit from.
     * Can be "Any" (meaning most recent completed build), "Not Failed" (meaning most recent unstable/stable build),
     * or "Successful" (meaning most recent stable build).
     */
    public String criteria;

    @DataBoundConstructor
    public CloneWorkspaceSCM(String parentJobName, String criteria) {
        this.parentJobName = parentJobName;
        this.criteria = criteria;
    }

    /**
     * Get the parent job name. Process it for parameters if needed.
     *
     * @return Parent job name.
     */
    public String getParamParentJobName(AbstractBuild<?, ?> build) {
        String original = parentJobName;
        if (build != null) {
            ParametersAction parameters = build.getAction(ParametersAction.class);
            if (parameters != null) {
                original = parameters.substitute(build, original);
            }
        }

        return original;
    }
        
    
    /**
     * Obtains the {@link WorkspaceSnapshot} object that this {@link SCM} points to,
     * or throws {@link ResolvedFailedException} upon failing.
     *
     * @param parentJob Processed parent job name.
     * @return never null.
     */
    public Snapshot resolve(String parentJob) throws ResolvedFailedException {
        Hudson h = Hudson.getInstance();
        AbstractProject<?,?> job = h.getItemByFullName(parentJob, AbstractProject.class);
        if(job==null) {
            if(h.getItemByFullName(parentJob)==null) {
                AbstractProject nearest = AbstractProject.findNearest(parentJob);
                throw new ResolvedFailedException(Messages.CloneWorkspaceSCM_NoSuchJob(parentJob,nearest.getFullName()));
            } else
                throw new ResolvedFailedException(Messages.CloneWorkspaceSCM_IncorrectJobType(parentJob));
        }

        
        AbstractBuild<?,?> b = CloneWorkspaceUtil.getMostRecentBuildForCriteria(job,criteria);
        
        if(b==null)
            throw new ResolvedFailedException(Messages.CloneWorkspaceSCM_NoBuild(criteria,parentJob));

        WorkspaceSnapshot snapshot = b.getAction(WorkspaceSnapshot.class);
        if(snapshot==null)
            throw new ResolvedFailedException(Messages.CloneWorkspaceSCM_NoWorkspace(parentJob,criteria));

        return new Snapshot(snapshot,b);
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        try {
            workspace.deleteContents();
            String parentJob = getParamParentJobName(build);
            Snapshot snapshot = resolve(parentJob);
            listener.getLogger().println("Restoring workspace from build #" + snapshot.getParent().getNumber() + " of project " + parentJob);
            snapshot.restoreTo(workspace,listener);

            // write out the parent build number file
            PrintWriter w = new PrintWriter(new FileOutputStream(getParentBuildFile(build)));
            try {
                w.println(snapshot.getParent().getNumber());
            } finally {
                w.close();
            }
            
            return calcChangeLog(snapshot.getParent(), changelogFile, listener);
        } catch (ResolvedFailedException e) {
            listener.error(e.getMessage()); // stack trace is meaningless
            build.setResult(Result.FAILURE);
            return false;
        }
    }

    /**
     * Called after checkout has finished to copy the changelog from the parent build.
     */
    private boolean calcChangeLog(AbstractBuild<?,?> parentBuild, File changelogFile, BuildListener listener) throws IOException, InterruptedException {
        FilePath parentChangeLog = new FilePath(new File(parentBuild.getRootDir(), "changelog.xml"));
        if (parentChangeLog.exists()) {
            FilePath childChangeLog = new FilePath(changelogFile);
            parentChangeLog.copyTo(childChangeLog);
        }
        else {
            createEmptyChangeLog(changelogFile, listener, "log");
        }
        return true;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        AbstractProject<?,?> p = getContainingProject();
        final AbstractBuild lastBuild = p.getLastBuild();
        
        try {
            return resolve(getParamParentJobName(lastBuild)).getParent().getProject().getScm().createChangeLogParser();
        } catch (ResolvedFailedException e) {
            return null;
        } 
    }

    private AbstractProject getContainingProject() {
        for( AbstractProject p : Hudson.getInstance().getAllItems(AbstractProject.class) ) {
            SCM scm = p.getScm();
            if (scm != null && scm.getClass() == this.getClass() && this.equals(scm)) {
                return p;
            }
        }
        return null;
    }
                
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public static File getParentBuildFile(AbstractBuild b) {
        return new File(b.getRootDir(),"cloneWorkspaceParent.txt");
    }

    
    /**
     * Reads the parent build file of the specified build (or the closest, if the flag is so specified.)
     *
     * @param findClosest
     *      If true, this method will go back the build history until it finds a parent build file.
     *      A build may not have a parent build file for any number of reasons (such as failure, interruption, etc.)
     * @return
     *      Number of parent build
     */
    private int parseParentBuildFile(AbstractBuild<?,?> build, boolean findClosest) throws IOException {
        int parentBuildNumber = 0; // Default to 0, so that if we don't actually find a build,
                                   // polling et al will return true.

        // If the build itself is null, just return the default.
        if (build==null)
            return parentBuildNumber;
            
        if (findClosest) {
            for (AbstractBuild<?,?> b=build; b!=null; b=b.getPreviousBuild()) {
                if(getParentBuildFile(b).exists()) {
                    build = b;
                    break;
                }
            }
        }
        
        {// read the parent build file of the build
            File file = getParentBuildFile(build);
            if(!file.exists())
                // nothing to compare against
                return parentBuildNumber;

            BufferedReader br = new BufferedReader(new FileReader(file));
            try {
                String line;
                while((line=br.readLine())!=null) {
                    try {
                        parentBuildNumber = Integer.parseInt(fixEmptyAndTrim(line));
                    } catch (NumberFormatException e) {
                        // perhaps a corrupted line. ignore
                    }
                }
            } finally {
                br.close();
            }
        }

        return parentBuildNumber;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        // exclude locations that are svn:external-ed with a fixed revision.
        int parentBuildNumber = parseParentBuildFile(build,true);

        return new CloneWorkspaceSCMRevisionState(parentBuildNumber);
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?,?> project, Launcher launcher, FilePath workspace, final TaskListener listener, SCMRevisionState _baseline) throws IOException, InterruptedException {
        final AbstractBuild lastBuild = project.getLastBuild();
        String parentJob = getParamParentJobName(lastBuild);
        Hudson h = Hudson.getInstance();
        AbstractProject<?,?> parentProject = h.getItemByFullName(parentJob, AbstractProject.class);
        if (parentProject==null) {
            // Disable this project if the parent project no longer exists or doesn't exist in the first place.
            listener.getLogger().println("The CloneWorkspace parent project for " + project + " does not exist, project will be disabled."); 
            project.makeDisabled(true);
            return new PollingResult(_baseline, _baseline, PollingResult.Change.NONE);
        }

        final CloneWorkspaceSCMRevisionState baseline = (CloneWorkspaceSCMRevisionState)_baseline;

        Snapshot s = null;
        try {
            s = resolve(parentJob);
        } catch (ResolvedFailedException e) {
            listener.getLogger().println(e.getMessage());
            return new PollingResult(baseline, baseline, PollingResult.Change.NONE);
            //            return NO_CHANGES;
        }
        if (s==null) {
            listener.getLogger().println("Snapshot failed to resolve for unknown reasons.");
            return new PollingResult(baseline, baseline, PollingResult.Change.NONE);
            //            return NO_CHANGES;
        }
        else {
            if (s.getParent().getNumber() > baseline.parentBuildNumber) {
                listener.getLogger().println("Build #" + s.getParent().getNumber() + " of project " + parentJob
                                             + " is newer than build #" + baseline.parentBuildNumber + ", so a new build of "
                                             + project + " will be run.");
                return new PollingResult(baseline, new CloneWorkspaceSCMRevisionState(s.getParent().getNumber()), PollingResult.Change.SIGNIFICANT);
            //                return BUILD_NOW;
            }
            else {
                listener.getLogger().println("Build #" + s.getParent().getNumber() + " of project " + parentJob
                                             + " is NOT newer than build #" + baseline.parentBuildNumber + ", so no new build of "
                                             + project + " will be run.");
                return new PollingResult(baseline, baseline, PollingResult.Change.NONE);
            //                return NO_CHANGES;
            }
        }
    }

        

    @Extension
    public static class DescriptorImpl extends SCMDescriptor<CloneWorkspaceSCM> {
        public DescriptorImpl() {
            super(CloneWorkspaceSCM.class, null);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.CloneWorkspaceSCM_DisplayName();
        }

        
        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(CloneWorkspaceSCM.class, formData);
        }

        public List<String> getEligibleParents() {
            List<String> parentNames = new ArrayList<String>();
            
            for (AbstractProject p : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                if (p.getPublishersList().get(CloneWorkspacePublisher.class) != null) {
                    parentNames.add(p.getFullName());
                }
            }
            
            return parentNames;
        }

    }

    public final class CloneWorkspaceSCMRevisionState extends SCMRevisionState implements Serializable {

        final int parentBuildNumber;

        CloneWorkspaceSCMRevisionState(int parentBuildNumber) {
            this.parentBuildNumber = parentBuildNumber;
        }

        private static final long serialVersionUID = 1L;
    }

 
    /**
     * {@link Exception} indicating that the resolution of the job/build failed.
     */
    private final class ResolvedFailedException extends Exception {
        private ResolvedFailedException(String message) {
            super(message);
        }
    }

    private static class Snapshot {
        final WorkspaceSnapshot snapshot;
        final AbstractBuild<?,?> parent;

        private Snapshot(WorkspaceSnapshot snapshot, AbstractBuild<?,?> parent) {
            this.snapshot = snapshot;
            this.parent = parent;
        }

        void restoreTo(FilePath dst,TaskListener listener) throws IOException, InterruptedException {
            snapshot.restoreTo(parent,dst,listener);
        }

        AbstractBuild<?,?> getParent() {
            return parent;
        }
    }

   private static final Logger LOGGER = Logger.getLogger(CloneWorkspaceSCM.class.getName());

}
