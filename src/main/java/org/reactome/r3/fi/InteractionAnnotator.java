/*
 * Created on Jun 17, 2010
 *
 */
package org.reactome.r3.fi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.junit.Test;
import org.reactome.funcInt.FIAnnotation;
import org.reactome.funcInt.Interaction;
import org.reactome.funcInt.ReactomeSource;
import org.reactome.funcInt.ReactomeSourceType;
import org.reactome.r3.service.HibernateInteractionDAO;
import org.reactome.r3.service.InteractionDAO;
import org.reactome.r3.util.FileUtility;
import org.reactome.r3.util.InteractionUtilities;
import org.springframework.orm.hibernate3.support.OpenSessionInViewFilter;

/**
 * This class is used to annotate functional interactions.
 * @author wgm
 *
 */
public class InteractionAnnotator {
    private static final Logger logger = Logger.getLogger(InteractionAnnotator.class);
    private MySQLAdaptor dba;
    private InteractionDAO interationDAO;
    // Load types
    private Map<String, FIAnnotation> nameToType;
    // We use this as a map to search of type 
    private Map<String, String> reverseNameToName;
    // Pre-loaded annotation for quick performance
    private Map<String, FIAnnotation> fiToAnnotation;
    // For return reactome sources for pathway fis
    private Map<String, Set<Long>> fiToSources;
    
    public InteractionAnnotator() throws Exception {
        initInteractionTypes();
    }
    
    /**
     * Initialize a source dba based on some hard-coded information.
     * @return
     * @throws Exception
     */
    public MySQLAdaptor initSourceDBA() throws Exception {
//        dba = new MySQLAdaptor("localhost",
//                               "reactome_41_plus_i",
//                               "root",
//                               "macmysql01",
//                               3306);
//        dba = new MySQLAdaptor("localhost",
//                               "reactome_28_plus_i_myisam",
//                               "root",
//                               "macmysql01",
//                               3306);
        dba.initDumbThreadForConnection();
        return dba;
    }
    
    /**
     * Query Reactome sources DB IDs for a set of FIs.
     * @param fis
     * @return
     * @throws IOException
     */
    public Map<String, Set<Long>> queryPathwayFIsSources(String[] fis) {
        if (fiToSources == null)
            return null;
        Map<String, Set<Long>> rtnFiToSource = new HashMap<String, Set<Long>>();
        for (String fi : fis) {
            Set<Long> sources = fiToSources.get(fi);
            if (sources == null) {
                // Do a flip in case proteins in FIs are not sorted
                String tmpFI = flipFI(fi);
                sources = fiToSources.get(tmpFI);
                if (sources == null)
                    continue;
            }
            rtnFiToSource.put(fi, sources);
        }
        return rtnFiToSource;
    }
    
    private String flipFI(String fi) {
        int index = fi.indexOf("\t");
        String protein1 = fi.substring(0, index);
        String protein2 = fi.substring(index + 1);
        return protein2 + "\t" + protein1;
    }
    
    /**
     * Set the file name from FIs to their sources and load the map into memory.
     * @param fileName
     * @throws IOException
     */
    public void setFIToSourceFile(String fileName) throws IOException {
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        fiToSources = new HashMap<String, Set<Long>>();
        String line = null;
        while ((line = fu.readLine()) != null) {
            int index = line.lastIndexOf("\t");
            String fi = line.substring(0, index);
            String source = line.substring(index + 1);
            InteractionUtilities.addElementToSet(fiToSources, fi, new Long(source));
        }
        fu.close();
    }
    
