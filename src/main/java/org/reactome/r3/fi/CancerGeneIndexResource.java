/*
 * Created on Sep 22, 2010
 *
 */
package org.reactome.r3.fi;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.reactome.cancerindex.model.Sentence;
import org.reactome.r3.service.CancerGeneIndexDAO;
import org.springframework.context.annotation.Scope;

@Path("/cancerGeneIndex")
@Scope("Singleton")
public class CancerGeneIndexResource {
    //TODO: Still need to make sure all logger use the same logging mechanism. Right now,
    // Some use apache logging and some use Java logging.
    private static final Logger logger = Logger.getLogger(CancerGeneIndexResource.class.getName());
    private CancerGeneIndexDAO dao;
    
    public CancerGeneIndexResource() {
        
    }

    public CancerGeneIndexDAO getDao() {
        return dao;
    }

    public void setDao(CancerGeneIndexDAO dao) {
        this.dao = dao;
    }
    
    /**
     * Query disease codes for a set of genes.
     * @param query
     * @return
     */
    @Path("/queryGeneToDiseases")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String queryGeneToDiseases(String query) {
        Set<String> genes = new HashSet<String>();
        String[] lines = query.split("\n");
        for (String line : lines)
            genes.add(line);
        try {
            Map<String, Set<String>> geneToDiseases = dao.queryGeneToDiseaseCodes(genes);
            StringBuilder builder = new StringBuilder();
            for (String gene : geneToDiseases.keySet()) {
                builder.append(gene).append("\t");
                Set<String> diseases = geneToDiseases.get(gene);
                for (String disease : diseases)
                    builder.append(disease).append(",");
                builder.append("\n");
            }
            return builder.toString();
        }
        catch(Exception e) {
            logger.log(Level.SEVERE, 
                       "Error in queryDiseasesForGenes: \n" + query,
                       e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Path("/queryAnnotations")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Sentence> queryAnnotations(String gene) {
        try {
            List<Sentence> sentences = dao.queryAnnotations(gene);
            return sentences;
        }
        catch(Exception e) {
            logger.log(Level.SEVERE,
                       "Error in queryAnnotations: " + gene,
                       e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
}
