package org.reactome.r3.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.reactome.r3.util.FileUtility;

import edu.ohsu.bcb.druggability.dataModel.DatabaseRef;
import edu.ohsu.bcb.druggability.dataModel.Drug;
import edu.ohsu.bcb.druggability.dataModel.ExpEvidence;
import edu.ohsu.bcb.druggability.dataModel.Interaction;
import edu.ohsu.bcb.druggability.dataModel.LitEvidence;
import edu.ohsu.bcb.druggability.dataModel.Source;
import edu.ohsu.bcb.druggability.dataModel.Target;

public class DrugCentralFileParser {
    private Map<String, Drug> nameToDrug = new HashMap<>();
    private Map<String, Target> accToTarget = new HashMap<>();
    private Map<String, Source> keyToSource = new HashMap<>();
    private Map<String, Interaction> keyToInteraction = new HashMap<>();
    // For marking
    private DatabaseRef drugCentral;
    private Source drugCentralSource;
    
    public DrugCentralFileParser() {
    }
    
    @Test
    public void testParseFile() throws IOException {
        String fileName = "WebContent/WEB-INF/drug.target.interaction.08292017.tsv";
        File file = new File(fileName);
        List<Interaction> interactions = parseFile(file);
        System.out.println("Total interactions: " + interactions.size());
        // Check if there is any duplication
        Map<String, Interaction> keyToInt = new HashMap<>();
        interactions.forEach(i -> {
            String key = i.getIntDrug().getDrugID() + "\t" + i.getIntTarget().getUniprotID();
//            if (keyToInt.containsKey(key)) {
//                System.out.println("Duplicated Key: " + key);
//            }
            keyToInt.put(key, i);
        });
        System.out.println("Total keyToInt: " + keyToInt.size());
        if (keyToInt.size() != interactions.size())
            System.err.println("There are duplicated interactions!");
        
        interactions.stream().filter(i -> i.getIntDrug().getDrugName().equals("Gefitinib")).
        forEach(i -> {
            StringBuilder builder = new StringBuilder();
            builder.append(i.getIntDrug().getDrugName());
            builder.append("\t").append(i.getIntTarget().getTargetName());
            builder.append("\t").append(i.getInteractionType());
            ExpEvidence evidence = i.getExpEvidenceSet().stream().findFirst().get();
            builder.append("\t").append(evidence.getAssayRelation());
            builder.append("\t").append(evidence.getAssayValue());
            builder.append("\t").append(evidence.getAssayUnits());
            
            System.out.println(builder.toString());
        });
        
        Set<String> drugs = interactions.stream()
                                        .map(i -> i.getIntDrug().getDrugName())
                                        .collect(Collectors.toSet());
        System.out.println("Total drugs: " + drugs.size());
        
        Set<String> targets = interactions.stream()
                                          .map(i -> i.getIntTarget().getUniprotID())
                                          .collect(Collectors.toSet());
        System.out.println("Total targets: " + targets.size());
        
        Set<String> assayTypes = interactions.stream()
                .map(i -> i.getExpEvidenceSet().stream().findFirst().get().getAssayType())
                .collect(Collectors.toSet());
        System.out.println("Assay types: " + assayTypes.size());
        assayTypes.forEach(System.out::println);
        
        Set<String> interactionTypes = interactions.stream()
                .filter(i -> i.getInteractionType() != null)
                .map(i -> i.getInteractionType())
                .collect(Collectors.toSet());
        System.out.println("Total interaction types: " + interactionTypes.size());
        interactionTypes.forEach(System.out::println);
    }
    
    public List<Interaction> parseFile(File file) throws IOException {
        FileUtility fu = new FileUtility();
        fu.setInput(file.getAbsolutePath());
        String line = fu.readLine();
        // Headers are: "DRUG_NAME" "STRUCT_ID" "TARGET_NAME"   "TARGET_CLASS"  
        // "ACCESSION" "GENE"  "SWISSPROT" "ACT_VALUE" "ACT_UNIT"  
        // "ACT_TYPE"  "ACT_COMMENT"   "ACT_SOURCE"    "RELATION"  "MOA"   "MOA_SOURCE" 
        // "ACT_SOURCE_URL"    "MOA_SOURCE_URL"    "ACTION_TYPE"   "TDL"   "ORGANISM"
        int parsedLine = 0;
        while ((line = fu.readLine()) != null) {
            parsedLine ++;
//            System.out.println(line);
            String[] tokens = line.split("\t");
            removeQuotationMarks(tokens);
            if (tokens[4].trim().length() == 0)
                continue; // Work with proteins only
            // Filter to human only
            if (tokens.length < 20 || !tokens[19].equals("Homo sapiens"))
                continue;
            Drug drug = getDrug(tokens);
            ExpEvidence evidence = parseEvidence(tokens);
            evidence.setExpID(parsedLine);
            // Target may be listed as Q01668|Q13936, which is for multiple targets
            Set<Target> targets = getTargets(tokens);
            for (Target target : targets) {
                String key = drug.getDrugID() + "\t" + target.getUniprotID();
                Interaction interaction = keyToInteraction.get(key);
                if (interaction != null) {
                    Set<ExpEvidence> evidences = interaction.getExpEvidenceSet();
                    evidences.add(evidence);
                    continue;
                }
                interaction = new Interaction();
                interaction.setIntDrug(drug);
                interaction.setIntTarget(target);
                if (tokens.length > 17) // ActionType (17) 
                    interaction.setInteractionType(tokens[17]);
                Set<ExpEvidence> evidences = new HashSet<>();
                evidences.add(evidence);
                interaction.setExpEvidenceSet(evidences);
                keyToInteraction.put(key, interaction);
                interaction.setInteractionID(keyToInteraction.size());
            }
        }
        fu.close();
        Set<Source> interactionSource = Collections.singleton(getDrugCentralSource());
        Collection<Interaction> interactions = keyToInteraction.values();
        interactions.forEach(i -> i.setInteractionSourceSet(interactionSource));
        return new ArrayList<>(interactions);
    }

