/*
 * Created on Mar 3, 2015
 *
 */
package org.reactome.r3.fi;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.reactome.restfulapi.APIControllerHelper;
import org.reactome.restfulapi.models.DatabaseObject;
import org.reactome.restfulapi.models.Pathway;
import org.springframework.context.annotation.Scope;

/**
 * Some code copied from ReactomeRESTful API project for easy deploy and maintenance.
 * @author gwu
 */
@Path("/ReactomeRestful")
@Scope("Singleton") // single object instance per web application
public class ReactomeRESTAPI {
    private static final Logger logger = Logger.getLogger(ReactomeRESTAPI.class);
    private APIControllerHelper restHelper;
    
    public ReactomeRESTAPI() {
    }

    public APIControllerHelper getRestHelper() {
        return restHelper;
    }

    public void setRestHelper(APIControllerHelper restHelper) {
        this.restHelper = restHelper;
    }

    /**
     * Use this method to get a list of Pathways that have been listed in the 
     * FrontPage instance for other non-human species
     * @return
     */
    @GET
    @Path("/frontPageItems/{speciesName}")
    public List<Pathway> queryFrontPageItems(@PathParam("speciesName") String speciesName) {
        try {
            String decoded = URLDecoder.decode(speciesName, "utf-8");
            return restHelper.listFrontPageItem(decoded);
        }
        catch(UnsupportedEncodingException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Get a XML string for the pathway hierarchy for a specified species.
     * @param speciesName
     * @return
     */
    @GET
    @Path("/pathwayHierarchy/{speciesName}")
    public String getPathwayHierarchy(@PathParam("speciesName") String speciesName) {
        try {
            String decoded = URLDecoder.decode(speciesName, "utf-8");
            return restHelper.generatePathwayHierarchy(decoded);
        }
        catch(UnsupportedEncodingException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * @param PathwayId The ID of the Pathway
     * @param format The format which the Pathway will be rendered in
     * @return Base64 encoded String of the pathway diagram
     */
    @GET
    @Path("/pathwayDiagram/{dbId : \\d+}/{format: .+}")
    @Produces(MediaType.TEXT_PLAIN)
    public String pathwayDiagram(@PathParam("dbId") final long PathwayId, 
                                 @PathParam("format") String format) {
        format = format.toLowerCase();
        return restHelper.getPathwayDiagram(PathwayId, 
                                         format,
                                         null);
    }
    
    /**
     * Get a list of DB_IDs for events contained by a Pathway with specified pathwayId. All events, both
     * Pathways and Reactions, should be in the returned list, recursively.
     * @param pathwayId DB_ID for a Pathway object.
     * @return a list of DB_IDs for Events contained by a Pathway object. The returned DB_IDs are in a
     * simple Text and delimited by ",".
     */
    @GET
    @Path("/getContainedEventIds/{dbId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getContainedEventIds(@PathParam("dbId") Long pathwayId) {
        List<Long> dbIds = restHelper.getContainedEventIds(pathwayId);
        StringBuilder builder = new StringBuilder();
        for (Long dbId : dbIds) {
            builder.append(dbId).append(",");
        }
        builder.delete(builder.length() - 1, builder.length());
        return builder.toString();
    }
    
    /**
     * @param className Class Name of Object you are querying for
     * @param dbID
     * @return A full object of type className
     */
    @GET
    @Path("/queryById/{className}/{dbId}")
    public DatabaseObject queryById(@PathParam("className") final String className, @PathParam("dbId") final String dbID) {
        return restHelper.queryById(className, dbID);
    }
}