    /**
     * Set a file for FIs containing annotations that is generated previously for
     * quick performance.
     * @param fileName
     * @throws IOException
     */
    public void setFIWithAnnotationFile(String fileName) throws Exception {
        if (nameToType == null)
            initInteractionTypes();
        File file = new File(fileName);
        if (!file.exists()) {
            logger.error(fileName + " doesn't exist!");
            return;
        }
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        // Header
        String line = fu.readLine();
        fiToAnnotation = new HashMap<String, FIAnnotation>();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            String fi = tokens[0] + "\t" + tokens[1];
            FIAnnotation annotation = new FIAnnotation();
            annotation.setAnnotation(tokens[2]);
            annotation.setDirection(tokens[3]);
            annotation.setScore(new Double(tokens[4]));
            // Need to set reverse annotation
            FIAnnotation template = nameToType.get(annotation.getAnnotation());
            if (template != null) // Just an extra information. This may not be used.
                annotation.setReverseAnnotation(template.getReverseAnnotation());
            fiToAnnotation.put(fi, annotation);
        }
        fu.close();
    }
    
    public void setSourceDBA(MySQLAdaptor dba) {
        this.dba = dba;
        dba.initDumbThreadForConnection();
    }
    
    public MySQLAdaptor getSourceDBA() {
        return this.dba;
    }
    
    public void setInteractionDAO(InteractionDAO dao) {
        this.interationDAO = dao;
    }
    
    public InteractionDAO getInteractionDAO() {
        return this.interationDAO;
    }
    
    /**
     * Query the source type for a FI described by two protein or gene names.
     * TODO: Basically the following query is not right. The output FIs in names have been normalized
     * based on some standard mapping. Please see method HibernateFIReader.generateFIFileInGeneInHibernate.
     * Note: the normalization in the above mentioned method has been removed since a bug in the mapping
     * file from NCBI.
     * @param name1
     * @param name2
     * @return
     * @throws Exception
     */
    public Map<String, Set<String>> queryTypes(String name1,
                                               String name2) throws Exception {
        List<Interaction> interactions = interationDAO.queryOnNames(name1, name2);
        if (interactions == null || interactions.size() == 0) {
            logger.error(name1 + ", " + name2 + " cannot find an interaction!");
            return null;
        }
        // Make sure the correct name types
        Map<String, Set<String>> pairToTypes = new HashMap<String, Set<String>>();
        for (Iterator it = interactions.iterator(); it.hasNext();) {
            Interaction interaction = (Interaction) it.next();
            Set<String> types = queryType(interaction);
            // Use the upper case only to avoid more than two pairs between two proteins
            // (e.g.): GNAS and PIK3R1
            String protein1 = interaction.getFirstProtein().getLabel().toUpperCase();
            String protein2 = interaction.getSecondProtein().getLabel().toUpperCase();
            String key = protein1 + "\t" + protein2;
            Set<String> set = pairToTypes.get(key);
            if (set == null) {
                set = new HashSet<String>();
                pairToTypes.put(key, set);
            }
            set.addAll(types);
        }
        return convertPassiveTypes(pairToTypes);
    }
    
    /**
     * Query the type for the specified two proteins. There should be only one type returned.
     * So this method has merged types for interactions name1:name2 and name2:name1.
     * @param name1
     * @param name2
     * @param session
     * @return
     * @throws Exception
     */
    public FIAnnotation annotate(String name1,
                                 String name2) throws Exception {
        if (nameToType == null)
            initInteractionTypes();
        if (fiToAnnotation != null) {
            return getAnnotationFromSaved(name1, name2);
        }
        List<Interaction> interactions = interationDAO.queryOnNames(name1, name2);
        if (interactions == null || interactions.size() == 0) {
            logger.error(name1 + ", " + name2 + " cannot find an interaction!");
            return null;
        }
        return annotate(name1, 
                        name2,
                        interactions);
    }
    
    private List<FIAnnotation> getAnnotationsFromSaved(Map<String, String[]> idToNames) {
        List<FIAnnotation> annotations = new ArrayList<FIAnnotation>();
        for (String id : idToNames.keySet()) {
            String[] names = idToNames.get(id);
            FIAnnotation annotation = getAnnotationFromSaved(names[0], names[1]);
            if (annotation != null) {
                annotation.setInteractionId(id);
                annotations.add(annotation);
            }
        }
        return annotations;
    }
    
    /**
     * Get the FIAnnotation from a preloaded annotation file.
     * @param name1
     * @param name2
     * @return
     */
    private FIAnnotation getAnnotationFromSaved(String name1, String name2) {
        String fi = name1 + "\t" + name2;
        FIAnnotation annotation = fiToAnnotation.get(fi);
        if (annotation != null)
            return generateAnnotationFromSaved(annotation, false);
        // If name1 and name2 are reversed
        fi = name2 + "\t" + name1;
        annotation = fiToAnnotation.get(fi);
        if (annotation != null) {
            return generateAnnotationFromSaved(annotation, true);
        }
        // Need to create an unknown annotation
        annotation = new FIAnnotation();
        annotation.setAnnotation("unkown");
//        annotation.setDirection("-");
//        annotation.setScore(1.0d);
        return annotation;
    }
    
    /**
     * If an annotation needs to be reversed and this annotation is merged, we may have to
     * reverse each annotation merged into its reverse one and then merge them together again.
     * @param annotation
     * @param needReverse
     * @return
     */
    private FIAnnotation generateAnnotationFromSaved(FIAnnotation annotation,
                                                     boolean needReverse) {
        if (!needReverse)
            return annotation; // There is no need to copy.
        String[] tokens = annotation.getAnnotation().split("; ");
        Set<FIAnnotation> list = new HashSet<FIAnnotation>();
        for (String token : tokens) {
            FIAnnotation template = nameToType.get(token);
            if (template == null) {
                // Try one more from another way around
                String listedName = reverseNameToName.get(token);
                template = nameToType.get(listedName);
                if (template == null) 
                    //                logger.error(token + " doesn't have a FIAnnotation specified!");
                    continue; // We do our best, and have to ignore this case not to stop the process.
                else
                    list.add(template); // This has been reversed already.
            }
            else
                list.add(template.generateReverseType());
        }
        return mergeTypes(list);
    }
    
    /**
     * Annotate an Interaction object.
     * @param interaction
     * @return
     * @throws Exception
     */
    public FIAnnotation annotate(Interaction interaction) throws Exception {
        Set<FIAnnotation> annotations = queryType(interaction,
                                                  interaction.getFirstProtein().getShortName(),
                                                  interaction.getSecondProtein().getShortName());
        return mergeTypes(annotations);
    }

    private FIAnnotation annotate(String name1, 
                                  String name2,
                                  List<Interaction> interactions) throws Exception {
        Set<FIAnnotation> rtnTypes = new HashSet<FIAnnotation>();
        for (Interaction interaction : interactions) {
            Set<FIAnnotation> fiTypes = queryType(interaction, name1, name2);
            rtnTypes.addAll(fiTypes);
        }
        FIAnnotation rtn = mergeTypes(rtnTypes);
        // To get the score for it based on interactions. Extracted FIs have score 1.0.
        // It is possible that several interactions have been mixed together. 
        // See: ReactomeR3CytoscacpePlugin.displayInteraction
        double score = 0;
        // Find the highest score in this loop.
        for (Interaction i : interactions) {
            if (i.getEvidence() != null) {
                if (i.getEvidence().getProbability() > score)
                    score = i.getEvidence().getProbability();
            }
            else { // Extracted FI
                score = 1.0d;
                break;
            }
        }
        rtn.setScore(score);
        return rtn;
    }
    
    public List<FIAnnotation> annotate(Map<String, String[]> idToNames) throws Exception {
        if (nameToType == null)
            initInteractionTypes();
        Set<String> allNames = new HashSet<String>();
        for (String[] names : idToNames.values()) {
            allNames.add(names[0]);
            allNames.add(names[1]);
        }
        List<FIAnnotation> annotations = new ArrayList<FIAnnotation>();
        if (allNames.size() == 0)
            return annotations; // Just in case some third-part network is passed in.
        if (fiToAnnotation != null)
            return getAnnotationsFromSaved(idToNames);
        List<Interaction> interactions = interationDAO.searchNamesForAll(new ArrayList<String>(allNames));
        List<Interaction> foundInteractions = new ArrayList<Interaction>();
        for (String id : idToNames.keySet()) {
            String[] names = idToNames.get(id);
            foundInteractions.clear();
            for (Interaction in : interactions) {
                String name1 = in.getFirstProtein().getShortName();
                String name2 = in.getSecondProtein().getShortName();
                if ((name1.equals(names[0]) && name2.equals(names[1])) ||
                    (name1.equals(names[1]) && name2.equals(names[0])))
                    foundInteractions.add(in);
            }
            if (foundInteractions.size() == 0) {
                logger.error(names[0] + ", " + names[1] + " cannot find an interaction!");
                continue;
            }
            FIAnnotation annotation = annotate(names[0], names[1], foundInteractions);
            if (annotation != null) {
                annotation.setInteractionId(id);
                annotations.add(annotation);
            }
        }
        return annotations;
    }
    
    public String generateType(Set<String> types) {
        if (types == null || types.size() == 0)
            return "unknown";
        List<String> list = new ArrayList<String>(types);
        Collections.sort(list);
        StringBuilder builder = new StringBuilder();
        for (Iterator<String> it = list.iterator(); it.hasNext();) {
            builder.append(it.next());
            if (it.hasNext())
                builder.append("; ");
        }
        return builder.toString();
    }
    
    private FIAnnotation mergeTypes(Set<FIAnnotation> types) {
        if (types.size()  == 1) {
            FIAnnotation original = types.iterator().next();
            return original.cloneType();
        }
        FIAnnotation merged = new FIAnnotation();
        Set<String> typeNames = new HashSet<String>();
        Set<String> firstLetter = new HashSet<String>(1);
        Set<String> thirdLetter = new HashSet<String>(1);
        int index = 0;
        for (FIAnnotation type : types) {
            typeNames.add(type.getAnnotation());
            if (type.getDirection() != null && type.getDirection().length() > 1) {
                index = type.getDirection().indexOf("-");
                if (index == 0)
                    thirdLetter.add(type.getDirection().charAt(1) + "");
                else if (index == 1)
                    firstLetter.add(type.getDirection().charAt(0) + "");
            }
        }
        merged.setAnnotation(generateType(typeNames));
        // Need to generate direction
        String direction = ((firstLetter.size() == 1) ? firstLetter.iterator().next() : "") + 
                           "-" + // Always
                           ((thirdLetter.size() == 1) ? thirdLetter.iterator().next() : "");
        merged.setDirection(direction);
        return merged;
    }
    
    /**
     * Remove types ending with "_by"
     * @param pairToTypes
     * @return
     */
    private Map<String, Set<String>> convertPassiveTypes(Map<String, Set<String>> pairToTypes) {
        Map<String, Set<String>> convertedPairToTypes = new HashMap<String, Set<String>>();
        for (String pair : pairToTypes.keySet()) {
            Set<String> types = pairToTypes.get(pair);
            int index = pair.indexOf("\t");
            String name1 = pair.substring(0, index);
            String name2 = pair.substring(index + 1);
            String reverseKey = name2 + "\t" + name1;
            for (String type : types) {
                if (type.endsWith("_by")) {
                    String newType = null;
                    if (type.equals("inhibited_by")) {
                        newType = "inhibit";
                    }
                    else {
                        index = type.indexOf("d_by");
                        newType = type.substring(0, index);
                    }
                    Set<String> converted = convertedPairToTypes.get(reverseKey);
                    if (converted == null) {
                        converted = new HashSet<String>();
                        convertedPairToTypes.put(reverseKey, converted);
                    }
                    converted.add(newType);
                }
                else {
                    Set<String> original = convertedPairToTypes.get(pair);
                    if (original == null) {
                        original = new HashSet<String>();
                        convertedPairToTypes.put(pair, original);
                    }
                    original.add(type);
                }
            }
        }
        return convertedPairToTypes;
    }
    
    /**
     * Query a type for a passed Interaction.
     * @param interaction
     * @return
     * @throws Exception
     */
    private Set<String> queryType(Interaction interaction) throws Exception {
        if (interaction == null)
            return null;
        Set<String> types = new HashSet<String>();
        if (interaction.getEvidence() != null) {
            types.add("predicted");
            return types; // Predicted based on NBC
        }
        // Need to query the Reactome Source
        Set<ReactomeSource> sources = interaction.getReactomeSources();
        if (dba == null)
            initSourceDBA();
        for (ReactomeSource src : sources) {
            if (src.getSourceType() == ReactomeSourceType.COMPLEX)
                types.add("complex");
            else if (src.getSourceType() == ReactomeSourceType.INTERACTION) {
                String type = extractInteractionTypeInString(src);
                types.add(type);
            }
            else if (src.getSourceType() == ReactomeSourceType.REACTION) {
                String type = extractTypeFromReaction(src, interaction);
                for (String type1 : type.split(":"))
                    types.add(type1);
            }
            else if (src.getSourceType() == ReactomeSourceType.TARGETED_INTERACTION) {
                String type = extractTypeFromTargetedInteraction(src, interaction);
                types.add(type);
            }
        }
        return types;
    }
    
    private Set<FIAnnotation> queryType(Interaction interaction, 
                                        String name1,
                                        String name2) throws Exception {
        Set<FIAnnotation> fiTypes = new HashSet<FIAnnotation>();
        if (interaction == null)
            return fiTypes;
        if (interaction.getEvidence() != null) {
            fiTypes.add(nameToType.get("predicted"));
            return fiTypes;
        }
        // Need to query the Reactome Source
        Set<ReactomeSource> sources = interaction.getReactomeSources();
        if (dba == null)
            initSourceDBA();
        boolean needReverse = false;
        // Need to see if a reverse type should be used.
        if (interaction.getFirstProtein().getShortName().equals(name2))
            needReverse = true;
        FIAnnotation fiType = null;
        boolean isReaction = false;
        for (ReactomeSource src : sources) {
            isReaction = false;
            if (src.getSourceType() == ReactomeSourceType.COMPLEX) {
                fiType = nameToType.get("complex");
            }
            else if (src.getSourceType() == ReactomeSourceType.INTERACTION) {
                fiType = extractInteractionType(src, 
                                                interaction,
                                                needReverse);
            }
            else if (src.getSourceType() == ReactomeSourceType.REACTION) {
                String types = extractTypeFromReaction(src, interaction);
                String[] tokens = types.split(":");
                for (String type : tokens) {
                    fiType = nameToType.get(type);
                    if (fiType != null && needReverse)
                        fiType = fiType.generateReverseType();
                    if (fiType != null)
                        fiTypes.add(fiType);
                }
                isReaction = true;
            }
            else if (src.getSourceType() == ReactomeSourceType.TARGETED_INTERACTION) {
                String type = extractTypeFromTargetedInteraction(src, interaction);
                fiType = nameToType.get(type);
                if (fiType != null && needReverse)
                    fiType = fiType.generateReverseType();
            }
            if (fiType == null) {
                logger.error(name1 + " " + name2 + " has no type!" + " Interaction: " + interaction.getDbId());
                fiType = nameToType.get("unknown"); // Used as an error mark
            }
            if (!isReaction)
                fiTypes.add(fiType);
        }
        return fiTypes;
    }
    
    /**
     * This method is used to extract type based on TargetedInteraction.
     * @param src
     * @return
     * @throws Exception
     */
    private String extractTypeFromTargetedInteraction(ReactomeSource src,
                                                      Interaction interaction) throws Exception {
//        if (src.getDataSource().equals("TRED")) { // Right now covers TRED TF/Target interactions only.
        // First check based on ids
            Long dbId = src.getReactomeId();
            GKInstance instance = dba.fetchInstance(dbId);
            // Get factor
            GKInstance factor = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.factor);
            Set<GKInstance> refPepSeqs = InstanceUtilities.grepRefPepSeqsFromPhysicalEntity(factor);
            Set<String> factorIds = new HashSet<String>();
            Set<String> factorNames = new HashSet<String>();
            for (GKInstance refPepSeq : refPepSeqs) {
                String id = (String) refPepSeq.getAttributeValue(ReactomeJavaConstants.identifier);
                factorIds.add(id);
                if (refPepSeq.getSchemClass().isValidAttribute(ReactomeJavaConstants.geneName)) {
                    String geneName = (String) refPepSeq.getAttributeValue(ReactomeJavaConstants.geneName);
                    if (geneName != null)
                        factorNames.add(geneName);
                }
            }
            // Get target
            GKInstance target = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.target);
            refPepSeqs = InstanceUtilities.grepRefPepSeqsFromPhysicalEntity(target);
            Set<String> targetIds = new HashSet<String>();
            Set<String> targetNames = new HashSet<String>();
            for (GKInstance refPepSeq : refPepSeqs) {
                String id = (String) refPepSeq.getAttributeValue(ReactomeJavaConstants.identifier);
                targetIds.add(id);
                if (refPepSeq.getSchemClass().isValidAttribute(ReactomeJavaConstants.geneName)) {
                    String geneName = (String) refPepSeq.getAttributeValue(ReactomeJavaConstants.geneName);
                    if (geneName != null)
                        targetNames.add(geneName);
                }
            }
            String proteinId1 = interaction.getFirstProtein().getPrimaryAccession();
            String protein1 = interaction.getFirstProtein().getShortName();
            String proteinId2 = interaction.getSecondProtein().getPrimaryAccession();
            String protein2 = interaction.getSecondProtein().getShortName();
            if (factorIds.contains(proteinId1) && targetIds.contains(proteinId2)) {
                return "expression regulates";
            }
            if (factorIds.contains(proteinId2) && targetIds.contains(proteinId1)) {
                return "expression regulated by";
            }
        // If we cannot find mapping based on ids, we may try using gene names since all TF/target interactions
        // are loaded based on gene names and may choose a ids that are not the same as in the database.
            if (factorNames.contains(protein1) && targetNames.contains(protein2)) {
                return "expression regulates";
            }
            if (factorNames.contains(protein2) && targetNames.contains(protein1)) {
                return "expression regulated by";
            }
