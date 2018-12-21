/*
 * Created on Feb 25, 2014
 *
 */
package org.reactome.r3.fi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.gk.model.GKInstance;
import org.gk.model.PersistenceAdaptor;
import org.gk.persistence.MySQLAdaptor;
import org.junit.Test;
import org.reactome.factorgraph.Factor;
import org.reactome.factorgraph.FactorGraph;
import org.reactome.factorgraph.Variable;
import org.reactome.pathway.factorgraph.PathwayPGMConfiguration;
import org.reactome.pathway.factorgraph.PathwayToFactorGraphConverter;
import org.reactome.r3.util.FileUtility;

/**
 * This class is used as a facade to this package. All clients that need to use classes in this package
 * should go through this class to make sure the API is stable.
 * @author gwu
 *
 */
public class FactorGraphFacade {
    // A flag to indicate if the configration has been done
    private boolean isConfigured;

    /**
     * Default constructor.
     */
    public FactorGraphFacade() {
    }
    
    /**
     * Convert the passed pathway object into a factor graph.
     * @param pathway the passed pathway should have its own pathway diagram drawn. The
     * pathway diagram should be fully expanded. In other words, the diagram should contain
     * PhysicalEntities connected by ReactionlikeEvents. 
     * @throws Exception
     */
    public FactorGraph convertToFactorGraph(GKInstance pathway,
                                            List<String> namesForEscape) throws Exception {
        // Persistence adaptor to query _displayName
        PersistenceAdaptor dba = pathway.getDbAdaptor();
        if (!isConfigured) {
            PathwayPGMConfiguration config = PathwayPGMConfiguration.getConfig();
            InputStream is = getClass().getResourceAsStream("PGM_Pathway_Config.xml");
            config.config(is);
            isConfigured = true;
        }
        PathwayToFactorGraphConverter converter = new PathwayToFactorGraphConverter();
        converter.setNamesForEscape(namesForEscape);
        FactorGraph fg = converter.convertPathway(pathway);
        // Make sure all factors have names for displaying
        validateFactorNames(fg);
        return fg;
    }
    
    /**
     * Make sure all factors have names.
     * @param fg
     */
    private void validateFactorNames(FactorGraph fg) {
        if (fg == null)
            return;
        StringBuilder builder = new StringBuilder();
        for (Factor factor : fg.getFactors()) {
            if (factor.getName() != null)
                continue;
            List<Variable> variables = factor.getVariables();
            builder.setLength(0);
            for (Variable var : variables)
                builder.append(var.getName()).append(", ");
            builder.delete(builder.length() - 2, builder.length());
            factor.setName(builder.toString());
        }
    }
    
    @Test
    public void testConvert() throws Exception {
        FileUtility.initializeLogging();
        MySQLAdaptor dba = new MySQLAdaptor("localhost", 
                                            "reactome_55_plus_i",
                                            "root", 
                                            "macmysql01");
        Long dbId = 2032785L;
        dbId = 381183L;
        GKInstance pathway = dba.fetchInstance(dbId);
        String[] escapeNames = new String[] {
                "ATP",
                "ADP",
                "Pi",
                "H2O",
                "GTP",
                "GDP",
                "CO2",
                "H+"
        };
        List<String> escapeList = Arrays.asList(escapeNames);
        FactorGraph fg = convertToFactorGraph(pathway, escapeList);
        System.out.println("Factor Graph for: " + fg.getName());
        System.out.println("Total factors: " + fg.getFactors().size());
        System.out.println("Total variables: " + fg.getVariables().size());
        System.out.println("\nFactors:");
        for (Factor factor : fg.getFactors()) {
            System.out.println(factor.getId() + ": " + factor.getName());
        }
        System.out.println("\nVariables:");
        for (Variable var : fg.getVariables()) {
            System.out.println(var.getId() + ": " + var.getName());
            for (Factor factor : var.getFactors())
                System.out.println(factor.getId() + ": " + factor.getName());
        }
        fg.exportFG(System.out);
        
        File file = new File("tmp.xml");
        FileOutputStream fos = new FileOutputStream(file);
        fg.exportFG(fos);
        JAXBContext jc = JAXBContext.newInstance(FactorGraph.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        fg = (FactorGraph) unmarshaller.unmarshal(new FileInputStream(file));
        file.delete();
        for (Variable var : fg.getVariables()) {
            System.out.println(var.getId() + ": " + var.getName());
            for (Factor factor : var.getFactors())
                System.out.println(factor.getId() + ": " + factor.getName());
        }
    }
}
