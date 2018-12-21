/*
 * Created on Mar 19, 2013
 *
 */
package org.reactome.r3.fi;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.reactome.pagerank.HeatKernelCalculator;
import org.reactome.pagerank.HotNetImplementation;
import org.reactome.pagerank.HotNetModule;
import org.reactome.pagerank.HotNetResult;
import org.reactome.r3.util.FileUtility;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * This helper class is used to as wrapper for org.reactome.pagerank.HotNetImplementation.
 * Some external configurations required by HotNetImplementation are set here.
 * @author gwu
 *
 */
public class HotNetAnalysisHelper {
    private static final Logger logger = Logger.getLogger(HotNetAnalysisHelper.class);
    private String fiFileName;
    private String heatKernelFileName;
    
    public HotNetAnalysisHelper() {
    }

    public String getFiFileName() {
        return fiFileName;
    }

    public void setFiFileName(String fiFileName) {
        this.fiFileName = fiFileName;
    }

    public String getHeatKernelFileName() {
        return heatKernelFileName;
    }

    public void setHeatKernelFileName(String heatKernelFileName) {
        this.heatKernelFileName = heatKernelFileName;
    }
    
    public HotNetResult doHotNetAnalysis(Map<String, Double> geneToScore,
                                         Double delta,
                                         Double fdrCutoff,
                                         Integer permutationNumber) throws Exception {
        Set<String> fis = new FileUtility().loadInteractions(fiFileName);
        HotNetImplementation hotNetImp = new HotNetImplementation();
        if (fdrCutoff == null)
            fdrCutoff = 0.25d; // Choose the default FDR value as 0.25
        hotNetImp.setFdrCutoff(fdrCutoff);
        HeatKernelCalculator helper = new HeatKernelCalculator();
        logger.info("Used memory before loading heat kernel: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        DoubleMatrix2D heatKernel = helper.loadSerializedHeatKernelMatrix(heatKernelFileName);
        logger.info("Used memory after loading heat kernel: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        boolean isAutoDeltaUsed = false;
        if (delta == null) {
            //            delta = hotNetImp.selectDeltaViaRealData(heatKernel,
            //                                                     fis, 
            //                                                     geneToScore);
            // Use a quick way based on the original HotNet Python code from the Raphael's group
            delta = hotNetImp.selectDeltaViaPreList(heatKernel, 
                                                    fis, 
                                                    geneToScore);
            isAutoDeltaUsed = true;
        }
        if (permutationNumber == null)
            permutationNumber = 100;
        hotNetImp.setPermutation(permutationNumber);
        List<HotNetModule> modules = hotNetImp.searchForModules(heatKernel,
                                                                fis, 
                                                                geneToScore, 
                                                                delta);
        HotNetResult result = new HotNetResult();
        result.setDelta(delta);
        result.setModules(modules);
        result.setFdrThreshold(fdrCutoff);
        result.setPermutation(permutationNumber);
        result.setUseAutoDelta(isAutoDeltaUsed);
        // Forge GC though it is not very useful
        heatKernel = null;
        System.gc(); 
        return result;
    }
    
}