//        }
        return null;
    }
    
    /**
     * Extract the FI type from a reaction. There are four types for a FI extracted from a
     * reaction: Input, Catalyze (Or Catalyzed), Inhibit (Inhibited), Activate (Activated).
     * Two proteins may be invovled in multiple types of FIs (e.g. activation and catalysis) and
     * sometimes may have conflict information (e.g. activation and inhibition together). The output
     * from this method returns only one value in the first order: activate, inhibit, catalyze,
     * activated by, inhibited by, catalyzed by, and input. 
     * NOTE: the type is used for reference only!
     * As of April 9, 2019, all types will be returned.
     * @param src
     * @param interaction
     * @return
     * @throws Exception
     */
    private String extractTypeFromReaction(ReactomeSource src,
                                           Interaction interaction) throws Exception {
        String firstProteinId = interaction.getFirstProtein().getPrimaryAccession();
        String secondProteinId = interaction.getSecondProtein().getPrimaryAccession();
        // Need to map back to the reaction
        GKInstance reaction = dba.fetchInstance(src.getReactomeId());
        // List types first
        List list = reaction.getAttributeValuesList(ReactomeJavaConstants.input);
        Set<GKInstance> inputs = new HashSet<GKInstance>();
        if (list != null) {
            for (Object obj : list) {
                GKInstance input = (GKInstance) obj;
                inputs.add(input);
            }
        }
        Set<String> inputIds = extractIds(inputs);
        Set<GKInstance> catalysts = getCatalysts(reaction);
        Set<String> catalystIds = extractIds(catalysts);
        // Check regulators
        Set<GKInstance> activators = getRegulators(reaction, 
                                                   ReactomeJavaConstants.PositiveRegulation);
        Set<String> activatorIds = extractIds(activators);
        Set<GKInstance> inhibitors = getRegulators(reaction, 
                                                   ReactomeJavaConstants.NegativeRegulation);
        Set<String> inhibitorIds = extractIds(inhibitors);
        Set<String> rtn = new HashSet<>();
        // Generate the types
        if (activatorIds.contains(firstProteinId) && !activatorIds.contains(secondProteinId)) {
            rtn.add("activate");
        }
        if (inhibitorIds.contains(firstProteinId) && !inhibitorIds.contains(secondProteinId))
            rtn.add("inhibit");
        if (catalystIds.contains(firstProteinId) && inputIds.contains(secondProteinId))
            rtn.add("catalyze");
        // Check the other direction
        if (activatorIds.contains(secondProteinId) && !activatorIds.contains(firstProteinId))
            rtn.add("activated by");
        if (inhibitorIds.contains(secondProteinId) && !inhibitorIds.contains(firstProteinId))
            rtn.add("inhibited by");
        if (catalystIds.contains(secondProteinId) && inputIds.contains(firstProteinId))
            rtn.add("catalyzed by");
        if (inputIds.contains(firstProteinId) && inputIds.contains(secondProteinId))
            rtn.add("input");
        if (rtn.size() == 0)
            rtn.add("reaction"); // Cannot see the difference
        return rtn.stream().collect(Collectors.joining(":"));
    }
    
    /**
     * Extract ids from the set of GKInstances.
     */
    private Set<String> extractIds(Set<GKInstance> instances) throws Exception {
        Set<String> ids = new HashSet<String>();
        for (GKInstance instance : instances) {
            Set<GKInstance> refPepSeqs = InstanceUtilities.grepRefPepSeqsFromPhysicalEntity(instance);
            for (GKInstance refPepSeq : refPepSeqs) {
                String id = (String) refPepSeq.getAttributeValue(ReactomeJavaConstants.identifier);
                if (id != null)
                    ids.add(id);
            }
        }
        return ids;
    }
    
    private Set<GKInstance> getCatalysts(GKInstance reaction) throws Exception {
        Set<GKInstance> catalysts = new HashSet<GKInstance>();
        List cas = reaction.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        if (cas != null) {
            for (Iterator it1 = cas.iterator(); it1.hasNext();) {
                GKInstance ca = (GKInstance) it1.next();
                List list = ca.getAttributeValuesList(ReactomeJavaConstants.physicalEntity);
                if (list != null)
                    catalysts.addAll(list);
            }
        }
        return catalysts;
    }
    
    private Set<GKInstance> getRegulators(GKInstance reaction,
                                          String clsName) throws Exception {
        Set<GKInstance> regulators = new HashSet<GKInstance>();
        Collection<GKInstance> regulations = InstanceUtilities.getRegulations(reaction);
        if (regulations != null) {
            for (Iterator<GKInstance> it1 = regulations.iterator(); it1.hasNext();) {
                GKInstance regulation = (GKInstance) it1.next();
                if (regulation.getSchemClass().isa(clsName)) {
                    List<GKInstance> list = regulation.getAttributeValuesList(ReactomeJavaConstants.regulator);
                    for (Iterator<GKInstance> it2 = list.iterator(); it2.hasNext();) {
                        GKInstance tmp = (GKInstance) it2.next();
                        if (tmp.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity))
                            regulators.add(tmp);
                    }
                }
            }
        }
        return regulators;
    }
    
    
    
    /**
     * A helper method to extract interaction type from a ReactomeSource object.
     * @param src
     * @return
     * @throws Exception
     */
    private FIAnnotation extractInteractionType(ReactomeSource src,
                                          Interaction interaction,
                                          boolean needReverse) throws Exception {
        GKInstance instance = dba.fetchInstance(src.getReactomeId());
        String type = (String) instance.getAttributeValue(ReactomeJavaConstants.interactionType);
        if (type == null)
            return nameToType.get("interaction");
        // Do a very simple reverse mapping
        List<?> interactors = instance.getAttributeValuesList(ReactomeJavaConstants.interactor);
        // Check the first type only
        GKInstance firstValue = (GKInstance) interactors.get(0);
        Set<GKInstance> tmp = new HashSet<GKInstance>();
        tmp.add(firstValue);
        Set<String> firstIdentifiers = extractIds(tmp);
        if (needReverse) {
            if (firstIdentifiers.contains(interaction.getFirstProtein().getPrimaryAccession())) {
                FIAnnotation rtn = nameToType.get(type);
                return rtn.generateReverseType();
            }
            if (firstIdentifiers.contains(interaction.getSecondProtein().getPrimaryAccession())) {
                FIAnnotation rtn = nameToType.get(type);
                return rtn;
            }
        }
        else {
            if (firstIdentifiers.contains(interaction.getFirstProtein().getPrimaryAccession())) {
                FIAnnotation rtn = nameToType.get(type);
                return rtn;
            }
            if (firstIdentifiers.contains(interaction.getSecondProtein().getPrimaryAccession())) {
                FIAnnotation rtn = nameToType.get(type);
                return rtn.generateReverseType();
            }
        }
        return nameToType.get("interaction"); // Too complicated!!!
    }
    
    private String extractInteractionTypeInString(ReactomeSource src) throws Exception {
        GKInstance instance = dba.fetchInstance(src.getReactomeId());
        String type = (String) instance.getAttributeValue(ReactomeJavaConstants.interactionType);
        if (type == null)
            return "interaction";
        return type;
    }
    
    /**
     * This method is used to list interaction types from the database.
     * @throws Exception
     */
    @Test
    public void listInteractionTypes() throws Exception {
        initSourceDBA();
        Collection interactions = dba.fetchInstancesByClass(ReactomeJavaConstants.Interaction);
        SchemaClass cls = dba.getSchema().getClassByName(ReactomeJavaConstants.Interaction);
        SchemaAttribute att = cls.getAttribute(ReactomeJavaConstants.interactionType);
        dba.loadInstanceAttributeValues(interactions, att);
        Set<String> types = new HashSet<String>();
        for (Iterator it = interactions.iterator(); it.hasNext();) {
            GKInstance interaction = (GKInstance) it.next();
            String type = (String) interaction.getAttributeValue(ReactomeJavaConstants.interactionType);
            if (type != null)
                types.add(type);
        }
        //System.out.println("Types: " + types.size());
        for (String type : types) {// For the InteractionTypeMapper file.
            System.out.println("<type name=\"" + type + "\" reverse=\"" + type + "\" direction=\"none\"/>");
            //System.out.println(type);
        }
    }
    
    /**
     * Load FI types from a pre-generated file and add some types known already (e.g. predicted).
     * @return
     * @throws Exception
     */
    private void initInteractionTypes() throws Exception {
        nameToType= new HashMap<String, FIAnnotation>();
        reverseNameToName = new HashMap<String, String>();
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(getClass().getResourceAsStream("InteractionTypeMapper.xml"));
        List list = document.getDocument().getRootElement().getChildren("type");
        for (Iterator it = list.iterator(); it.hasNext();) {
            Element elm = (Element) it.next();
            String name = elm.getAttributeValue("name");
            String reverse = elm.getAttributeValue("reverse");
            String direction = elm.getAttributeValue("direction");
            FIAnnotation type = new FIAnnotation();
            type.setAnnotation(name);
            type.setDirection(direction);
            type.setReverseAnnotation(reverse);
            nameToType.put(name, type);
            reverseNameToName.put(reverse, name);
        }
        // Some specific type
        FIAnnotation predictedType = new FIAnnotation();
        predictedType.setAnnotation("predicted");
        predictedType.setReverseAnnotation("predicted");
        predictedType.setDirection("-");
        nameToType.put(predictedType.getAnnotation(), predictedType);
        // Some specific type
        FIAnnotation unknownType = new FIAnnotation();
        unknownType.setAnnotation("unknown");
        nameToType.put(unknownType.getAnnotation(), unknownType);
        loadReactionTypes(nameToType);
    }
    
    private void loadReactionTypes(Map<String, FIAnnotation> nameToType) {
        String[] types = new String[] {
                "complex",
                "activate",
                "inhibit",
                "catalyze",
                "activated by",
                "inhibited by",
                "catalyzed by",
                "input",
                "reaction", // Cannot see the difference
                // Following for TF/Target interactions
                "expression regulates",
                "expression regulated by",
                "interaction"
        };
        String[] reverseTypes = new String[] {
                "complex",
                "activated by",
                "inhibited by",
                "catalyzed by",
                "activate",
                "inhibite",
                "catalyze",
                "input",
                "reaction", // Cannot see the difference
                "expression regulated by",
                "expression regulates",
                "interaction"
        };
        String[] directions = new String[] {
                "-",
                "->",
                "-|",
                "->",
                "<-",
                "|-",
                "<-",
                "-",
                "-",
                "->",
                "<-",
                "-"
        };
        for (int i = 0; i < types.length; i++) {
            FIAnnotation type = new FIAnnotation();
            type.setAnnotation(types[i]);
            type.setReverseAnnotation(reverseTypes[i]);
            type.setDirection(directions[i]);
            nameToType.put(type.getAnnotation(), type);
        }
    }
    
    /**
     * This method is used to annotate FIs so that they can be placed in the download
     * folder and used for quick performance.
     * @throws Exception
     */
    @Test
    public void annoateAllFIs() throws Exception {
        // Parameters for the 2009 version
//        MySQLAdaptor dba = new MySQLAdaptor("localhost",
//                                            "reactome_28_plus_i_myisam",
//                                            "root",
//                                            "macmysql01");
//        String fiFileName = "WebContent/WEB-INF/FIsInGene_041709.txt";
//        String outFileName = "WebContent/WEB-INF/FIsInGene_041709_with_annotations.txt";
//        File configFile = new File("WebContent/WEB-INF/funcIntHibernate.cfg.xml");
        
        // Parameters for the 2012 version
//        MySQLAdaptor dba = new MySQLAdaptor("localhost",
//                                            "reactome_41_plus_i",
//                                            "root",
//                                            "macmysql01");
//        String dirName = "/Users/gwu/Documents/EclipseWorkspace/FINetworkBuild/results/2012/";
//        String fiFileName = dirName + "FIsInGene_071012.txt";
//        String outFileName = dirName + "FIsInGene_071012_with_annotations.txt";
//        
//        File configFile = new File("WebContent/WEB-INF/funcIntHibernate.cfg.xml");
        
        // Parameters for the 2013 version
//        MySQLAdaptor dba = new MySQLAdaptor("localhost",
//                                            "reactome_47_plus_i",
//                                            "root",
//                                            "macmysql01");
//        String dirName = "/Users/gwu/Documents/EclipseWorkspace/FINetworkBuild/results/2013/";
//        String fiFileName = dirName + "FIsInGene_121013.txt";
//        String outFileName = dirName + "FIsInGene_121013_with_annotations.txt";
//        
//        File hibernateConfig = new File("WebContent/WEB-INF/funcIntHibernate.cfg.xml");
        
        // The following running are based on configuration in the FINetworkBuild project, which
        // should work for all versions.
//        String fiNetworkBuildDir = "/Users/wug/Documents/eclipse_workspace/FINetworkBuild/";
        // Since 2018, FINetworkBuild project has been migrated to GitHub
        String fiNetworkBuildDir = "/Users/wug/git/FINetworkBuild/";
        String resourceDir = fiNetworkBuildDir + "resources/";
        String configFile = resourceDir + "configuration.prop";
        Properties config = new Properties();
        config.load(new FileInputStream(configFile));
        
        String resultDir = config.getProperty("RESULT_DIR");
        String dirName = resultDir.replace("${YEAR}", config.getProperty("YEAR")) + "/";
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            config.getProperty("REACTOME_SOURCE_DB_NAME"),
                                            config.getProperty("DB_USER"),
                                            config.getProperty("DB_PWD"));
        String tmp = config.getProperty("GENE_FI_FILE_NAME");
        int index = tmp.lastIndexOf("/");
        String fiFileName = dirName + tmp.substring(index + 1);
        index = fiFileName.lastIndexOf(".");
        String outFileName = fiFileName.substring(0, index) + "_with_annotations" + fiFileName.substring(index);
        File hibernateConfig = new File(resourceDir + "funcIntHibernate.cfg.xml");
        
        setSourceDBA(dba);
        SessionFactory sf = new Configuration().configure(hibernateConfig).buildSessionFactory();
        final Session session = sf.openSession();
        InteractionDAO interactionDAO = new HibernateInteractionDAO() {
            public List<Interaction> queryOnNames(final String name1, final String name2) {
                String queryText = "FROM Interaction as i WHERE ((i.firstProtein.shortName = ? AND i.secondProtein.shortName = ?) OR " +
                        "(i.firstProtein.shortName = ? AND i.secondProtein.shortName = ?))"; // For a reverse search
                Query query = session.createQuery(queryText);
                query.setString(0, name1);
                query.setString(1, name2);
                query.setString(2, name2);
                query.setString(3, name1);
                List interactions = query.list();
                return (List<Interaction>) interactions;
            }
        };
        setInteractionDAO(interactionDAO);
        
        OpenSessionInViewFilter filter = new OpenSessionInViewFilter();
        filter.setSingleSession(true);
        
        // This is a special case occurred in 2014 version of the FI network: because of the same sequence of TRAPPC2 and
        // TRAPPC2P1, the following FI cannot be mapped and a manual annotation has to be performed.
        // The following FI doesn't appear in 2015. So the following code is commented out!
