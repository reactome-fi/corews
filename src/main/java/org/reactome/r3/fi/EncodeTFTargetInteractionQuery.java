/*
 * Created on Mar 1, 2012
 *
 */
package org.reactome.r3.fi;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.junit.Test;
import org.reactome.r3.util.FileUtility;
import org.reactome.r3.util.InteractionUtilities;

/**
 * This method is used to query TF/Target interctions from encode.
 * @author gwu
 *
 */
public class EncodeTFTargetInteractionQuery {
    private final static Logger logger = Logger.getLogger(EncodeTFTargetInteractionQuery.class);
    // Two types TF/Target interactions. Both of them are cached
    private Set<String> distalInteractions;
    private Set<String> proximalInteractions;
    private String accToNameMapFile;
    
    public EncodeTFTargetInteractionQuery() {
    }
    
    public String getAccToNameMapFile() {
        return accToNameMapFile;
    }

    public void setAccToNameMapFile(String accToNameMapFile) {
        this.accToNameMapFile = accToNameMapFile;
    }

    /**
     * Query TF/Target interactions for a protein UniProt accession number. The query protein
     * can be either TF or target. 
     * @param accession
     * @return a set of TF/Target interactions (TF\tTarget).
     */
    public Set<String> queryInteactions(String accession) {
        Set<String> rtn = new HashSet<String>();
        FileUtility fu = new FileUtility();
        try {
            Map<String, String> accToName = fu.importMap(accToNameMapFile);
            String name = accToName.get(accession);
            if (name != null) {
                queryInteractions(name, proximalInteractions, rtn);
                queryInteractions(name, distalInteractions, rtn);
            }
        }
        catch(IOException e) {
            logger.error("Error in queryInteractions", e);
        }
        return rtn;
    }
    
    public String getInteractionsInMITab(String accession) {
        StringBuilder builder = new StringBuilder();
        FileUtility fu = new FileUtility();
        try {
            Map<String, String> accToName = fu.importMap(accToNameMapFile);
            String name = accToName.get(accession);
            if (name != null) {
                Map<String, String> nameToAcc = InteractionUtilities.swapKeyValue(accToName);
                getInteractionsInMITab(accession, name, proximalInteractions, builder, nameToAcc);
                getInteractionsInMITab(accession, name, distalInteractions, builder, nameToAcc);
            }
        }
        catch(IOException e) {
            logger.error("Error in queryInteractions", e);
        }
        return builder.toString();
    }
    
    private void getInteractionsInMITab(String accession,
                                        String query,
                                        Set<String> interactions,
                                        StringBuilder rtn,
                                        Map<String, String> nameToAcc) {
        for (String interaction : interactions) {
            String[] tokens = interaction.split("\t");
            if (tokens[0].equals(query)) {
                rtn.append("uniprotkb:").append(accession).append("\t");
                rtn.append("uniprotkb:").append(nameToAcc.get(tokens[1])).append("\t");
            }
            else if (tokens[1].equals(query)) {
                rtn.append("uniprotkb:").append(nameToAcc.get(tokens[0])).append("\t");
                rtn.append("uniprotkb:").append(accession).append("\t");
            }
            else
                continue;
            rtn.append("uniprotkb:").append(tokens[0]).append("\t");
            rtn.append("uniprotkb:").append(tokens[1]);
            for (int i = 4; i < 15; i++)
                rtn.append("\t-"); // Required but no information is provided
            rtn.append("\n");
        }
    }
    
    private void queryInteractions(String query,
                                   Set<String> interactions,
                                   Set<String> results) {
        for (String interaction : interactions) {
            String[] tokens = interaction.split("\t");
            if (tokens[0].equals(query) || tokens[1].equals(query))
                results.add(interaction);
        }
    }

    /**
     * Set the file name for distal interactions.
     * @param fileName
     */
    public void setDistalFileName(String fileName) {
        distalInteractions = loadInteractions(fileName);
    }
    
