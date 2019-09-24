/*
 * Created on Oct 1, 2013
 *
 */
package org.reactome.r3.fi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.InstanceNotFoundException;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.Node;
import org.gk.render.ProcessNode;
import org.gk.render.Renderable;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderablePathway;
import org.gk.schema.SchemaClass;
import org.junit.Test;
import org.reactome.data.ReactomeAnalyzer;
import org.reactome.data.ReactomeAnalyzerTopicHelper;
import org.reactome.funcInt.FIAnnotation;
import org.reactome.funcInt.Interaction;
import org.reactome.funcInt.Protein;
import org.reactome.funcInt.ReactomeSource;
import org.reactome.funcInt.ReactomeSourceType;

/**
 * This class is used to convert a Reactome pathway into a list of Interaction objects. This class
 * actually is a simplified version of ReactionFuncInteractionExtractor, which is used in the FI Network
 * build project.
 * @author gwu
 *
 */
public class PathwayToFIsConverter {
    private MySQLAdaptor dba;
    // The following three maps should not be cached in a multiple
    // threading environment
//    // A String based FI to a Interaction object.
//    private Map<String, Interaction> pairToInteractionMap;
//    // Reactome DB_IDs to ReactomeSource objects
//    private Map<Long, ReactomeSource> idToSourceMap;
//    // A map from a ReferenceGeneProduct to a simple Protein
//    private Map<GKInstance, Protein> instToProtein;
    // Used to annotate converted interactions
    private InteractionAnnotator annotator;
    
    /**
     * Default constructor.
     */
    public PathwayToFIsConverter() {
    }
    
    public void setMySQLAdaptor(MySQLAdaptor dba) {
        this.dba = dba;
    }
    
    public MySQLAdaptor getMySQLAdaptor() {
        return this.dba;
    }
    
    public void setAnnotator(InteractionAnnotator annotator) {
        this.annotator = annotator;
    }
    
    /**
     * Convert a pathway, which is specified by its DB_ID, to a list of FIs. The passed pathwayId
     * may be a DB_ID for a PathwayDiagram.
     * @param instId a DB_ID of a Pathway or PathwayDiagram.
     * @return
     * @throws Exception
     */
    public List<Interaction> convertPathwayToFIs(Long instId) throws Exception {
        GKInstance inst = dba.fetchInstance(instId);
        SchemaClass cls = inst.getSchemClass();
        if (inst == null || !(cls.isa(ReactomeJavaConstants.Pathway) || cls.isa(ReactomeJavaConstants.PathwayDiagram)))
            throw new InstanceNotFoundException(ReactomeJavaConstants.Pathway,
                                                instId);
        return extractFIsFromInstance(inst);
    }

