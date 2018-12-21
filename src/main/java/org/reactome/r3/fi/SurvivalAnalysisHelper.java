/*
 * Created on Feb 24, 2011
 *
 */
package org.reactome.r3.fi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.reactome.r3.util.FileUtility;
import org.reactome.r3.util.ProcessRunner;

/**
 * This helper class is used to do survival analysis.
 * @author wgm
 *
 */
public class SurvivalAnalysisHelper extends ProcessCallerWrapper {
    // The R script that is used to do survival analysis
    private String rScript;
    private FileUtility fu;
    // The path to Rscript command
    private String rScriptCommand = "Rscript"; // Default value
    
    public SurvivalAnalysisHelper() {
        fu = new FileUtility();
    }
    
    public String getrScriptCommand() {
        return rScriptCommand;
    }

    public void setrScriptCommand(String rScriptCommand) {
        this.rScriptCommand = rScriptCommand;
    }

    public String getrScript() {
        return rScript;
    }

    public void setrScript(String rScript) {
        this.rScript = rScript;
    }

    /**
     * This is the method to do actual survival analysis. This method is synchronized, and only
     * one thread can access this method at the same time to avoid any file name conflict. This class
     * should be initialized as a singleton in a server-side application. The parameters passed
     * to the process should be as following:
     * "Rscript script.name score.file.name clin.file.name coxph|kaplan-meier module.index(zero-based) 
     * plot.file.name(optional) PDF|PNG(must if plot.file.name)"
     * @param query
     * @param model
     * @param module
     * @Param two element String list: the first is output from System.out and 
     * the second from System.err.
     * @throws IOException
     */
    public synchronized SurvivalAnalysisResult doSurvivalAnalysis(String query,
                                                                  String model,
                                                                  String module) throws IOException {
        // Get a temp file name: assume at least several milli-second is needed.
        String fileNamePre = super.getTempFileNamePre();
        // For score file
        File scoreFile = new File(tempDirName, fileNamePre + "_score.txt");
        File clinFile = new File(tempDirName, fileNamePre + "_clin.txt");
        generateTempFiles(query, 
                          scoreFile, 
                          clinFile);
        return doSurvivalAnalysis(scoreFile, 
                                  clinFile, 
                                  model, 
                                  module,
                                  fileNamePre,
                                  true);
    }

    public synchronized SurvivalAnalysisResult doSurvivalAnalysis(File scoreFile,
                                                                  File clinFile,
                                                                  String model,
                                                                  String module,
                                                                  String fileNamePre,
                                                                  boolean deleteTempFile) throws IOException {
        ProcessRunner runner = getProcessRunner();
        List<String> parameters = new ArrayList<String>();
        parameters.add(rScriptCommand);
        parameters.add(rScript);
        parameters.add(scoreFile.getAbsolutePath());
        parameters.add(clinFile.getAbsolutePath());
        parameters.add(model);
        File plotFile = null;
        if (module != null) {
            parameters.add(module);
            if (model.equals("kaplan-meier")) {
                // Only kaplan-meyer can generate plot
                // Plot file. Always generate PDF file
                plotFile = new File(tempDirName, fileNamePre + "_plot.pdf");
                parameters.add(plotFile.getAbsolutePath());
                parameters.add("PDF");
            }
        }
        String[] output = runner.runScript(parameters.toArray(new String[0]));
        SurvivalAnalysisResult result = new SurvivalAnalysisResult();
        result.setOutput(output[0]);
        result.setError(output[1]);
        if (deleteTempFile) {
            // Temp files should be deleted
            scoreFile.delete();
            clinFile.delete();
        }
        if (plotFile != null) {
            // Don't want to provide the whole path for security purpose
            // Provide name for file type
            result.setPlotFileName(plotFile.getName());
            if (!plotFile.exists())
                plotFile.createNewFile();
            String encoded = fu.encodeFileInBase64(plotFile.getAbsolutePath());
            result.setPlotResult(encoded);
            if (deleteTempFile)
                plotFile.delete();
        }
        return result;
    }

    private void generateTempFiles(String query, File scoreFile, File clinFile) throws IOException {
        StringReader sis = new StringReader(query);
        BufferedReader br = new BufferedReader(sis);
        String line = null;
        boolean isInScore = false;
        boolean isInClin = false;
        while ((line = br.readLine()) != null) {
           if (line.startsWith("#Sample score matrix begin!")) {
                isInScore = true;
                fu.setOutput(scoreFile.getAbsolutePath());
            }
            else if (line.startsWith("#Sample score matrix end!")) {
                isInScore = false;
                fu.close();
            }
            else if (line.startsWith("#Clin matrix begin!")) {
                isInClin = true;
                fu.setOutput(clinFile.getAbsolutePath());
            }
            else if (line.startsWith("#Clin matrix end!")) {
                isInClin = false;
                fu.close();
            }
            else if (isInScore || isInClin)
                fu.printLine(line);
        }
        br.close();
        sis.close();
    }
    
}
