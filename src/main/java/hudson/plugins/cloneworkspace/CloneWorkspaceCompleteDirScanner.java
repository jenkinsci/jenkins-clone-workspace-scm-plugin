/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016, Schneider Electric
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

import hudson.Util;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import org.apache.commons.lang.ArrayUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * This custom DirScanner is based on {@link hudson.util.DirScanner.Filter}, supports Ant FileSet pattern as well as the
 * copy of empty directories.
 */
public class CloneWorkspaceCompleteDirScanner {

    /**
     * DirScanner class which will be used as parameter in the CloneWorkspacePublisher.snapshot function
     */
    public static class CompleteDirScanner extends DirScanner.Filter {
        private FileFilter filter;

        public CompleteDirScanner(FileFilter filter) {
            super(filter);
            this.filter = filter;
        }

        @Override
        public void scan(File dir, FileVisitor visitor) throws IOException {
            // Check if we can access the subfiles
            if(dir.listFiles() != null) {
                // Do not archive the workspace home directory (which will be named after the job name) by manually
                // scanning all subfiles and subdirectories
                for(File child : dir.listFiles()) {
                    // Avoid unintended directories at top level to be copied
                    if(filter.accept(child)) {
                        super.scan(child, visitor);
                    }
                }
            }
        }
    }

    /**
     * FileFilter class which {@link CompleteDirScanner} will take in parameter
     */
    public static class CompleteFileFilter implements FileFilter, Serializable {
        private static final long serialVersionUID = 1L;
        private List<File> includesList;

        public CompleteFileFilter(List<File> includesList) {
            this.includesList = includesList;
        }

        @Override
        public boolean accept(File file) {
            return includesList.contains(file);
        }
    }

    /**
     * Ant pattern to FileFilter converter (mimic the {@link hudson.util.DirScanner.Glob} behavior)
     */
    public static FileFilter AntToFileFilter(File directory, String includes, String excludes, boolean useDefaultExcludes) {
        // Identical steps as the DirScanner.Glob to interpret the Ant pattern
        FileSet fs = Util.createFileSet(directory, includes, excludes);
        fs.setDefaultexcludes(useDefaultExcludes);

        // Join lists to copy both files and directories
        DirectoryScanner ds = fs.getDirectoryScanner(new org.apache.tools.ant.Project());
        String[] includedFilesName = (String[]) ArrayUtils.addAll(ds.getIncludedDirectories(), ds.getIncludedFiles());

        // Create matching File object
        List<File> includedFiles = new ArrayList<File>();
        for(String itemPath : includedFilesName) {
            includedFiles.add(new File(directory, itemPath));
        }

        return new CompleteFileFilter(includedFiles);
    }
}
