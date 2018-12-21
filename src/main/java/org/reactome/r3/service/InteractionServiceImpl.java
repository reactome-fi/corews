/*
 * Created on Oct 12, 2006
 *
 */
package org.reactome.r3.service;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.jws.WebService;

import org.reactome.funcInt.Evidence;
import org.reactome.funcInt.Interaction;
import org.reactome.funcInt.ReactomeSource;

/**
 * This class is used as service for doing some parsing from the client and passing
 * the actual query to FuncIntDAO class.
 * @author guanming
 *
 */
@WebService(endpointInterface = "org.reactome.r3.service.InteractionService")
public class InteractionServiceImpl implements InteractionService {
    private InteractionDAO dao;
    
    public InteractionServiceImpl() {
    }
    
    public void setInteractionDAO(InteractionDAO dao) {
        this.dao = dao;
    }
    
    public InteractionDAO getInteractionDAO() {
        return this.dao;
    }
    
    public Interaction loadInteraction(long id) {
        return dao.load(id);
    }
    
    public Set<ReactomeSource> querySourcesForInteractionId(long id) {
        Interaction interaction = dao.load(id);
        return interaction.getReactomeSources();
    }
    
    public Evidence queryEvidenceForInteractionId(long id) {
        Interaction interaction = dao.load(id);
        return interaction.getEvidence();
    }
    
    /**
     * Query for a list of Interaction for a list of UniProt accessions. These accessions
     * are common delimited.
     * @param accessionQuery
     * @return
     */
    public List<Interaction> queryForOr(String accessionQuery) {
        // Parse the passed accessionQuery
        String[] accessions = FIServiceUtilities.splitQuery(accessionQuery); // In case there is an empty
        return dao.search(Arrays.asList(accessions));
    }
    
    public String queryForOrInJSON(String accessionQuery) {
        List<Interaction> interactions = queryForOr(accessionQuery);
        StringBuilder builder = new StringBuilder();
        // Generate JSON string
        builder.append("{\"result\":[");
        for (Iterator<Interaction> it = interactions.iterator(); it.hasNext();) {
            Interaction in = it.next();
            builder.append("{\"id\":\"").append(in.getDbId()).append("\",");
            builder.append("\"firstProtein\":\"").append(in.getFirstProtein().getPrimaryAccession()).append("\",");
            builder.append("\"secondProtein\":\"").append(in.getSecondProtein().getPrimaryAccession()).append("\"}");
            if (it.hasNext())
                builder.append(",");
        }
        builder.append("]}");
        return builder.toString();
    }
    
    public List<Interaction> queryForAnd(String accessionQuery) {
        // Parse the passed accessionQuery
        String[] accessions = FIServiceUtilities.splitQuery(accessionQuery);
        // A special case: switch to fetch for FIs
        if (accessions.length == 1)
            return dao.search(Arrays.asList(accessions));
        else
            return dao.searchForAll(Arrays.asList(accessions));
    }
    
    public Set<String> loadAllFIs() {
        throw new IllegalStateException("This method is not implemented!");
    }
    
    public List<Interaction> queryFIs(String srcQuery, 
                                      String targetQuery) {
        throw new IllegalStateException("This method is not implemented!");
    }
}
