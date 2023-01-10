package org.reactome.r3.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.reactome.funcInt.FIAnnotation;
import org.reactome.r3.util.FileUtility;

/**
 * This class is used to handle TF/target interaction data downloaded from https://github.com/saezlab/dorothea:
 * The tab delimited files for mouse and human are exported from the R workspace in the github repo.
 * @author wug
 *
 */
public class DorotheaIntearctionService {
    
    private final static Logger logger = Logger.getLogger(DorotheaIntearctionService.class);
    private Map<String, FIAnnotation> mouseFIToAnnot;
    private Map<String, FIAnnotation> humanFIToAnnot;
    private String mouseFileName;
    private String humanFileName;
    
    public DorotheaIntearctionService() {
    }
    
    public List<FIAnnotation> annotateMouseInteractions(Collection<String> interactions) {
        return annotateInteractions(interactions, mouseFIToAnnot);
    }
    
    public List<FIAnnotation> annotateHumanInteractions(Collection<String> interactions) {
        return annotateInteractions(interactions, humanFIToAnnot);
    }
    
    private List<FIAnnotation> annotateInteractions(Collection<String> interactions,
                                                    Map<String, FIAnnotation> fiToAnnot) {
        return interactions.stream()
                           .map(i -> fiToAnnot.get(i))
                           .filter(a -> a != null)
                           .collect(Collectors.toList());
    }
    
    public Set<String> getMouseInteractions() {
        return mouseFIToAnnot.keySet();
    }
    
    public Set<String> getHumanInteractions() {
        return humanFIToAnnot.keySet();
    }
    
    public void setMouseFile(String fileName) {
        this.mouseFileName = fileName;
        mouseFIToAnnot = new HashMap<>();
        loadData(fileName, mouseFIToAnnot);
    }
    
    public void setHumanFile(String fileName) {
        this.humanFileName = fileName;
        humanFIToAnnot = new HashMap<>();
        loadData(fileName, humanFIToAnnot);
    }
    
    public List<String> loadInteractions(String species,
                                         char[] confidence) throws IOException {
        String fileName = null;
        if (species.equals("human"))
            fileName = humanFileName;
        else if (species.equals("mouse"))
            fileName = mouseFileName;
        if (fileName == null)
            throw new IllegalArgumentException(species + " is not supported.");
        // Handle confidence
        Set<String> confidenceSet = new HashSet<>();
        for (char c : confidence)
            confidenceSet.add(c + "");
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        String line = fu.readLine();
        List<String> rtn = new ArrayList<>();
        rtn.add(line);
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            if (confidenceSet.contains(tokens[1]))
                rtn.add(line);
        }
        fu.close();
        return rtn;
    }
    
    private void loadData(String fileName,
                          Map<String, FIAnnotation> fiToAnnot) {
        try {
            FileUtility fu = new FileUtility();
            fu.setInput(fileName);
            String line = fu.readLine(); 
            while ((line = fu.readLine()) != null) {
                String[] tokens = line.split("\t");
                FIAnnotation annot = new FIAnnotation();
                // Here we abuse the id by pushing gene pairs here
                String interaction = tokens[0] + "\t" + tokens[2];
//                if (fiToAnnot.containsKey(interaction))
//                    throw new IllegalStateException(interaction + " is duplicated!");
                annot.setInteractionId(interaction);
                if (tokens[3].equals("1")) {
                    annot.setAnnotation("activate");
                    annot.setDirection("->");
                }
                else if (tokens[3].equals("-1")) {
                    annot.setAnnotation("inhibit");
                    annot.setDirection("-|");
                }
                else if (tokens[3].equals("0")) {
                    annot.setAnnotation("unknown");
                    annot.setDirection("-");
                }
                // It is possible an interaction may have more than one annotation
                FIAnnotation preAnnot = fiToAnnot.get(interaction);
                if (preAnnot == null)
                    fiToAnnot.put(interaction, annot);
                else 
                    mergeAnnotation(annot, preAnnot);
            }
            fu.close();
        }
        catch(Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
    
    private void mergeAnnotation(FIAnnotation source, FIAnnotation target) {
        // Handle annotation types: activate, inhibit, and unknown
        Set<String> targetTypes = Stream.of(target.getAnnotation().split(",")).collect(Collectors.toSet());
        targetTypes.add(source.getAnnotation());
        target.setAnnotation(targetTypes.stream().sorted().collect(Collectors.joining(",")));
        // Handle directions types: ->, -|, -
        if (source.getDirection().equals("->")) {
            if (target.getDirection().equals("-|"))
                target.setDirection("-"); // Don't know the direction any more
            else if (target.getDirection().equals("-"))
                target.setDirection("->");
        }
        else if (source.getDirection().equals("-|")) {
            if (target.getDirection().equals("->"))
                target.setDirection("-");
            else if (target.getDirection().equals("-"))
                target.setDirection("-|");
        }
    }

}
