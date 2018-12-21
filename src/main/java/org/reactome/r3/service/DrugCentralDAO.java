package org.reactome.r3.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import edu.ohsu.bcb.druggability.dataModel.Drug;
import edu.ohsu.bcb.druggability.dataModel.Interaction;

/**
 * This class is used to load DrugCentral drug/target interactions directly
 * from a tsv file download from the drugcentral web site.
 * @author wug
 */
public class DrugCentralDAO implements DrugDAO {
    private static final Logger logger = Logger.getLogger(DrugCentralDAO.class);
    private String fileName;
    private List<Interaction> interactions;
    
    public DrugCentralDAO() {
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    private void loadInteractions() {
        if (fileName == null)
            throw new IllegalStateException("The source file has not specfiied!");
        File file = new File(fileName);
        if (!file.exists())
            throw new IllegalStateException(fileName + " doesn't exist!");
        try {
            interactions = new DrugCentralFileParser().parseFile(file);
        }
        catch(IOException e) {
            logger.error("DrugCentralDAO.loadInteractions(): " + e, e);
        }
    }

    @Override
    public List<Drug> listDrugs() {
        if (interactions == null)
            loadInteractions();
        Set<Drug> drugs = interactions.stream().map(i -> i.getIntDrug()).collect(Collectors.toSet());
        return new ArrayList<>(drugs);
    }

    @Override
    public List<Interaction> queryInteractions(Collection<String> genes) {
        if (interactions == null)
            loadInteractions();
        List<Interaction> rtn = interactions.stream()
                .filter(i -> genes.contains(i.getIntTarget().getTargetName()))
                .collect(Collectors.toList());
        return rtn;
    }

    @Override
    public List<Interaction> queryInteractions(Collection<String> genes, String drug) {
        if (interactions == null)
            loadInteractions();
        List<Interaction> rtn = interactions.stream()
                .filter(i -> i.getIntDrug().getDrugName().equals(drug))
                .filter(i -> genes.contains(i.getIntTarget().getTargetName()))
                .collect(Collectors.toList());
        return rtn;
    }

    @Override
    public List<Interaction> queryInteractions(String gene) {
        if (interactions == null)
            loadInteractions();
        List<Interaction> rtn = interactions.stream()
                .filter(i -> i.getIntTarget().getTargetName().equals(gene))
                .collect(Collectors.toList());
        return rtn;
    }

    @Override
    public List<Interaction> queryInteractionsForDrugs(String[] drugNames) {
        if (interactions == null)
            loadInteractions();
        Set<String> drugs = Arrays.asList(drugNames).stream().collect(Collectors.toSet());
        List<Interaction> rtn = interactions.stream()
                .filter(i -> drugs.contains(i.getIntDrug().getDrugName()))
                .collect(Collectors.toList());
        return rtn;
    }
    
}
