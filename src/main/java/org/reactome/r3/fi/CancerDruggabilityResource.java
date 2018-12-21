/*
 * Created on Dec 14, 2016
 *
 */
package org.reactome.r3.fi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlRootElement;

import org.gk.model.GKInstance;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Node;
import org.gk.render.ProcessNode;
import org.gk.render.RenderablePathway;
import org.junit.Test;
import org.reactome.r3.service.CancerDruggabilityDAO;
import org.reactome.r3.service.DrugCentralDAO;
import org.reactome.r3.service.DrugDAO;
import org.reactome.r3.util.FileUtility;
import org.reactome.r3.util.InteractionUtilities;
import org.reactome.restfulapi.InstanceNotFoundException;
import org.springframework.context.annotation.Scope;

import edu.ohsu.bcb.druggability.dataModel.DatabaseRef;
import edu.ohsu.bcb.druggability.dataModel.Drug;
import edu.ohsu.bcb.druggability.dataModel.ExpEvidence;
import edu.ohsu.bcb.druggability.dataModel.Interaction;
import edu.ohsu.bcb.druggability.dataModel.LitEvidence;
import edu.ohsu.bcb.druggability.dataModel.Source;
import edu.ohsu.bcb.druggability.dataModel.Target;

/**
 * This class serves drug/target interactions for the Reactome pathways and FI network.
 * @author gwu
 *
 */
@Path("/drug")
@Scope("Singleton")
public class CancerDruggabilityResource {
    // Serve the targetome drug/target interactions
    private DrugDAO cancerDruggabilityDAO;
    // Serve the DrugCentral drug/target interactions
    private DrugDAO drugCentralDAO;
    private ReactomeObjectHandler reactomeObjectHandler;
    private PathwayDrugImpactAnalyzer impactAnalyzer;
    
    /**
     * Default constructor.
     */
    public CancerDruggabilityResource() {
    }
    
    public PathwayDrugImpactAnalyzer getImpactAnalyzer() {
        return impactAnalyzer;
    }

    public void setImpactAnalyzer(PathwayDrugImpactAnalyzer impactAnalyzer) {
        this.impactAnalyzer = impactAnalyzer;
    }

    public ReactomeObjectHandler getReactomeObjectHandler() {
        return reactomeObjectHandler;
    }

    public void setReactomeObjectHandler(ReactomeObjectHandler reactomeObjectHandler) {
        this.reactomeObjectHandler = reactomeObjectHandler;
    }

    public DrugDAO getDrugCentralDAO() {
        return drugCentralDAO;
    }

    public void setDrugCentralDAO(DrugCentralDAO drugCentralDAO) {
        this.drugCentralDAO = drugCentralDAO;
    }

    public DrugDAO getCancerDruggabilityDAO() {
        return cancerDruggabilityDAO;
    }

    public void setCancerDruggabilityDAO(CancerDruggabilityDAO cancerDruggabilityDAO) {
        this.cancerDruggabilityDAO = cancerDruggabilityDAO;
    }

    /**
     * Query a list of drug-target interactions.
     * @param geneLines a list of genes delimited by "\n".
     * @return
     */
    @Path("/queryDrugTargetInteractions/{dataSource}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public InteractionsInDiagram queryDrugTargetInteractions(@PathParam("dataSource") String dataSource,
                                                             String geneLines) {
        String[] genes = geneLines.split("\n");
        List<String> geneList = new ArrayList<String>();
        for (String gene : genes)
            geneList.add(gene);
        List<Interaction> interactions = getDrugDAO(dataSource).queryInteractions(geneList);
        InteractionsInDiagram rtn = new InteractionsInDiagram();
        rtn.setInteractions(interactions);
        return rtn;
    }
    
    private DrugDAO getDrugDAO(String dataSource) {
        dataSource = dataSource.toLowerCase();
        if (dataSource.equals("targetome"))
            return cancerDruggabilityDAO;
        else if (dataSource.equals("drugcentral"))
            return drugCentralDAO;
        throw new IllegalArgumentException("Datasource, " + dataSource + ", is not supported!");
    }
    