//        String fi = "TRAPPC2P1\tZBTB33";
        // Error in 2016 version of the FI network.
//        String fi = "BRCA1\tTRAPPC2B"; 
//        fi = "CBSL\tUSF2";
//        fi = "FOS\tHSPA1B";
//        fi = "HSPA1B\tNFIC";
//        fi = "HSPA1B\tTFAP2A";
//        fi = "TRAPPC2B\tZBTB33";
        // Errors in 2017 version of the FI network. Copy annotation from previous year.
        // Test for 2018
//        fi = "AAAS\tSLU7";
//        String[] genes = fi.split("\t");
//        FIAnnotation fiAnnot = annotate(genes[0], genes[1]);
//        String text = String.format("%s\t%s\t%s\t%.2f",
//                                       fi,
//                                       fiAnnot.getAnnotation(),
//                                       fiAnnot.getDirection(),
//                                       fiAnnot.getScore());
//        System.out.println(text);
//        if (true)
//            return;

        FileUtility fu = new FileUtility();
        fu.setInput(fiFileName);
        fu.setOutput(outFileName);
        fu.printLine("Gene1\tGene2\tAnnotation\tDirection\tScore");
        String line = null;
        int count = 0;
        while ((line = fu.readLine()) != null) {
//            if (!(line.contains("ATF2") && line.contains("CDKN1A")))
//                continue;
            String[] tokens = line.split("\t");
            FIAnnotation annotation = annotate(tokens[0], tokens[1]);
            String outLine = String.format("%s\t%s\t%s\t%.2f",
                                           line,
                                           annotation.getAnnotation(),
                                           annotation.getDirection(),
                                           annotation.getScore());
            fu.printLine(outLine);
            count ++;
//            if (count == 100)
//                break;
        }
        fu.close();
        session.close();
        sf.close();
        logger.info("Total interactions: " + count);
    }
}
