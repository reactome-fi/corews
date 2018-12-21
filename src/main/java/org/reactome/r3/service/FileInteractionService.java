/*
 * Created on Jun 14, 2010
 *
 */
package org.reactome.r3.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.reactome.funcInt.Evidence;
import org.reactome.funcInt.Interaction;
import org.reactome.funcInt.ReactomeSource;
import org.reactome.r3.util.FileUtility;
import org.reactome.r3.util.InteractionUtilities;

/**
 * Use this InteractionService to query FIs based on FIs loaded from a pre-generated file. Using a 
 * pre-generated FI file can increase performance tremendously.
 * @author wgm
 *
 */
public class FileInteractionService implements InteractionService {
    // Delegator for some methods that cannot be implemented based on pre-generated file.
    private InteractionService delegator;
    private Set<String> loadedFIs;
    
    public FileInteractionService() {
    }
    
    public void setDelegator(InteractionService service) {
        this.delegator = service;
    }
    
    public InteractionService getDelegator() {
        return this.delegator;
    }
    
    public void setFIFile(String fileName) throws IOException {
        FileUtility fu = new FileUtility();
        //long mem1 = Runtime.getRuntime().totalMemory();
        loadedFIs = fu.loadInteractions(fileName);
        //long mem2 = Runtime.getRuntime().totalMemory();
        //System.out.println("Memory used for loading FIs: " + (mem2 - mem1));
    }

    public Interaction loadInteraction(long id) {
        return delegator.loadInteraction(id);
    }

    public Evidence queryEvidenceForInteractionId(long id) {
        return delegator.queryEvidenceForInteractionId(id);
    }

    public List<Interaction> queryForAnd(String accessionQuery) {
        String[] queries = FIServiceUtilities.splitQuery(accessionQuery);
        // Need to create Set
        Set<String> fis = null;
        if (queries.length == 1)
            fis = InteractionUtilities.grepFIsContains(queries[0], loadedFIs);
        else
            fis = InteractionUtilities.getFIs(Arrays.asList(queries), loadedFIs);
        return FIServiceUtilities.convertFIsToInteractions(fis);
    }
    
    public List<Interaction> queryFIs(String srcQuery,
                                      String targetQuery) {
        // Don't use FIServiceUtilities since a protein name may contain
        // a space (" ").
//        String[] srcIds = FIServiceUtilities.splitQuery(srcQuery);
        String[] srcIds = srcQuery.split(",");
        Set<String> srcSet = new HashSet<String>();
        for (String id : srcIds)
            srcSet.add(id);
        String[] targetIds = targetQuery.split(",");
        Set<String> targetSet = new HashSet<String>();
        for (String id : targetIds)
            targetSet.add(id);
        Set<String> fis = new HashSet<String>();
        int index;
        String id1, id2;
        for (String fi : loadedFIs) {
            index = fi.indexOf("\t");
            id1 = fi.substring(0, index);
            id2 = fi.substring(index + 1);
            if ((srcSet.contains(id1) && targetSet.contains(id2)) ||
                (targetSet.contains(id1) && srcSet.contains(id2))) // No direction for FIs
                fis.add(fi);
        }
        return FIServiceUtilities.convertFIsToInteractions(fis);
    }

    public Set<String> loadAllFIs() {
        return this.loadedFIs;
    }
    
    public List<Interaction> queryForOr(String accessionQuery) {
        String[] queries = FIServiceUtilities.splitQuery(accessionQuery);
        // Need to create Set
        Set<String> fis = InteractionUtilities.grepFIsContains(Arrays.asList(queries),
                                                               loadedFIs);
        return FIServiceUtilities.convertFIsToInteractions(fis);
    }

    public String queryForOrInJSON(String accessionQuery) {
        return delegator.queryForOrInJSON(accessionQuery);
    }

    public Set<ReactomeSource> querySourcesForInteractionId(long id) {
        return delegator.querySourcesForInteractionId(id);
    }
    
}