    /**
     * Set the file name for proximal interactions.
     * @param fileName
     */
    public void setProximalFileName(String fileName) {
        proximalInteractions = loadInteractions(fileName);
    }
    
    private Set<String> loadInteractions(String fileName)  {
        Set<String> interactions = new HashSet<String>();
        try {
            FileUtility fu = new FileUtility();
            fu.setInput(fileName);
            String line = null;
            //            int repeat = 0;
            while ((line = fu.readLine()) != null) {
                //                System.out.println(line);
                String[] tokens = line.split("(\t| )+"); // May be tab or just empty space
                String interaction = null;
                if (tokens.length == 2) {
                    if (!tokens[1].equals("proximal_raw") &&
                            !tokens[1].equals("distal"))
                        interaction = tokens[0] + "\t" + tokens[1];
                }
                else if (tokens.length > 2 && tokens[2].length() > 0) {
                    interaction = tokens[0] + "\t" + tokens[2];
                }
                if (interaction == null)
                    continue;
                //                if (interactions.contains(interaction)) {
                //                    System.out.println(interaction + " is in the set!");
                //                    repeat ++;
                //                    continue;
                //                }
                //                else
                interactions.add(interaction);
            }
            fu.close();
        }
        catch(IOException e) {
            logger.error("Error in loadInteractions()", e);
        }
        return interactions;
    }
    
    @Test
    public void testQueryFIs() {
        String distalFileName = "/Users/gwu/datasets/encode/Paper/Distal.txt";
        setDistalFileName(distalFileName);
        String proximalFileName = "/Users/gwu/datasets/encode/Paper/Proximal_filtered.txt";
        setProximalFileName(proximalFileName);
        String accToNameFile = "WebContent/WEB-INF/ProteinAccessionToName_070110.txt";
        setAccToNameMapFile(accToNameFile);
        String proteinAccession = "O43521"; // BIM1 (BCL2L11)
        Set<String> interactions = queryInteactions(proteinAccession);
        System.out.println("Total interactions: " + interactions.size());
        for (String interaction : interactions)
            System.out.println(interaction);
        String mitab = getInteractionsInMITab(proteinAccession);
        System.out.println("\nIn PSI MITAB:\n" + mitab);
    }
    
    @Test
    public void testLoad() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        System.out.println("Total memory: " + totalMemory);
        long freeMemory = Runtime.getRuntime().freeMemory();
        System.out.println("Free memory: " + freeMemory);
        String distalFileName = "/Users/gwu/datasets/encode/Paper/Distal.txt";
        setDistalFileName(distalFileName);
        System.out.println("Total memory after loading distal: " + Runtime.getRuntime().totalMemory());
        System.out.println("Free memory: " + Runtime.getRuntime().freeMemory());
        String proximalFileName = "/Users/gwu/datasets/encode/Paper/Proximal_filtered.txt";
        setProximalFileName(proximalFileName);
        System.out.println("Total memory after loading proximal: " + Runtime.getRuntime().totalMemory());
        System.out.println("Free memory: " + Runtime.getRuntime().freeMemory());
        System.out.println("Total distal intearctions: " + distalInteractions.size());
        System.out.println("Total proximal interactions: " + proximalInteractions.size());
        Set<String> totalInteractions = new HashSet<String>();
        totalInteractions.addAll(distalInteractions);
        totalInteractions.addAll(proximalInteractions);
        System.out.println("Total interactions: " + totalInteractions.size());
//        proximalFileName = "/Users/gwu/datasets/encode/Paper/Proximal_raw.txt";
//        setProximalFileName(proximalFileName);
//        System.out.println("Total memory after loading raw proximal: " + Runtime.getRuntime().totalMemory());
//        System.out.println("Total proximal interactions (raw): " + proximalInteractions.size());
//        System.out.println("Free memory: " + Runtime.getRuntime().freeMemory());
    }
    
}
