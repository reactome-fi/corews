/*
 * Created on Apr 7, 2011
 *
 */
package org.reactome.r3.fi;

import org.reactome.r3.util.ProcessRunner;

/**
 * An abstract class that wrap a ProcessCaller to do something like a command based
 * data analysis for the front end application.
 * @author wgm
 *
 */
public abstract class ProcessCallerWrapper {
    // Temp name used to store files
    protected String tempDirName;
    
    protected ProcessCallerWrapper() {
    }
    
    protected String getTempFileNamePre() {
        return System.currentTimeMillis() + "";
    }
    
    public void setTempDirName(String tempDirName) {
        this.tempDirName = tempDirName;
    }
    
    public String getTempDirName() {
        return this.tempDirName;
    }
    
    protected ProcessRunner getProcessRunner() {
        return new ProcessRunner();
    }
}
