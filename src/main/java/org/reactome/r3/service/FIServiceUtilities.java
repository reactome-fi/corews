/*
 * Created on Jun 14, 2010
 *
 */
package org.reactome.r3.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.reactome.funcInt.Interaction;
import org.reactome.funcInt.Protein;

public class FIServiceUtilities {
    
    public static String[] splitQuery(String query) {
        return query.split("(,| |\\\t)+");
    }
    
    public static Protein constructProteinFromName(Map<String, Protein> nameToProtein,
                                                   String name) {
        Protein protein = nameToProtein.get(name);
        if (protein == null) {
            protein = new Protein();
            // Assign name to both protein's name and shortName attribute
            protein.setName(name);
            protein.setShortName(name);
            nameToProtein.put(name, protein);
        }
        return protein;
    }
    
    /**
     * Extract a list of FI partners from a list of Interaction. The returned
     * genes are in a String delimited by "," without containing the genes
     * from the query text. 
     * @param query a list of genes delimited by tab or comma
     * @param interactions a list of interactions
     * @param needValidate true to validate interactions containing genes from the list.
     * @return
     */
    public static Set<String> extractFIPartners(String query,
                                                List<Interaction> interactions) {
        Set<String> partners = new HashSet<String>();
        String[] genes = splitQuery(query);
        Set<String> queryGenes = new HashSet<String>(Arrays.asList(genes));
        for (Interaction interaction : interactions) {
            String gene1 = interaction.getFirstProtein().getShortName();
            String gene2 = interaction.getSecondProtein().getShortName();
            if (queryGenes.contains(gene1) && queryGenes.contains(gene2))
                continue; // Both are in the query genes
            if (queryGenes.contains(gene1))
                partners.add(gene2);
            else if (queryGenes.contains(gene2))
                partners.add(gene1);
        }
        return partners;
    }
    
    /**
     * Do a filtering of a list of FIs. FIs in the second passed list of FIs should
     * be filtered out from the first list.
     * @param srcFIs
     * @param toBeRemovedFIs
     */
    public static void filterFIs(List<Interaction> srcFIs,
                                 List<Interaction> toBeRemovedFIs) {
        for (Iterator<Interaction> it = srcFIs.iterator(); it.hasNext();) {
            Interaction fi = it.next();
            String name1 = fi.getFirstProtein().getName();
            String name2 = fi.getSecondProtein().getName();
            // Check if this FI can be extracted from PathwayDiagram
            boolean isFound = false;
            for (Interaction fi1 : toBeRemovedFIs) {
                String name11 = fi1.getFirstProtein().getName();
                String name12 = fi1.getSecondProtein().getName();
                if (name1.equals(name11) && name2.equals(name12)) {
                    isFound = true;
                    break;
                }
                if (name1.equals(name12) && name2.equals(name11)) {
                    isFound = true;
                    break;
                }
            }
            if (isFound)
                it.remove();
        }
    }
    
    public static List<Interaction> convertFIsToInteractions(Set<String> fis) {
        // Convert to Intearction
        Map<String, Protein> nameToProtein = new HashMap<String, Protein>();
        int index = 0;
        List<Interaction> rtn = new ArrayList<Interaction>(fis.size());
        for (String fi : fis) {
            index = fi.indexOf("\t");
            String name1 = fi.substring(0, index);
            Protein protein1 = FIServiceUtilities.constructProteinFromName(nameToProtein, name1);
            String name2 = fi.substring(index + 1);
            Protein protein2 = FIServiceUtilities.constructProteinFromName(nameToProtein, name2);
            Interaction interaction = new Interaction();
            interaction.setFirstProtein(protein1);
            interaction.setSecondProtein(protein2);
            rtn.add(interaction);
        }
        return rtn;
    }
    
    /**
     * A simple utility to construct a parameter string for hibernate query.
     * @param accessions
     * @return
     */
    public static String generateInParas(List<String> accessions) {
        StringBuilder paras = new StringBuilder();
        paras.append("(");
        for (Iterator<String> it = accessions.iterator(); it.hasNext();) {
            it.next(); // Move the position
            paras.append("?");
            if (it.hasNext())
                paras.append(",");
        }
        paras.append(")");
        return paras.toString();
    }
    
}
