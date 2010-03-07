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
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import net.sf.json.JSONObject;

import java.io.File;
import java.io.IOException;

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
     * The criteria which determines whether we'll archive a given build's workspace.
     * Can be "Any" (meaning most recent completed build), "Not Failed" (meaning most recent unstable/stable build),
     * or "Successful" (meaning most recent stable build).
     */
    private final String criteria;

    @DataBoundConstructor
    public CloneWorkspacePublisher(String workspaceGlob, String criteria) {
        this.workspaceGlob = workspaceGlob.trim();
        this.criteria = criteria;
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

    public String getCriteria() {
        return criteria;
    }


    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Result criteriaResult = CloneWorkspaceUtil.getResultForCriteria(criteria);
        
        String realGlob;
        // Default to **/* if no glob is specified.
        if (workspaceGlob.length()==0) {
            realGlob = "**/*";
        }
        else {
            try {
                realGlob = build.getEnvironment(listener).expand(workspaceGlob);
            } catch (IOException e) {
                // We couldn't get an environment for some reason, so we'll just use the original.
                realGlob = workspaceGlob;
            }

        }

        if (build.getResult().isBetterOrEqualTo(criteriaResult)) {
            listener.getLogger().println(Messages.CloneWorkspacePublisher_ArchivingWorkspace());
            FilePath ws = build.getWorkspace();
            if (ws==null) { // #3330: slave down?
                return true;
            }
            
            try {
                
                String msg = ws.validateAntFileMask(realGlob);
                // This means we found something.
                if(msg==null) {
                    build.addAction(FileSystemProvisioner.DEFAULT.snapshot(build, ws, realGlob, listener));

                    // Find the next most recent build meeting this criteria with an archived snapshot.
                    AbstractBuild<?,?> previousArchivedBuild = CloneWorkspaceUtil.getMostRecentBuildForCriteriaWithSnapshot(build.getPreviousBuild(), criteria);
                    
                    if (previousArchivedBuild!=null) {
                        listener.getLogger().println(Messages.CloneWorkspacePublisher_DeletingOld(previousArchivedBuild.getDisplayName()));
                        try {
                            File oldWss = new File(previousArchivedBuild.getRootDir(), "workspace.zip");
                            Util.deleteFile(oldWss);
                        } catch (IOException e) {
                           e.printStackTrace(listener.error(e.getMessage()));
                        }
                    }

                    return true;
                }
                else {
                    listener.getLogger().println(Messages.CloneWorkspacePublisher_NoMatchFound(realGlob,msg));
                    return true;
                }
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.error(
                                                 Messages.CloneWorkspacePublisher_FailedToArchive(realGlob)));
                return true;
            } catch (InterruptedException e) {
                e.printStackTrace(listener.error(
                                                 Messages.CloneWorkspacePublisher_FailedToArchive(realGlob)));
                return true;
            }

        }
        else {
            listener.getLogger().println(Messages.CloneWorkspacePublisher_CriteriaNotMet(criteriaResult));
            return true;
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