    /**
     * Query a list of drug-target interactions.
     * @param geneLines a list of genes delimited by "\n".
     * @return
     */
    @Path("/queryInteractionsForDrugs/{dataSource}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public InteractionsInDiagram queryInteractionsForDrug(@PathParam("dataSource") String dataSource,
                                                          String drugNames) {
        String[] drugs = drugNames.split("\n");
        List<Interaction> interactions = getDrugDAO(dataSource).queryInteractionsForDrugs(drugs);
        InteractionsInDiagram rtn = new InteractionsInDiagram();
        rtn.setInteractions(interactions);
        return rtn;
    }
    
    /**
     * List all cancer drugs in the database.
     * @return
     */
    @Path("/listDrugs/{dataSource}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Drug> listDrugs(@PathParam("dataSource") String dataSource) {
        List<Drug> drugs = getDrugDAO(dataSource).listDrugs();
        return drugs;
    }
    
    /**
     * This method is used to query drug/target interactions for a PE that is specified by its DB_ID.
     * @param peDBId
     * @return
     * @throws Exception
     */
    @Path("/queryInteractionsForPEInDiagram/{dataSource}/{pdId}/{peId}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public InteractionsInDiagram queryInteractionsForPEInDiagram(@PathParam("dataSource") String dataSource,
                                                                 @PathParam("pdId") Long pdId,
                                                                 @PathParam("peId") Long peId) throws Exception {
        InteractionsInDiagram rtn = new InteractionsInDiagram();
        List<String> geneList = grepGenesInPE(peId);
        if (geneList.size() == 0)
            return rtn;
        List<Interaction> interactions = getDrugDAO(dataSource).queryInteractions(geneList);
        rtn.setInteractions(interactions);
        return rtn;
    }
    
