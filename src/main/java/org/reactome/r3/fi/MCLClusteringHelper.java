/*
 * Created on Apr 7, 2011
 *
 */
package org.reactome.r3.fi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.reactome.r3.util.FileUtility;
import org.reactome.r3.util.ProcessRunner;

/**
 * This helper class is used to do MCL clustering.
 * @author wgm
 */
public class MCLClusteringHelper extends ProcessCallerWrapper {
    // For some unknown reason, a bash script has to be used so that
    // parameters can be passed into MCL script.
    private String mclScript;
    private boolean keepTempFile;
    
    public MCLClusteringHelper() {
    }
    
    public boolean isKeepTempFile() {
        return keepTempFile;
    }

    public void setKeepTempFile(boolean keepTempFile) {
        this.keepTempFile = keepTempFile;
    }

    public String getMclScript() {
        return mclScript;
    }

    public void setMclScript(String mclScript) {
        this.mclScript = mclScript;
    }

    /**
     * This is the method to do MCL clustering for weighted FI network. The method is
     * synchronized to avoid any file name conflicting. This method is similar to 
     * SurivalANalyzerHelper.doSurvivalAnalysis(). In the future, it should be considered
     * to unite these two classes together.
     * @param query String converted from Set<String>. Element in Set is FI \t Corr.
     * @param inflation the only parameter that can be ajusted by user for MCL.
     * @return a list of clusters. A cluster is a set of genes.
     * @throws IOException
     */
    public synchronized MCLClusteringResult cluster(Set<String> fisWithCorrs,
                                                    Double inflation) throws Exception {
        fisWithCorrs = validateWeights(fisWithCorrs);
        // Get a temp file name: assume at least several milli-second is needed.
        String tempFilePre = getTempFileNamePre();
        // For score file
        File fiFile = new File(tempDirName, tempFilePre + "_FIsWithCorrs.txt");
        File outFile = new File(tempDirName, tempFilePre + "_MCLResult.txt");
        generateTempFile(fisWithCorrs, 
                         fiFile); 
        ProcessRunner runner = getProcessRunner();
        List<String> parameters = new ArrayList<String>();
        // Have to use a bash script. Make sure bash is in the server-side
        parameters.add("bash");
        parameters.add(mclScript);
        // The following parameters are passed into the shell script.
        parameters.add(fiFile.getAbsolutePath());
        parameters.add(inflation + "");
        parameters.add(outFile.getAbsolutePath());
        MCLClusteringResult result = new MCLClusteringResult();
        try {
            String[] output = runner.runScript(parameters.toArray(new String[0]));
            result.setOutput(output[0]);
            result.setError(output[1]);
            // Need to read file
            ArrayList<String> clusters = new ArrayList<String>();
            // For some unknown reason, output from MCL is redirected to error stream.
            // The following check should not be used.
//            if (result.getError() == null || result.getError().length() == 0) {
            if (outFile.exists()) { // Check if the outFile is created. If true, the running should be successful.
                // This should be a successful run
//                System.out.println("Processing clustering results:");
                FileUtility fu = new FileUtility();
                fu.setInput(outFile.getAbsolutePath());
                String line = null;
                while ((line = fu.readLine()) != null) {
                    clusters.add(line);
                }
                fu.close();
            }
            result.setClusters(clusters);
        }
        catch(Exception e) {
            fiFile.delete();
            outFile.delete();
            throw e;
        }
        // Temp files should be deleted
        if (!keepTempFile) {
            fiFile.delete();
            outFile.delete();
        }
        return result;
    }
    
    /**
     * Have to make sure all weights no negative
     */
    private Set<String> validateWeights(Set<String> fisWithCorrs) {
        double min = Integer.MAX_VALUE;
        for (String fiWithCor : fisWithCorrs) {
            String[] tokens = fiWithCor.split("\t");
            double cor = Double.parseDouble(tokens[2]);
            if (min > cor)
                min = cor;
        }
        if (min >= 0.0d)
            return fisWithCorrs;
        // Need to adjust so that all values can be positive
        Set<String> rtn = new HashSet<String>();
        for (String fiWithCor : fisWithCorrs) {
            String[] tokens = fiWithCor.split("\t");
            double cor = Double.parseDouble(tokens[2]);
            cor -= min;
            String newFIWithCor = tokens[0] + "\t" + tokens[1] + "\t" + cor;
            rtn.add(newFIWithCor);
        }
        return rtn;
    }
    
    
    private void generateTempFile(Set<String> fisWithCorrs, File file) throws IOException {
        FileUtility fu = new FileUtility();
        fu.setOutput(file.getAbsolutePath());
        for (String line : fisWithCorrs)
            fu.printLine(line);
        fu.close();
    }
    
    @Test
    public void testCluster() throws Exception {
        tempDirName = "/Users/wgm/Documents/temp/";
        mclScript = "/Users/wgm/Documents/EclipseWorkspace/caBigR3WebApp/WebContent/WEB-INF/mcl_script.sh";
        // Load FIsWithCorrs
        FileUtility fu = new FileUtility();
        String fiCorFileName = "../caBigR3/FIsWithCorrs.txt";
        Set<String> fisWithCorrs = fu.loadInteractions(fiCorFileName);
        MCLClusteringResult result = cluster(fisWithCorrs, 5.0d);
        System.out.println("Output: " + result.getOutput());
        System.out.println("Error: " + result.getError());
        System.out.println("Clusters: " + result.getClusters().size());
        for (String cluster : result.getClusters()) {
            System.out.println(cluster);
        }
    }
    
    @Test
    public void testMCL() throws IOException {
        ProcessRunner runner = new ProcessRunner();
        runner.runScript(new String[] {"/Users/wgm/ProgramFiles/mcl/bin/mcl"});
    }
        
}
