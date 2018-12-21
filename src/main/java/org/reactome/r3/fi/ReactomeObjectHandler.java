/*
 * Created on Nov 25, 2013
 *
 */
package org.reactome.r3.fi;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlRootElement;

import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Node;
import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.gk.schema.SchemaAttribute;
import org.gk.util.StringUtils;
import org.junit.Test;
import org.reactome.data.ReactomeAnalyzerTopicHelper;
import org.reactome.funcInt.FIAnnotation;
import org.reactome.funcInt.Interaction;
import org.reactome.r3.model.ReactomeAttribute;
import org.reactome.r3.model.ReactomeInstance;
import org.reactome.r3.service.FIServiceUtilities;
import org.reactome.r3.util.InteractionUtilities;

/**
 * This class is used to process Reactome related jobs.
 * @author gwu
 *
 */
public class ReactomeObjectHandler {
    private MySQLAdaptor srcDBA;
    private String pathwayListFileName;
    
    /**
     * Default constructor.
     */
    public ReactomeObjectHandler() {
    }

    public String getPathwayListFileName() {
        return pathwayListFileName;
    }

    public void setPathwayListFileName(String pathwayListFileName) {
        this.pathwayListFileName = pathwayListFileName;
    }
    
    /**
     * Load a pre-populated pathways, which usually should have ELV diagrams available.
     * @return
     * @throws Exception
     */
    public List<GKInstance> loadPathwayList() throws Exception {
        if (pathwayListFileName == null)
            return new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(pathwayListFileName))) {
            List<Long> dbIds = stream.map(line -> new Long(line.split("\t")[0])).collect(Collectors.toList());
            List<GKInstance> pathways = new ArrayList<>();
            for (Long dbId : dbIds) {
                GKInstance pathway = srcDBA.fetchInstance(dbId);
                if (pathway != null)
                    pathways.add(pathway);
            }
            return pathways;
        }
    }

    public MySQLAdaptor getSrcDBA() {
        return srcDBA;
    }

    public void setSrcDBA(MySQLAdaptor srcDBA) {
        this.srcDBA = srcDBA;
    }
    
    /**
     * Query a Reactome instance based on its DB_ID. This is a very simple implementation,
     * and should not be mixed with one in the ReactomeRESTful API, which is much more
     * sophisticated.
     * @param dbId
     * @return
     * @throws Exception
     */
    public ReactomeInstance queryInstance(Long dbId) throws Exception {
        ReactomeInstance rtn = new ReactomeInstance();
        GKInstance instance = srcDBA.fetchInstance(dbId);
        if (instance == null)
            return rtn;
        // A quick loading method
        srcDBA.fastLoadInstanceAttributeValues(instance);
        // Copy values 
        rtn.setDbId(instance.getDBID());
        rtn.setDisplayName(instance.getDisplayName());
        rtn.setSchemaClass(instance.getSchemClass().getName());
        String[] escapedAtts = new String[] {
                ReactomeJavaConstants.DB_ID,
                ReactomeJavaConstants.modified,
                ReactomeJavaConstants.created,
                ReactomeJavaConstants.edited,
                ReactomeJavaConstants.authored,
                ReactomeJavaConstants.reviewed,
                ReactomeJavaConstants.figure
        };
        List<String> escapedAttList = Arrays.asList(escapedAtts);
        for (Object obj : instance.getSchemaAttributes()) {
            SchemaAttribute att = (SchemaAttribute) obj;
            if (att.getName().startsWith("_") ||
                escapedAttList.contains(att.getName())) // Avoid any database control attribute
                continue;
            List<?> values = instance.getAttributeValuesList(att);
            if (values == null || values.size() == 0)
                continue;
            ReactomeAttribute rAtt = createReactomeAttribute(att.getName(), values);
            rtn.addAttribute(rAtt);
        }
        // For Reaction, we want to know its regulations too
        if (instance.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
            @SuppressWarnings("unchecked")
            Collection<GKInstance> regulations = instance.getReferers(ReactomeJavaConstants.regulatedEntity);
            if (regulations != null && regulations.size() > 0) {
                ReactomeAttribute rAtt = createReactomeAttribute("regulatedBy", regulations);
                rtn.addAttribute(rAtt);
            }
        }
        rtn.sortAttributes();
        return rtn;
    }

    private ReactomeAttribute createReactomeAttribute(String name,
                                                      Collection<?> values) {
        ReactomeAttribute rAtt = new ReactomeAttribute();
        rAtt.setName(name);
        for (Object value : values) {
            if (value instanceof GKInstance) {
                GKInstance valueInst = (GKInstance) value;
                ReactomeInstance rValueInst = new ReactomeInstance();
                rValueInst.setDbId(valueInst.getDBID());
                rValueInst.setDisplayName(valueInst.getDisplayName());
                rAtt.addValue(rValueInst);
            }
            else
                rAtt.addValue(value);
        }
        return rAtt;
    }
    
    /**
     * Get the mapping from genes to EWAS ids. 
     * @param ids EWAS DB_IDs delimited by ",".
     * @return
     * @throws Exception
     */
    public List<GeneToPEIds> getGeneToEWASIds(String ids) throws Exception {
        String[] tokens = ids.split(",");
        Map<String, Set<GKInstance>> geneToInstances = new HashMap<String, Set<GKInstance>>();
        for (String token : tokens) {
            GKInstance inst = srcDBA.fetchInstance(new Long(token));
            if (inst == null || !inst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence))
                continue;
            GKInstance refEntity = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            if (refEntity == null || !refEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceSequence))
                continue;
            String geneName = (String) refEntity.getAttributeValue(ReactomeJavaConstants.geneName);
            if (geneName == null)
                continue;
            InteractionUtilities.addElementToSet(geneToInstances, geneName, inst);
        }
        return convertMapToGeneToPEIdsList(geneToInstances);
    }
    
    /**
     * Get a map from gene names to instance DB_Ids for a PathwayDiagram specified by its DB_ID.
     * @param pathwayDiagramId
     * @return
     * @throws Exception
     */
    public List<GeneToPEIds> getGeneToIdsInPathwayDiagram(Long pathwayDiagramId) throws Exception {
        GKInstance pathwayDiagram = srcDBA.fetchInstance(pathwayDiagramId);
        if (pathwayDiagram == null || !pathwayDiagram.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram))
            return new ArrayList<ReactomeObjectHandler.GeneToPEIds>();
        DiagramGKBReader diagramReader = new DiagramGKBReader();
        RenderablePathway diagram = diagramReader.openDiagram(pathwayDiagram);
        ReactomeAnalyzerTopicHelper helper = new ReactomeAnalyzerTopicHelper();
        Map<String, Set<GKInstance>> geneToInstances = new HashMap<String, Set<GKInstance>>();
        for (Object o : diagram.getComponents()) {
            Renderable r = (Renderable) o;
            if (r.getReactomeId() == null || !(r instanceof Node))
                continue;
            GKInstance inst = (GKInstance) srcDBA.fetchInstance(r.getReactomeId());
            // Two types of nodes: PEs and Sub-Pathway
            if (inst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity)) {
                Set<GKInstance> refEntities = helper.grepRefPepSeqs(inst);
                for (GKInstance refEntity : refEntities) {
                    if (refEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceSequence)) {
                        String geneName = (String) refEntity.getAttributeValue(ReactomeJavaConstants.geneName);
                        if (geneName != null)
                            InteractionUtilities.addElementToSet(geneToInstances, geneName, inst);
                    }
                }
            }
            else if (inst.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                // Get a list of complexes
                Set<GKInstance> entityComps = InstanceUtilities.grepPathwayParticipants(inst);               
                for (GKInstance entity : entityComps) {
                    Set<GKInstance> refEntities = helper.grepRefPepSeqs(entity);
                    for (GKInstance refEntity : refEntities) {
                        if (refEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceSequence)) {
                            String geneName = (String) refEntity.getAttributeValue(ReactomeJavaConstants.geneName);
                            if (geneName != null)
                                InteractionUtilities.addElementToSet(geneToInstances, geneName, inst);
                        }
                    }
                }
            }
        }
        return convertMapToGeneToPEIdsList(geneToInstances);
    }

    private List<GeneToPEIds> convertMapToGeneToPEIdsList(Map<String, Set<GKInstance>> geneToInstances) {
        // Convert geneToPEs map to a list for XML/JSON generation
        List<GeneToPEIds> list = new ArrayList<ReactomeObjectHandler.GeneToPEIds>();
        for (String gene : geneToInstances.keySet()) {
            Set<GKInstance> pes = geneToInstances.get(gene);
            List<Long> peIds = new ArrayList<Long>(pes.size());
            for (GKInstance pe : pes)
                peIds.add(pe.getDBID());
            GeneToPEIds value = new GeneToPEIds();
            value.setGene(gene);
            value.setPeDbIds(peIds);
            list.add(value);
        }
        return list;
    }
    
    /**
     * used a simple approach to list genes contained by a PE: no "OR" is used. 
     * Just use "," since it is very difficult to list genes in a semantic way. 
     * For example a DefinedSet contains two complexes, each of which contains 
     * another two complexes, each of which is an EWAS. Some kind of tree structure 
     * must be used to indicate such a structure. Or need multiple layers of 
     * parentheses.
     * @param peId
     * @return
     * @throws Exception
     */
    public String getContainedGenesInPE(Long peId) throws Exception {
        GKInstance inst = srcDBA.fetchInstance(peId);
        if (inst == null)
            return "";
        StringBuilder builder = new StringBuilder();
        // Assume this is just an EWAS 
        getContainedGenesInPE(inst, builder);
        // In case no gene can be found
        if (builder.length() == 0)
            return "";
        builder.delete(builder.length() - 1, builder.length());
        return validateText(builder.toString());
    }
    
    /**
     * Make sure genes are not duplicated in a complex list, e.g., NGF,NGF,NTRK1,...
     * should not be listed multiple times.
     * @param text
     * @return
     */
    private String validateText(String text) {
        String[] tokens = text.split(",");
        Set<String> set = new HashSet<String>();
        for (String token : tokens)
            set.add(token);
        List<String> list = new ArrayList<String>(set);
        Collections.sort(list);
        return StringUtils.join(",", list);
    }
    
    private void getContainedGenesInPE(GKInstance instance, 
                                       StringBuilder builder) throws Exception {
        Set<GKInstance> pes = InstanceUtilities.getContainedInstances(instance, 
                                                                      ReactomeJavaConstants.hasComponent,
                                                                      ReactomeJavaConstants.hasMember,
                                                                      ReactomeJavaConstants.hasCandidate,
                                                                      ReactomeJavaConstants.repeatedUnit);
        pes.add(instance);
        for (GKInstance pe : pes) {
            if (pe.getSchemClass().isValidAttribute(ReactomeJavaConstants.referenceEntity)) {
                GKInstance refEntity = (GKInstance) pe.getAttributeValue(ReactomeJavaConstants.referenceEntity);
                if (refEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceSequence)) {
                    String geneName = (String) refEntity.getAttributeValue(ReactomeJavaConstants.geneName);
                    if (geneName != null) {
                        builder.append(geneName).append(",");
                    }
                }
            }
        }
    }
    
    @Test
    public void testGetContainedGenesInPE() throws Exception {
        srcDBA = new MySQLAdaptor("localhost", "reactome_47_plus_i", "root", "macmysql01");
        Long dbId = 205013L; // A complex
        String genes = getContainedGenesInPE(dbId);
        System.out.println("Genes in " + dbId + ": " + genes);
        // Another empty one
        dbId = 2465873L;
        genes = getContainedGenesInPE(dbId);
        System.out.println("Genes in " + dbId + ": " + genes);
    }
    
    @Test
    public void testGetGeneToPEIdsInPathway() throws Exception {
        srcDBA = new MySQLAdaptor("localhost", "reactome_47_plus_i", "root", "macmysql01");
        Long dbId = 528078L;
        List<GeneToPEIds> list = getGeneToIdsInPathwayDiagram(dbId);
        System.out.println("Total size: " + list.size());
    }
    
    /**
     * A helper method to convert a list of FIs as a simple GeneInDiagarmToGeneToPEIds object list
     * based on a PathwayDiagram.
     * @param diagramId
     * @param genes
     * @param interactions
     * @return
     * @throws Exception
     */
    public List<GeneInDiagramToGeneToPEIds> convertFIsForPEinDiagram(Long diagramId,
                                                                     String genes,
                                                                     List<Interaction> interactions,
                                                                     InteractionAnnotator annotator) throws Exception {
        // Used to map FIs that cannot be extracted but existing in other place
        List<GeneToPEIds> geneToPEIds = getGeneToIdsInPathwayDiagram(diagramId);
        // Use a map for quick check
        Map<String, GeneToPEIds> geneToMap = new HashMap<String, ReactomeObjectHandler.GeneToPEIds>();
        for (GeneToPEIds map : geneToPEIds)
            geneToMap.put(map.getGene(), map);
        List<GeneInDiagramToGeneToPEIds> rtn = new ArrayList<GeneInDiagramToGeneToPEIds>();
        Set<String> geneSet = new HashSet<String>(Arrays.asList(FIServiceUtilities.splitQuery(genes)));
        for (Interaction fi : interactions) {
            String gene1 = fi.getFirstProtein().getShortName();
            String gene2 = fi.getSecondProtein().getShortName();
            // If both genes are contained by the requested PE, don't show them
            // since they cannot be displayed
            if (geneSet.contains(gene1) && geneSet.contains(gene2))
                continue;
            // Since the passed FI is not fully loaded, this simple version of annotate has
            // to be used
            GeneInDiagramToGeneToPEIds entry = new GeneInDiagramToGeneToPEIds();
            if (geneSet.contains(gene2)) {
                // Do a switch
                String tmp = gene1;
                gene1 = gene2;
                gene2 = tmp;
            }
            entry.setGene(gene1);
            GeneToPEIds partnerGene = geneToMap.get(gene2);
            if (partnerGene == null) {
                partnerGene = new GeneToPEIds();
                partnerGene.setGene(gene2);
            }
            entry.setPartnerGene(partnerGene);
            FIAnnotation annotation = annotator.annotate(gene1, gene2);
            if (annotation != null)
                entry.setDirection(annotation.getDirection());
            rtn.add(entry);
        }
        return rtn;
    }
    
    /**
     * This simple class is used to model a simple interaction between a Gene in a
     * pathway diagram and its FI partners.
     */
    @XmlRootElement
    static class GeneInDiagramToGeneToPEIds {
        private String gene; // Gene in a pathway diagram
        private String direction; // FI direction
        private GeneToPEIds partnerGene; // Its FI partner
        
        public GeneInDiagramToGeneToPEIds() {
            
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getGene() {
            return gene;
        }

        public void setGene(String gene) {
            this.gene = gene;
        }

        public GeneToPEIds getPartnerGene() {
            return partnerGene;
        }

        public void setPartnerGene(GeneToPEIds partnerGene) {
            this.partnerGene = partnerGene;
        }
        
    }
    
    /**
     * A simple model class that is used to handle mapping from genes to PEs.
     * @author gwu
     *
     */
    @XmlRootElement
    static class GeneToPEIds {
        private String gene;
        private List<Long> peDbIds;
        
        public GeneToPEIds() {
        }

        public String getGene() {
            return gene;
        }

        public void setGene(String gene) {
            this.gene = gene;
        }

        public List<Long> getPeDbIds() {
            return peDbIds;
        }

        public void setPeDbIds(List<Long> peDbIds) {
            this.peDbIds = peDbIds;
        }
        
    }
    
}
