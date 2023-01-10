package org.reactome.r3.fi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.reactome.r3.util.FileUtility;

/**
 * This class is used to create an orthologous map between human and mouse genes.
 * @author wug
 *
 */
public class HumanMouseGeneMapper {
    
    // This map file should be downloaded from http://www.informatics.jax.org/downloads/reports/HOM_MouseHumanSequence.rpt.
    private String mapFileName;

    public HumanMouseGeneMapper() {
    }
    
    public String getMapFileName() {
        return mapFileName;
    }

    public void setMapFileName(String mapFileName) {
        this.mapFileName = mapFileName;
    }

    public Map<String, Set<String>> getMouseToHumanMap() throws IOException {
        if (mapFileName == null)
            throw new IllegalStateException("mapFileName has not been specified.");
        FileUtility fu = new FileUtility();
        fu.setInput(mapFileName);
        String line = fu.readLine(); // The header
        Map<String, List<String>> idToSymbols = new HashMap<>();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            String speciesPlusGene = tokens[2] + "\t" + tokens[3];
            idToSymbols.compute(tokens[0], (key, list) -> {
                if (list == null)
                    list = new ArrayList<>();
                list.add(speciesPlusGene);
                return list;
            });
        }
        fu.close();
        Map<String, Set<String>> mouse2human = new HashMap<>();
        Set<String> mouseGenes = new HashSet<>();
        Set<String> humanGenes = new HashSet<>();
        for (String id : idToSymbols.keySet()) {
            List<String> list = idToSymbols.get(id);
            if (list.size() == 1)
                continue; // There is only one member. Just ignore.
            mouseGenes.clear();
            humanGenes.clear();
            list.forEach(s -> {
                String[] tokens = s.split("\t");
                if (tokens[0].equals("10090")) // Mouse taxon id
                    mouseGenes.add(tokens[1]);
                else if (tokens[0].equals("9606")) // Human taxon id
                    humanGenes.add(tokens[1]);
            });
            if (mouseGenes.size() == 0 || humanGenes.size() == 0)
                continue;
            mouseGenes.forEach(mg -> {
                mouse2human.compute(mg, (key, set) -> {
                    if (set == null)
                        set = new HashSet<>();
                    set.addAll(humanGenes);
                    return set;
                });
            });
        }
        return mouse2human;
    }
    
    @Test
    public void testGetMouseToHumanMap() throws IOException {
        mapFileName = "/Users/wug/datasets/MGI/HOM_MouseHumanSequence.rpt.txt";
        Map<String, Set<String>> mouse2humanMap = getMouseToHumanMap();
        System.out.println("Size of mouse2humanMap: " + mouse2humanMap.size());
    }
    
    @Test
    public void checkMapFile() throws IOException {
        String fileName = "/Users/wug/datasets/MGI/HOM_MouseHumanSequence.rpt.txt";
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        String line = fu.readLine();
        Map<String, List<String>> idToSymbols = new HashMap<>();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            String speciesPlusGene = tokens[2] + "\t" + tokens[3];
            idToSymbols.compute(tokens[0], (key, list) -> {
                if (list == null)
                    list = new ArrayList<>();
                list.add(speciesPlusGene);
                return list;
            });
        }
        fu.close();
        System.out.println("Size of idToSymbols: " + idToSymbols.size());
        
        // Do some cleaning
        for (String id : idToSymbols.keySet()) {
            List<String> list = idToSymbols.get(id);
            if (list.size() == 1)
                continue; // Nothing we can do
            // Check if we have two species here
            Set<String> species = list.stream()
                                      .map(s -> s.split("\t")[0])
                                      .distinct()
                                      .collect(Collectors.toSet());
            if (species.size() == 1)
                continue; // Nothing to do
            if (list.size() > 2)
                System.out.println(id + ": " + list);
        }
    }
}
