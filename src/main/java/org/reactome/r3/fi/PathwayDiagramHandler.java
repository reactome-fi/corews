/*
 * Created on Jul 28, 2010
 *
 */
package org.reactome.r3.fi;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gk.elv.ElvDiagramHandler;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.DiagramGKBWriter;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.Project;
import org.gk.render.RenderablePathway;
import org.gk.util.GraphLayoutEngine;
import org.junit.Test;

public class PathwayDiagramHandler {
	private static final Logger logger = Logger.getLogger(PathwayDiagramHandler.class);
    private MySQLAdaptor diagramDBA;
    private String dotPath;
    
    public PathwayDiagramHandler() {
    }
    
    public String getDotPath() {
        return dotPath;
    }

    public void setDotPath(String dotPath) {
        this.dotPath = dotPath;
        GraphLayoutEngine.dot = dotPath;
    }

    public void setDiagramDBA(MySQLAdaptor dba) {
        this.diagramDBA = dba;
        // Keep it working 
        dba.initDumbThreadForConnection();
    }
    
    public MySQLAdaptor getDiagramDBA() {
        return this.diagramDBA;
    }
    
    public String queryDiagramXML(GKInstance sourcePathway) throws Exception {
        RenderablePathway rDiagram = null;
        // Check if there is a diagram in the diagram database
        GKInstance pathway = diagramDBA.fetchInstance(sourcePathway.getDBID());
        if (pathway != null) {
            GKInstance diagram = queryPathwayDiagram(pathway);
            if (diagram != null) {
                rDiagram = new DiagramGKBReader().openDiagram(diagram);
            }
        }
        // Generate a diagram from scratch
        if (rDiagram == null) {
            // Generate a diagram
            rDiagram = new RenderablePathway();
            rDiagram.setDisplayName(sourcePathway.getDisplayName());
            ElvDiagramHandler elvHanlder = new ElvDiagramHandler();
            elvHanlder.createNewDiagram(sourcePathway, rDiagram);
        }
        // Convert into string
        DiagramGKBWriter writer = new DiagramGKBWriter();
        writer.setNeedDisplayName(true);
        Project project = new Project();
        project.setProcess(rDiagram);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writer.save(project, os);
        String text = os.toString("UTF-8");
        return text;
    }
    
    @Test
    public void testQueryPathwayDiagram() throws Exception {
    	// It seems there is an infinity loop related to this pathway
    	Long dbId = 5203986L;
    	diagramDBA = new MySQLAdaptor("localhost",
    							      "reactome_47_plus_i", 
    							      "root", 
    							      "macmysql01");
    	GKInstance pathway = diagramDBA.fetchInstance(dbId);
    	System.out.println("Pathway: " + pathway);
    	GKInstance diagram = queryPathwayDiagram(pathway);
    	System.out.println("Diagram: " + diagram);
    }
    
    /**
     * Search a diagram for the passed pathway. If there is no diagram for the passed pathway,
     * its pathway container will be checked recursively.
     * @return
     * @throws Exception
     */
    private GKInstance queryPathwayDiagram(GKInstance pathway) throws Exception {
    	logger.info("queryPathwayDiagram: " + pathway + ": starting");
        Set<GKInstance> current = new HashSet<GKInstance>();
        current.add(pathway);
        Set<GKInstance> next = new HashSet<GKInstance>();
        // To avoid an infinity loop for pathways imported from 
        // no Reactome databases (e.g. Pathway Calcium signaling in the CD4+ TCR pathway
        // from NCI-PID: some pathways are referred to each other by hasEvent attributes.
        Set<Long> checkedIds = new HashSet<Long>();
        while (current.size() > 0) {
            for (GKInstance instance : current) {
            	if (checkedIds.contains(instance.getDBID())) {
//            		System.out.println("Checked: " + instance);
            		continue;
            	}
            	checkedIds.add(instance.getDBID());
                Collection<?> c = diagramDBA.fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram,
                                                                      ReactomeJavaConstants.representedPathway,
                                                                      "=",
                                                                      instance);
                if (c != null && c.size() > 0) {
                    logger.info("queryPathwayDiagram: " + pathway + ": ending");
                	return (GKInstance) c.iterator().next();
                }
                // Check its parents
                Collection<GKInstance> parents = instance.getReferers(ReactomeJavaConstants.hasEvent);
                if (parents != null)
                    next.addAll(parents);
            }
            current.clear();
            current.addAll(next);
            next.clear();
        }
        logger.info("queryPathwayDiagram: " + pathway + ": ending");
        return null;
    }
    
    /**
     * Check if the first PE DB_IDs are matched to the second gene names.
     * @param dbIds
     * @param geneNames
     * @return a set of DB_IDs from the first String array.
     */
    public Set<String> checkMatchEntityIds(String[] dbIds, 
                                           String[] geneNames,
                                           MySQLAdaptor dba) throws Exception {
        List<Long> dbIdList = new ArrayList<Long>();
        for (String dbId : dbIds)
            dbIdList.add(new Long(dbId));
        List<Long> matchedIds = InstanceUtilities.checkMatchEntityIds(dbIdList,
                                                                      Arrays.asList(geneNames), 
                                                                      dba);
        Set<String> rtn = new HashSet<String>();
        if (matchedIds != null) {
            for (Long id : matchedIds)
                rtn.add(id.toString());
        }
        // To keep the memory usage low
        dba.refresh();
        return rtn;
    }
    
}