    /**
     * The actual method to do converting.
     * @param instance
     * @return
     * @throws Exception
     */
    private List<Interaction> extractFIsFromInstance(GKInstance instance) throws Exception {
        // These maps should not be declared as member properties to avoid
        // overwriting problem in a multiple threading environment.
        Map<String, Interaction> pairToInteractionMap = new HashMap<String, Interaction>();
        Map<Long, ReactomeSource> idToSourceMap = new HashMap<Long, ReactomeSource>();
        Map<GKInstance, Protein> instToProtein = new HashMap<GKInstance, Protein>();
        // To help objects to extract FIs.
        ReactomeAnalyzer reactomeAnalyzer = new ReactomeAnalyzer();
        reactomeAnalyzer.setMySQLAdaptor(this.dba);
        ReactomeAnalyzerTopicHelper topicHelper = new ReactomeAnalyzerTopicHelper();
        
        if (instance.getSchemClass().isa(ReactomeJavaConstants.Event)) {
            // The actual source mapping is based on PathwayDiagram so that the mapping can be used later on
            @SuppressWarnings("unchecked")
            Collection<GKInstance> pathwayDiagrams = dba.fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram,
                                                                                  ReactomeJavaConstants.representedPathway, 
                                                                                  "=",
                                                                                  instance);
            if (pathwayDiagrams == null || pathwayDiagrams.size() == 0) {
                extractFIsFromPathway(instance,
                                      reactomeAnalyzer,
                                      topicHelper,
                                      false,
                                      pairToInteractionMap,
                                      instToProtein,
                                      idToSourceMap);
            }
            else {
                GKInstance pathwayDiagram = pathwayDiagrams.iterator().next();
                extractFIsFromPathwayDiagram(pathwayDiagram, 
                                             reactomeAnalyzer,
                                             topicHelper, 
                                             pairToInteractionMap,
                                             idToSourceMap,
                                             instToProtein);
            }
        }
        else if (instance.getSchemClass().isa(ReactomeJavaConstants.PathwayDiagram)) {
            extractFIsFromPathwayDiagram(instance, 
                                         reactomeAnalyzer,
                                         topicHelper, 
                                         pairToInteractionMap,
                                         idToSourceMap,
                                         instToProtein);
        }
        List<Interaction> interactions = new ArrayList<Interaction>(pairToInteractionMap.values());
        annotate(interactions);
        return interactions;
    }

    private void extractFIsFromPathwayDiagram(GKInstance pathwayDiagram,
                                              ReactomeAnalyzer reactomeAnalyzer,
                                              ReactomeAnalyzerTopicHelper topicHelper,
                                              Map<String, Interaction> pairToInteractionMap,
                                              Map<Long, ReactomeSource> idToSourceMap,
                                              Map<GKInstance, Protein> instToProtein) throws Exception {
        RenderablePathway diagram = new DiagramGKBReader().openDiagram(pathwayDiagram);
        Set<GKInstance> interactors = new HashSet<GKInstance>();
        for (Object obj : diagram.getComponents()) {
            Renderable r = (Renderable) obj;
            if (r.getReactomeId() == null)
                continue;
            // We want to extract FIs from both contained pathways and complexes
            if ((r instanceof Node) && 
               !(r instanceof ProcessNode || r instanceof RenderableComplex))
                continue;
            GKInstance inst = dba.fetchInstance(r.getReactomeId());
            if (inst.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                interactors.clear();
                reactomeAnalyzer.extractInteractorsFromReaction(inst, interactors);
                generateInteractions(interactors, 
                                     inst, 
                                     topicHelper,
                                     pairToInteractionMap,
                                     instToProtein,
                                     idToSourceMap);
            }
            else if (inst.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                interactors.clear();
                reactomeAnalyzer.grepComplexComponents(inst, interactors);
                generateInteractions(interactors, 
                                     inst, 
                                     topicHelper,
                                     pairToInteractionMap,
                                     instToProtein,
                                     idToSourceMap);
            }
            // As of September 23, 2019, this converting is turned off to control the size of the final
            // converted FI network and make the annotation more reliable.
//            else if (inst.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
//                extractFIsFromPathway(inst,
//                                      reactomeAnalyzer, 
//                                      topicHelper, 
//                                      true, // true should be used in order to map converted FIs in the Cytoscape back to the displayed pathway diagrams.
//                                      pairToInteractionMap,
//                                      instToProtein,
//                                      idToSourceMap);
//            }
        }
    }

    private void extractFIsFromPathway(GKInstance pathway,
                                       ReactomeAnalyzer reactomeAnalyzer,
                                       ReactomeAnalyzerTopicHelper topicHelper,
                                       boolean usePathwayAsSource,
                                       Map<String, Interaction> pairToInteractionMap,
                                       Map<GKInstance, Protein> instToProtein,
                                       Map<Long, ReactomeSource> idToSourceMap) throws Exception {
        // Get a list of reactions for extracting FIs
        Set<GKInstance> eventComps = InstanceUtilities.grepPathwayEventComponents(pathway);
        // Get a list of complexes
        Set<GKInstance> entityComps = InstanceUtilities.grepPathwayParticipants(pathway);
        // Used as a temp holder of interactors.
        Set<GKInstance> interactors = new HashSet<GKInstance>();
        for (GKInstance event : eventComps) {
            if (event.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                interactors.clear();
                reactomeAnalyzer.extractInteractorsFromReaction(event, interactors);
                generateInteractions(interactors, 
                                     usePathwayAsSource ? pathway : event, 
                                     topicHelper,
                                     pairToInteractionMap,
                                     instToProtein,
                                     idToSourceMap);
            }
        }
        for (GKInstance entity : entityComps) {
            if (entity.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                interactors.clear();
                reactomeAnalyzer.grepComplexComponents(entity, interactors);
                generateInteractions(interactors,
                                     usePathwayAsSource ? pathway : entity,
                                     topicHelper,
                                     pairToInteractionMap,
                                     instToProtein,
                                     idToSourceMap);
            }
        }
    }
    
    /**
     * Annotate the converted list of interactions.
     * @param interactions
     * @throws Exception
     */
    //TODO: Check why this cannot work in a local setting?
    private void annotate(List<Interaction> interactions) throws Exception {
        if (annotator == null)
            return;
        for (Interaction interaction : interactions) {
            FIAnnotation annotation = annotator.annotate(interaction);
            interaction.setAnnotation(annotation);
        }
    }
    
    private void generateInteractions(Set<GKInstance> interactors,
                                      GKInstance source,
                                      ReactomeAnalyzerTopicHelper topicHelper,
                                      Map<String, Interaction> pairToInteractionMap,
                                      Map<GKInstance, Protein> instToProtein,
                                      Map<Long, ReactomeSource> idToSourceMap) throws Exception {
        if (interactors.size() < 2)
            return;
        List<GKInstance> interactorList = new ArrayList<GKInstance>(interactors);
        for (int i = 0; i < interactorList.size() - 1; i++) {
            GKInstance interactor1 = interactorList.get(i);
            Set<GKInstance> refGeneProducts1 = topicHelper.grepRefPepSeqs(interactor1);
            if (refGeneProducts1.size() == 0)
                continue;
            for (int j = i + 1; j < interactorList.size(); j++) {
                GKInstance interactor2 = interactorList.get(j);
                Set<GKInstance> refGeneProducts2 = topicHelper.grepRefPepSeqs(interactor2);
                if (refGeneProducts2.size() == 0)
                    continue;
                generateInteractions(refGeneProducts1, 
                                     refGeneProducts2,
                                     source,
                                     pairToInteractionMap,
                                     instToProtein,
                                     idToSourceMap);
            }
        }
    }
    
    private void generateInteractions(Set<GKInstance> refGeneProducts1,
                                      Set<GKInstance> refGeneProducts2,
                                      GKInstance source,
                                      Map<String, Interaction> pairToInteractionMap,
                                      Map<GKInstance, Protein> instToProtein,
                                      Map<Long, ReactomeSource> idToSourceMap) throws Exception {
        for (GKInstance refGeneProduct1 : refGeneProducts1) {
            Protein protein1 = getProtein(refGeneProduct1, instToProtein);
            for (GKInstance refGeneProduct2 : refGeneProducts2) {
                Protein protein2 = getProtein(refGeneProduct2, instToProtein);
                Interaction interaction = getInteraction(protein1, 
                                                         protein2,
                                                         pairToInteractionMap);
                if (interaction == null)
                    continue;
                ReactomeSource reactomeSrc = getReactomeSource(source, idToSourceMap);
                interaction.addReactomeSource(reactomeSrc);
            }
        }
    }
    
    private Interaction getInteraction(Protein protein1, 
                                       Protein protein2,
                                       Map<String, Interaction> pairToInteractionMap) {
        String name1 = protein1.getShortName();
        String name2 = protein2.getShortName();
        if (name1 == null || name2 == null)
            return null; // Don't show anything if no name is provided for interacting proteins.
        int compare = name1.compareTo(name2);
        if (compare == 0)
            return null; // Don't generate self interaction
        String key = null;
        if (compare < 0)
            key = name1 + "\t" + name2;
        else
            key = name2 + "\t" + name1;
        Interaction interaction = pairToInteractionMap.get(key);
        if (interaction != null)
            return interaction;
        interaction = new Interaction();
        if (compare < 0) {
            interaction.setFirstProtein(protein1);
            interaction.setSecondProtein(protein2);
        }
        else {
            interaction.setFirstProtein(protein2);
            interaction.setSecondProtein(protein1);
        }
        pairToInteractionMap.put(key, interaction);
        return interaction;
    }
    
    
    private ReactomeSource getReactomeSource(GKInstance instance,
                                             Map<Long, ReactomeSource> idToSourceMap) throws Exception {
        ReactomeSource src = idToSourceMap.get(instance.getDBID());
        if (src == null) {
            src = new ReactomeSource();
            src.setReactomeId(instance.getDBID());
            GKInstance dataSource = getDataSource(instance);
            if (dataSource == null)
                src.setDataSource("Reactome"); // default
            else
                src.setDataSource(dataSource.getDisplayName());
            if (instance.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                src.setSourceType(ReactomeSourceType.REACTION);
            else if (instance.getSchemClass().isa(ReactomeJavaConstants.Complex))
                src.setSourceType(ReactomeSourceType.COMPLEX);
            else if (instance.getSchemClass().isa(ReactomeJavaConstants.Interaction))
                src.setSourceType(ReactomeSourceType.INTERACTION);
            else if (instance.getSchemClass().isa(ReactomeJavaConstants.TargettedInteraction))
                src.setSourceType(ReactomeSourceType.TARGETED_INTERACTION);
            idToSourceMap.put(instance.getDBID(), src);
        }
        return src;
    }
    
    private GKInstance getDataSource(GKInstance instance) throws Exception {
        GKInstance dataSource = null;
        if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.dataSource)) {
            dataSource = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.dataSource);
        }
        return dataSource;
    }
    
    
    /**
     * Get a protein based on an accession number.
     * @param dbAccession
     * @return
     * @throws Exception
     */
    private Protein getProtein(GKInstance refGeneProduct,
                               Map<GKInstance, Protein> instToProtein) throws Exception {
        Protein protein = instToProtein.get(refGeneProduct);
        if (protein != null)
            return protein;
        protein = new Protein();
        // Only protein name is needed in this conversion
        String name = null;
        if (refGeneProduct.getSchemClass().isValidAttribute(ReactomeJavaConstants.geneName))
            name = (String) refGeneProduct.getAttributeValue(ReactomeJavaConstants.geneName);
        if (name == null || name.length() == 0)
            name = (String) refGeneProduct.getAttributeValue(ReactomeJavaConstants.name);
        protein.setShortName(name);
        String accession = (String) refGeneProduct.getAttributeValue(ReactomeJavaConstants.identifier);
        protein.setPrimaryAccession(accession);
        GKInstance db = (GKInstance) refGeneProduct.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
        protein.setPrimaryDbName(db.getDisplayName());
        instToProtein.put(refGeneProduct, protein);
        return protein;
    }
    
    @Test
    public void testConvertPathwayToFIs() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "reactome_67_plus_i",
                                            "root",
                                            "macmysql01");
        setMySQLAdaptor(dba);
        // Check Cell Cycle Checkpoints: 69620
        Long dbId = 69620L;
        List<Interaction> interactions = convertPathwayToFIs(dbId);
        System.out.println("Total interactions: " + interactions.size());
        
    }
}
