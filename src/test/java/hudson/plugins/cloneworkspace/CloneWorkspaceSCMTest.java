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

import hudson.FilePath;

import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.ExtractResourceWithChangesSCM;
import org.jvnet.hudson.test.ExtractChangeLogParser;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.UnstableBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class CloneWorkspaceSCMTest extends HudsonTestCase {

    public void testBasicCloning() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject();
        buildAndAssertSuccess(parentJob);

        FreeStyleProject childJob = createCloneChildProject();
        buildAndAssertSuccess(childJob);

        FreeStyleBuild fb = childJob.getLastBuild();

        FilePath ws = fb.getWorkspace();

        assertTrue("pom.xml should exist", ws.child("pom.xml").exists());
    }

    public void testSlaveCloning() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject();
        parentJob.setAssignedLabel(createSlave(new Label("parentSlave")).getSelfLabel());

        buildAndAssertSuccess(parentJob);

        FreeStyleProject childJob = createCloneChildProject();
        childJob.setAssignedLabel(createSlave(new Label("childSlave")).getSelfLabel());
        buildAndAssertSuccess(childJob);

        FreeStyleBuild fb = childJob.getLastBuild();

        FilePath ws = fb.getWorkspace();

        assertTrue("pom.xml should exist", ws.child("pom.xml").exists());
    }

    public void testGlobCloning() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject(new CloneWorkspacePublisher("moduleB/**/*", "Any"));
        
        buildAndAssertSuccess(parentJob);

        FreeStyleProject childJob = createCloneChildProject();
        buildAndAssertSuccess(childJob);

        FreeStyleBuild fb = childJob.getLastBuild();

        FilePath ws = fb.getWorkspace();

        assertFalse("pom.xml should NOT exist", ws.child("pom.xml").exists());
        assertTrue("moduleB/pom.xml should exist", ws.child("moduleB").child("pom.xml").exists());
    }

    public void testNoParentCloningFails() throws Exception {
        FreeStyleProject childJob = createCloneChildProject();

        assertBuildStatus(Result.FAILURE, childJob.scheduleBuild2(0).get());
    }

    public void testNotFailedCriteriaDoesNotAcceptFailure() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject();

        parentJob.getBuildersList().add(new FailureBuilder());
        
        assertBuildStatus(Result.FAILURE, parentJob.scheduleBuild2(0).get());
        
        FreeStyleProject childJob = createCloneChildProject(new CloneWorkspaceSCM("parentJob", "Not Failed"));
        assertBuildStatus(Result.FAILURE, childJob.scheduleBuild2(0).get());
    }

    public void testSuccessfulCriteriaDoesNotAcceptUnstable() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject();

        parentJob.getBuildersList().add(new UnstableBuilder());
        
        assertBuildStatus(Result.UNSTABLE, parentJob.scheduleBuild2(0).get());

        FreeStyleProject childJob = createCloneChildProject(new CloneWorkspaceSCM("parentJob", "Successful"));
        assertBuildStatus(Result.FAILURE, childJob.scheduleBuild2(0).get());
    }

    public void testNotFailedCriteriaDoesAcceptUnstable() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject();

        parentJob.getBuildersList().add(new UnstableBuilder());
        
        assertBuildStatus(Result.UNSTABLE, parentJob.scheduleBuild2(0).get());

        FreeStyleProject childJob = createCloneChildProject(new CloneWorkspaceSCM("parentJob", "Not Failed"));
        assertBuildStatus(Result.SUCCESS, childJob.scheduleBuild2(0).get());
    }

    public void testNotFailedParentCriteriaDoesNotArchiveFailure() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject(new CloneWorkspacePublisher("**/*", "Not Failed"));

        parentJob.getBuildersList().add(new FailureBuilder());
        
        assertBuildStatus(Result.FAILURE, parentJob.scheduleBuild2(0).get());

        FreeStyleProject childJob = createCloneChildProject();
        assertBuildStatus(Result.FAILURE, childJob.scheduleBuild2(0).get());
    }

    public void testCulprits() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject();
        buildAndAssertSuccess(parentJob);

        FreeStyleProject childJob = createCloneChildProject();
        buildAndAssertSuccess(childJob);

        FreeStyleBuild fb = childJob.getLastBuild();
        
        final Set<User> culprits = fb.getCulprits();
        assertEquals("The build should have only one culprit", 1, culprits.size());
        assertEquals("", "testuser", culprits.iterator().next().getFullName());
    }

    public void testChangeLogSetNotEmpty() throws Exception {
        FreeStyleProject parentJob = createCloneParentProject();
        buildAndAssertSuccess(parentJob);

        FreeStyleProject childJob = createCloneChildProject();
        buildAndAssertSuccess(childJob);

        FreeStyleBuild fb = childJob.getLastBuild();
        
        assertFalse("ChangeLogSet should not be empty.", fb.getChangeSet().isEmptySet());

        List<String> changedFiles = new ArrayList<String>();

        for (Entry e : fb.getChangeSet()) {
            for (String f : e.getAffectedPaths()) {
                changedFiles.add(f);
            }
        }

        assertTrue("ChangeLogSet should contain moduleB/src/main/java/test/AppB.java but does not", changedFiles.contains("moduleB/src/main/java/test/AppB.java"));
    }

    private FreeStyleProject createCloneChildProject() throws Exception {
        return createCloneChildProject(new CloneWorkspaceSCM("parentJob", "any"));
    }

    private FreeStyleProject createCloneChildProject(CloneWorkspaceSCM cws) throws Exception {
        FreeStyleProject childJob = createFreeStyleProject();
        childJob.setScm(cws);

        return childJob;
    }
    
    private FreeStyleProject createCloneParentProject() throws Exception {
        return createCloneParentProject(new CloneWorkspacePublisher("**/*", "Any"));
    }
    
    private FreeStyleProject createCloneParentProject(CloneWorkspacePublisher cwp) throws Exception {
        FreeStyleProject parentJob = createFreeStyleProject("parentJob");
        parentJob.setScm(new ExtractResourceWithChangesSCM(getClass().getResource("maven-multimod.zip"),
                                                           getClass().getResource("maven-multimod-changes.zip")));
        parentJob.getPublishersList().add(cwp);

        return parentJob;
    }
        
}