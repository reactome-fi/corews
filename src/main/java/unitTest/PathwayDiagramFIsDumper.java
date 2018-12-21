/*
 * Created on Aug 18, 2014
 *
 */
package unitTest;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.reactome.funcInt.Interaction;
import org.reactome.r3.fi.InteractionAnnotator;
import org.reactome.r3.fi.PathwayToFIsConverter;
import org.reactome.r3.util.FileUtility;

/**
 * This class is used to dump FIs converted from pathways. Only pathways having own 
 * diagrams are dumped.
 * @author gwu
 *
 */
public class PathwayDiagramFIsDumper {
    
    /**
     * Default construcotr.
     */
    public PathwayDiagramFIsDumper() {
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void dump() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost", 
                                            "reactome_47_plus_i",
                                            "root",
                                            "macmysql01");
        Collection<GKInstance> pds = dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
        dba.loadInstanceAttributeValues(pds, new String[]{ReactomeJavaConstants.representedPathway});
        // Get a list of pathways to be exported
        Set<GKInstance> pathways = new HashSet<GKInstance>();
        for (GKInstance pd : pds) {
            List<GKInstance> list = pd.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
            if (list == null || list.size() == 0)
                continue;
            for (GKInstance pathway : list) {
                GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
                if (species.getDisplayName().equals("Homo sapiens"))
                    pathways.add(pathway);
            }
        }
        System.out.println("Total pathways to be exported: " + pathways.size());
        PathwayToFIsConverter converter = new PathwayToFIsConverter();
        converter.setMySQLAdaptor(dba);
        InteractionAnnotator annotator = new InteractionAnnotator();
        annotator.setSourceDBA(dba);
        converter.setAnnotator(annotator);
        FileUtility fu = new FileUtility();
        String output = "/Users/gwu/Documents/Hossein/FIsInPathways_Release49_081814.txt";
        fu.setOutput(output);
        fu.printLine("Pathway_ID\tPathway_Name\tProtein1\tProtein2\tAnnotation\tDirection");
        for (GKInstance pathway : pathways) {
//            if (!pathway.getDBID().equals(1257604L))
//                continue;
            System.out.println("Working on " + pathway + "...");
            List<Interaction> interactions = converter.convertPathwayToFIs(pathway.getDBID());
            for (Interaction interaction : interactions)
                fu.printLine(pathway.getDBID() + "\t" + 
                             pathway.getDisplayName() + "\t" + 
                             interaction.getFirstProtein().getShortName() + "\t" + 
                             interaction.getSecondProtein().getShortName() + "\t" + 
                             interaction.getAnnotation().getAnnotation() + "\t" + 
                             interaction.getAnnotation().getDirection());
//            break;
        }
        fu.close();
    }
    
}
