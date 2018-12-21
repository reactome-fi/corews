package org.reactome.r3.service;

import java.util.Collection;
import java.util.List;

import edu.ohsu.bcb.druggability.dataModel.Drug;
import edu.ohsu.bcb.druggability.dataModel.Interaction;

public interface DrugDAO {
    public List<Drug> listDrugs();
    public List<Interaction> queryInteractions(Collection<String> genes);
    public List<Interaction> queryInteractions(Collection<String> genes,
                                               String drug);
    public List<Interaction> queryInteractions(String gene);
    public List<Interaction> queryInteractionsForDrugs(String[] drugNames);
}
