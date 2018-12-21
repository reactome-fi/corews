/*
 * Created on Oct 12, 2010
 *
 */
package org.reactome.r3.fi;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.reactome.r3.util.FileUtility;
import org.reactome.r3.util.InteractionUtilities;

/**
 * This class is used to handle KEGG related stuff.
 * @author wgm
 *
 */
public class KEGGHelper {
    private Map<String, String> pathwayNameToId;
    private Map<String, String> geneNameToId;
    
    public KEGGHelper() {
    }
    
    public void setMapTitleFile(String fileName) throws IOException {
        pathwayNameToId = new HashMap<String, String>();
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        String line = null;
        while ((line = fu.readLine()) != null) {
            if (line.startsWith("#"))
                continue; // Comment lines
            String[] tokens = line.split("\t");
            pathwayNameToId.put(tokens[1], tokens[0]);
        }
        fu.close();
    }
    
    public void setHsaListFile(String fileName) throws IOException {
        geneNameToId = new HashMap<String, String>();
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        String line = null;
        int index = 0;
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            // The second part is for an empty string in the third column
            if (tokens.length < 3 || tokens[2].trim().length() == 0)
                continue;
            index = tokens[2].indexOf(" ");
            String geneName = tokens[2].substring(4, index);
            geneNameToId.put(geneName, tokens[1]);
        }
        fu.close();
    }
    
    public String getPathwayId(String pathwayName) {
        return pathwayNameToId.get(pathwayName);
    }
    
    public String getGeneId(String geneName) {
        return geneNameToId.get(geneName);
    }
    
    public String getGeneIds(String[] geneNames) {
        Set<String> ids = new HashSet<String>();
        for (String name : geneNames) {
            String id = geneNameToId.get(name);
            ids.add(id);
        }
        return InteractionUtilities.joinStringElements(", ", ids);
    }
}
