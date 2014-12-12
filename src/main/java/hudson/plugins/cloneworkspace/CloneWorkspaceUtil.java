/*
 * The MIT License
 * 
 * Copyright (c) 2010, Andrew Bayer
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
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.Result;

/**
 * Utility class for {@link CloneWorkspaceSCM} and {@link CloneWorkspacePublisher}.
 *
 * @author Andrew Bayer
 */

public class CloneWorkspaceUtil {

    public static Result getResultForCriteria(String criteria) {
        Result criteriaResult = Result.FAILURE;

        if (criteria.equals("Not Failed")) {
            criteriaResult = Result.UNSTABLE;
        }
        else if (criteria.equals("Successful")) {
            criteriaResult = Result.SUCCESS;
        }

        return criteriaResult;
    }

    public static AbstractBuild<?,?> getMostRecentBuildForCriteria(AbstractProject<?,?> project, String criteria) {
        return getMostRecentBuildForCriteria(project.getLastBuild(), getResultForCriteria(criteria));
    }
    
    public static AbstractBuild<?,?> getMostRecentBuildForCriteria(AbstractBuild<?,?> baseBuild, String criteria) {
        return getMostRecentBuildForCriteria(baseBuild, getResultForCriteria(criteria));
    }

    public static AbstractBuild<?,?> getMostRecentBuildForCriteria(AbstractBuild<?,?> baseBuild, Result criteriaResult) {
        if ((baseBuild == null)
            || ((!baseBuild.isBuilding()) && (baseBuild.getResult() != null)
                && (baseBuild.getResult().isBetterOrEqualTo(criteriaResult)))) {
            return baseBuild;
        }
        else {
            return getMostRecentBuildForCriteria(baseBuild.getPreviousBuild(), criteriaResult);
        }
    }


    public static AbstractBuild<?,?> getMostRecentBuildForCriteriaWithSnapshot(AbstractBuild<?,?> baseBuild, String criteria) {
        return getMostRecentBuildForCriteriaWithSnapshot(baseBuild, getResultForCriteria(criteria));
    }

    public static AbstractBuild<?,?> getMostRecentBuildForCriteriaWithSnapshot(AbstractProject<?,?> project, String criteria) {
        return getMostRecentBuildForCriteriaWithSnapshot(project.getLastBuild(), getResultForCriteria(criteria));
    }

    public static AbstractBuild<?,?> getMostRecentBuildForCriteriaWithSnapshot(AbstractBuild<?,?> baseBuild, Result criteriaResult) {
        AbstractBuild<?,?> criteriaBuild = getMostRecentBuildForCriteria(baseBuild, criteriaResult);

        if (criteriaBuild!=null) {
            if (criteriaBuild.getAction(WorkspaceSnapshot.class)!=null) {
                return criteriaBuild;
            }
            else {
                return getMostRecentBuildForCriteriaWithSnapshot(criteriaBuild.getPreviousBuild(), criteriaResult);
            }
        }
        else {
            return null;
        }
    }

    public static String getFileNameForMethod(String method)
    {
        if ("ZIP".equals(method)) {
            return "workspace.zip";
        } else if ("TARONLY".equals(method)) {
            return "workspace.tar";
        } else {
            return "workspace.tar.gz";
        }
    }
}