    private ExpEvidence parseEvidence(String[] tokens) {
        ExpEvidence evidence = new ExpEvidence();
        // "ACT_VALUE" (7) "ACT_UNIT"  
        // "ACT_TYPE" (9) "ACT_COMMENT"   "ACT_SOURCE"    "RELATION"  "MOA"   "MOA_SOURCE" 
        // "ACT_SOURCE_URL" (15)   "MOA_SOURCE_URL" 
        // value in file is -log(affinity)
        if (tokens[7].length() > 0) {
            Double value = Math.pow(10, -Double.parseDouble(tokens[7]));
            // Convert to nM
            value /= 1.0E-9;
            // Want to keep three digits for Double here
            evidence.setAssayValueMedian(String.format("%.3G", value) + "");
            evidence.setAssayUnits("nM");
            evidence.setAssayType(tokens[9]);
        }
        evidence.setAssayRelation(tokens[12]);
        evidence.setAssayDescription(tokens[10]);
        // Ignore MOA for now
        // Get source
        Source source = getSource(tokens);
        evidence.setExpSourceSet(Collections.singleton(source));
        return evidence;
    }
    
    private void removeQuotationMarks(String[] tokens) {
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].startsWith("\"")) {
                // Remove the token
                int length = tokens[i].length();
                tokens[i] = tokens[i].substring(1, length - 1);
            }
        }
    }
    
    private Source getSource(String[] tokens) {
        // ACT_SOURCE (11)   RELATION    MOA MOA_SOURCE  ACT_SOURCE_URL (14)  MOA_SOURCE_URL
        String key = tokens[11];
        if (key.length() == 0)
            throw new IllegalArgumentException("ACT_SOURCE IS EMPTY!");
        if (tokens[14].length() > 0)
            key += "\t" + tokens[14];
        else if (tokens[15].length() > 0)
            key += "\t" + tokens[15];
        Source source = keyToSource.get(key);
        if (source != null)
            return source;
        source = new Source();
        keyToSource.put(key, source);
        source.setSourceID(keyToSource.size());
        
        if (tokens[11].equals("SCIENTIFIC LITERATURE")) {
            String pubmedId = getPubMedId(tokens);
            LitEvidence litEvidence = new LitEvidence();
            litEvidence.setPubMedID(pubmedId);
            source.setSourceLiterature(litEvidence);
            litEvidence.setLitID(source.getSourceID());
            source.setSourceDatabase(getDrugCentral());
        }
        else { // Should be some database
            DatabaseRef dbRef = new DatabaseRef();
            dbRef.setDatabaseName(tokens[11]);
            dbRef.setDownloadURL(getUrl(tokens));
            source.setSourceDatabase(dbRef);
            dbRef.setDatabaseID(source.getSourceID());
        }
        return source;
    }
    
    private DatabaseRef getDrugCentral() {
        if (drugCentral != null)
            return drugCentral;
        drugCentral = new DatabaseRef();
        drugCentral.setDatabaseID(0);
        drugCentral.setDatabaseName("DrugCentral");
        return drugCentral;
    }
    
    private Source getDrugCentralSource() {
        if (drugCentralSource != null)
            return drugCentralSource;
        drugCentralSource = new Source();
        drugCentralSource.setSourceID(keyToSource.size());
        drugCentralSource.setSourceDatabase(getDrugCentral());
        return drugCentralSource;
    }
    
    private String getPubMedId(String[] tokens) {
        String url = getUrl(tokens);
        if (url == null)
            return null;
        int index = url.lastIndexOf("/");
        return url.substring(index + 1);
    }

    protected String getUrl(String[] tokens) {
        String url = null;
        if (tokens[15].length() > 0) 
            url = tokens[15];
        else if (tokens[16].length() > 0) 
            url = tokens[16];
        if (url == null)
            return null;
        return url;
    }
    
    private Set<Target> getTargets(String[] tokens) {
        // "TARGET_NAME" (2)   "TARGET_CLASS"  
        // "ACCESSION" "GENE"  "SWISSPROT"
        // "ORGANISM" (19)
        String[] acces = tokens[4].split("\\|");
        String[] genes = tokens[5].split("\\|");
        Set<Target> targets = new HashSet<>();
        for (int i = 0; i < acces.length; i++) {
            Target target = accToTarget.get(acces[i]);
            if (target == null) {
                target = new Target();
                target.setTargetName(genes[i]);
                target.setUniprotID(acces[i]);
                target.setTargetType(tokens[3]);
                if (tokens.length > 19)
                    target.setTargetSpecies(tokens[19]);
                accToTarget.put(acces[i], target);
                target.setTargetID(accToTarget.size());
            }
            targets.add(target);
        }
        return targets;
    }
    
    private Drug getDrug(String[] tokens) {
        Drug drug = nameToDrug.get(tokens[0]);
        if (drug != null)
            return drug;
        // "DRUG_NAME" "STRUCT_ID"
        drug = new Drug();
        String name = tokens[0];
        // Upcase the first letter
        String drugName = name.substring(0, 1).toUpperCase() + name.substring(1);
        drug.setDrugName(drugName);
        drug.setDrugID(new Integer(tokens[1])); // Use STRUCT_ID as the drug_id
        nameToDrug.put(name,  drug);
        return drug;
    }

}