    /**
     * This method is used to perform an impact analysis for a drug based on a list of pathways.
     * Use POST to avoid encoding drug names in URL.
     * @param drug
     * @return
     * @throws Exception
     */
    @Path("/performImpactAnalysis/{dataSource}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.TEXT_PLAIN})
    public String performImpactAnalysis(@PathParam("dataSource") String dataSource,
                                        String drug) throws Exception {
        String results = impactAnalyzer.performImpactAnalysis(drug, getDrugDAO(dataSource));
        return results;
    }

    private List<String> grepGenesInPE(Long peId) throws Exception {
        String geneText = reactomeObjectHandler.getContainedGenesInPE(peId);
        if (geneText == null || geneText.length() == 0)
            return new ArrayList<String>();
        String[] tokens = geneText.split(",");
        List<String> geneList = new ArrayList<String>();
        for (String token : tokens)
            geneList.add(token);
        return geneList;
    }
    
    /**
     * This method is used to query drug/target interactions for a PathwayDiagram specified by its DB_ID.
     * @param peDBId
     * @return
     * @throws Exception
     */
    @Path("/queryInteractionsForDiagram/{dataSource}/{pdId}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public InteractionsInDiagram queryInteractionsForDiagram(@PathParam("dataSource") String dataSource,
                                                             @PathParam("pdId") Long pdId) throws Exception {
        MySQLAdaptor dba = reactomeObjectHandler.getSrcDBA();
        GKInstance pd = dba.fetchInstance(pdId);
        if (pd == null)
            throw new InstanceNotFoundException(pdId);
        // Grep genes in the diagram
        Set<Long> peIds = grepPEIds(pd);
        return queryInteractionsForPEIDSet(peIds, dataSource);
    }

    private InteractionsInDiagram queryInteractionsForPEIDSet(Set<Long> peIds, String dataSource) throws Exception {
        Map<Long, List<String>> peIdToGenes = grepGenesInPEs(peIds);
        // Get all genes for easy query
        Set<String> allGenes = new HashSet<String>();
        for (List<String> geneList : peIdToGenes.values())
            allGenes.addAll(geneList);
        // Get interactions for all genes
        List<Interaction> interactions = getDrugDAO(dataSource).queryInteractions(allGenes);
        List<ReactomeIdToInteractions> idToInteractions = sortInteractions(interactions, peIdToGenes);
        InteractionsInDiagram rtn = new InteractionsInDiagram();
        rtn.setInteractions(interactions);
        rtn.setDbIdToInteractions(idToInteractions);
        return rtn;
    }
    
    /**
     * Query interactions for a set of DB_IDs for PEs.
     * @param DB_IDs delimited by "\n".
     * @return
     */
    @Path("/queryInteractionsForPEs/{dataSource}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public InteractionsInDiagram queryInteractionsForPEs(@PathParam("dataSource") String dataSource,
            String idText) throws Exception {
        String[] ids = idText.split("\n");
        Set<Long> idSet = new HashSet<Long>();
        for (String id : ids)
            idSet.add(new Long(id));
        return queryInteractionsForPEIDSet(idSet, dataSource);
    }
    
    /**
     * Sort interactions according to PhysicalEntitys' Reactome DB_IDs based on their contained genes.
     * @param interactions
     * @param peIdToGenes
     * @return
     */
    private List<ReactomeIdToInteractions> sortInteractions(List<Interaction> interactions,
                                                            Map<Long, List<String>> peIdToGenes) {
        List<ReactomeIdToInteractions> peIdToInteractions = new ArrayList<CancerDruggabilityResource.ReactomeIdToInteractions>();
        // Sort interactions based on genes first
        Map<String, Set<Interaction>> geneToInteractions = new HashMap<String, Set<Interaction>>();
        for (Interaction interaction : interactions) {
            String targetName = interaction.getIntTarget().getTargetName();
            InteractionUtilities.addElementToSet(geneToInteractions, targetName, interaction);
        }
        for (Long peId : peIdToGenes.keySet()) {
            List<String> genes = peIdToGenes.get(peId);
            List<Interaction> peInteractions = new ArrayList<Interaction>();
            for (String gene : genes) {
                Set<Interaction> geneInteractions = geneToInteractions.get(gene);
                if (geneInteractions != null && geneInteractions.size() > 0)
                    peInteractions.addAll(geneInteractions); // There should be no duplications since they are keyed vs genes
            }
            ReactomeIdToInteractions value = new ReactomeIdToInteractions();
            value.setDbId(peId);
            value.setInteractions(peInteractions);
            peIdToInteractions.add(value);
        }
        return peIdToInteractions;
    }

    private Set<Long> grepPEIds(GKInstance pd) throws Exception {
        Set<Long> peIds = new HashSet<Long>();
        RenderablePathway diagram = new DiagramGKBReader().openDiagram(pd);
        for (Object obj : diagram.getComponents()) {
            if (!(obj instanceof Node))
                continue;
            Node node = (Node) obj;
            if (node.getReactomeId() == null || node instanceof ProcessNode)
                continue;
            // We want to handle PEs only
            peIds.add(node.getReactomeId());
        }
        return peIds;
    }
    
    private Map<Long, List<String>> grepGenesInPEs(Set<Long> peIds) throws Exception {
        Map<Long, List<String>> peIdToGenes = new HashMap<Long, List<String>>();
        for (Long peId : peIds) {
            List<String> genesInPE = grepGenesInPE(peId);
            peIdToGenes.put(peId, genesInPE);
        }
        return peIdToGenes;
    }
    
    /**
     * A method to check a pre-generated file from Rory.
     * @throws Exception
     */
    @Test
    public void testSourceFile() throws Exception {
        String fileName = "WebContent/WEB-INF/DruggabilityV2_12.02.16.txt";
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        String line = fu.readLine();
        int totalLines = 0;
        int humanProteinLines = 0;
        Set<String> humanUniProtIds = new HashSet<String>();
        Set<String> humanGeneNames = new HashSet<String>();
        while ((line = fu.readLine()) != null) {
            totalLines ++;
            String[] tokens = line.split("\t");
            if (!tokens[2].equals("Protein"))
                continue;
//            if (tokens[4].equals("null") && !tokens[3].equals("null")) {
////                System.out.println(line);
//                continue;
//            }
            if (!tokens[4].equals("Homo sapiens") && !tokens[4].equals("null"))
                continue;
            humanProteinLines ++;
//            if (tokens[1].equals("null") || tokens[3].equals("null")) {
//                System.out.println(line);
//                continue;
//            }
            if (tokens[1].contains(" "))
                System.out.println(line);
            humanUniProtIds.add(tokens[3]);
            humanGeneNames.add(tokens[1]);
        }
        fu.close();
        System.out.println("Total lines: " + totalLines);
        System.out.println("Human protein lines: " + humanProteinLines);
        System.out.println("Human UniProt ids: " + humanUniProtIds.size());
        System.out.println("Human gene names: " + humanGeneNames.size());
    }
    
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    static class InteractionsInDiagram {
        // To save the size of output, all objects are loaded first so that others can use ids to refer
        // to them.
        @XmlElement(name="interaction")
        private Set<Interaction> interactions;
        @XmlElement(name="drug")
        private Set<Drug> drugs;
        @XmlElement(name="target")
        private Set<Target> targets;
        @XmlElement(name="expEvidence")
        private Set<ExpEvidence> expEvidences;
        @XmlElement(name="literature")
        private Set<LitEvidence> litEvidences;
        @XmlElement(name="source")
        private Set<Source> sources;
        @XmlElement(name="database")
        private Set<DatabaseRef> databases;
        
        @XmlElement(name="dbIdToInteractions")
        private List<ReactomeIdToInteractions> dbIdToInteractions;
        
        public InteractionsInDiagram() {
        }

        public Set<Interaction> getInteractions() {
            return interactions;
        }

        public void setInteractions(Set<Interaction> interactions) {
            this.interactions = interactions;
            extractObjects();
        }
        
        public void setInteractions(List<Interaction> interactions) {
            if (interactions == null)
                setInteractions(new HashSet<Interaction>());
            else
                setInteractions(new HashSet<Interaction>(interactions));
        }
        
        private void extractObjects() {
            if (interactions == null || interactions.size() == 0)
                return;
            drugs = new HashSet<Drug>();
            targets = new HashSet<Target>();
            expEvidences = new HashSet<ExpEvidence>();
            litEvidences = new HashSet<LitEvidence>();
            sources = new HashSet<Source>();
            databases = new HashSet<DatabaseRef>();
            
            for (Interaction interaction : interactions) {
                drugs.add(interaction.getIntDrug());
                targets.add(interaction.getIntTarget());
                if (interaction.getExpEvidenceSet() != null)
                    expEvidences.addAll(interaction.getExpEvidenceSet());
                if (interaction.getInteractionSourceSet() != null)
                    sources.addAll(interaction.getInteractionSourceSet());
            }
            for (ExpEvidence evid : expEvidences) {
                if (evid.getExpSourceSet() != null)
                    sources.addAll(evid.getExpSourceSet());
            }
            for (Source source : sources) {
                if (source.getSourceLiterature() != null)
                    litEvidences.add(source.getSourceLiterature());
                if (source.getSourceDatabase() != null)
                    databases.add(source.getSourceDatabase());
                if (source.getParentDatabase() != null)
                    databases.add(source.getParentDatabase());
            }
        }

        public List<ReactomeIdToInteractions> getDbIdToInteractions() {
            return dbIdToInteractions;
        }

        public void setDbIdToInteractions(List<ReactomeIdToInteractions> dbIdToInteractions) {
            this.dbIdToInteractions = dbIdToInteractions;
        }

        public Set<Drug> getDrugs() {
            return drugs;
        }

        public void setDrugs(Set<Drug> drugs) {
            this.drugs = drugs;
        }

        public Set<Target> getTargets() {
            return targets;
        }

        public void setTargets(Set<Target> targets) {
            this.targets = targets;
        }

        public Set<ExpEvidence> getExpEvidences() {
            return expEvidences;
        }

        public void setExpEvidences(Set<ExpEvidence> expEvidences) {
            this.expEvidences = expEvidences;
        }

        public Set<LitEvidence> getLitEvidences() {
            return litEvidences;
        }

        public void setLitEvidences(Set<LitEvidence> litEvidences) {
            this.litEvidences = litEvidences;
        }

        public Set<Source> getSources() {
            return sources;
        }

        public void setSources(Set<Source> sources) {
            this.sources = sources;
        }

        public Set<DatabaseRef> getDatabases() {
            return databases;
        }

        public void setDatabases(Set<DatabaseRef> databases) {
            this.databases = databases;
        }
        
    }
    
    /**
     * A simple class to support JAXB.
     * @author gwu
     *
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    static class ReactomeIdToInteractions {
        private Long dbId;
        @XmlIDREF
        @XmlElement(name="interaction")
        private List<Interaction> interactions;
        
        public ReactomeIdToInteractions() {
            
        }

        public Long getDbId() {
            return dbId;
        }

        public void setDbId(Long dbId) {
            this.dbId = dbId;
        }

        public List<Interaction> getInteractions() {
            return interactions;
        }

        public void setInteractions(List<Interaction> interactions) {
            this.interactions = interactions;
        }
        
    }
}
