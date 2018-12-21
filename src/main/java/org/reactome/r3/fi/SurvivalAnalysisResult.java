/*
 * Created on Feb 24, 2011
 *
 */
package org.reactome.r3.fi;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * This simple class is used to encode results from R survival analysis script.
 * @author wgm
 *
 */
@XmlRootElement
public class SurvivalAnalysisResult extends ProcessCallResult {
    private String plotFileName;
    // Plot encoded in Based64 in PDF
    private String plotResult;
    
    public SurvivalAnalysisResult() {
    }
    
    public String getPlotResult() {
        return plotResult;
    }

    public void setPlotResult(String plotResult) {
        this.plotResult = plotResult;
    }

    public String getPlotFileName() {
        return plotFileName;
    }

    public void setPlotFileName(String plotFileName) {
        this.plotFileName = plotFileName;
    }
    
}
