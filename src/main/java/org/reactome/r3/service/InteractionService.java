/*
 * Created on Oct 13, 2006
 *
 */
package org.reactome.r3.service;

import java.util.List;
import java.util.Set;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.codehaus.jra.Get;
import org.codehaus.jra.HttpResource;
import org.reactome.funcInt.Evidence;
import org.reactome.funcInt.Interaction;
import org.reactome.funcInt.ReactomeSource;

@WebService
public interface InteractionService {
    
    @Get
    @HttpResource(location = "/queryForOr/{ids}")
    public List<Interaction> queryForOr(@WebParam(name="ids")
                                        String accessionQuery);
    
    public List<Interaction> queryForAnd(String accessionQuery);
    
    public List<Interaction> queryFIs(String sourceQuery,
                                      String targetQuery);
    
    public Set<String> loadAllFIs();
    
    public String queryForOrInJSON(String accessionQuery);
    
    public Set<ReactomeSource> querySourcesForInteractionId(long id);
    
    public Evidence queryEvidenceForInteractionId(long id);
    
    public Interaction loadInteraction(long id);
}
