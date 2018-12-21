package org.reactome.r3.fi;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.gk.model.GKInstance;
import org.reactome.pathway.booleannetwork.AffinityToModificationMap;
import org.reactome.pathway.booleannetwork.BNPerturbationAnalyzer;
import org.reactome.pathway.booleannetwork.DefaultAffinityToModificationMap;
import org.reactome.pathway.booleannetwork.DrugTargetInteractionTypeMapper;
import org.reactome.pathway.booleannetwork.DrugToTargetsMapper;
import org.reactome.pathway.booleannetwork.ModificationType;
import org.reactome.pathway.booleannetwork.PathwayImpactAnalysisResult;
import org.reactome.pathway.booleannetwork.PathwayToBooleanNetworkConverter;
import org.reactome.r3.service.DrugDAO;

import edu.ohsu.bcb.druggability.dataModel.ExpEvidence;
import edu.ohsu.bcb.druggability.dataModel.Interaction;
import edu.ohsu.bcb.druggability.dataModel.Target;

/**
 * This class is used to perform a systematic pathway impact data analysis for cancer drugs.
 * @author wug
 *
 */
public class PathwayDrugImpactAnalyzer {
    private ReactomeObjectHandler reactomeHandler;
    private PathwayToBooleanNetworkConverter converter;
    
    public PathwayDrugImpactAnalyzer() {
    }
    
    public ReactomeObjectHandler getReactomeHandler() {
        return reactomeHandler;
    }

    public void setReactomeHandler(ReactomeObjectHandler reactomeHandler) {
        this.reactomeHandler = reactomeHandler;
    }

    public PathwayToBooleanNetworkConverter getConverter() {
        return converter;
    }

    public void setConverter(PathwayToBooleanNetworkConverter converter) {
        this.converter = converter;
    }

    /**
     * Perform a systematic pathway impact analysis for a specified drug. Currently it
     * support one drug only.
     * @param drug
     * @return
     */
    public String performImpactAnalysis(String drug, DrugDAO drugDAO) throws Exception {
        List<GKInstance> pathways = reactomeHandler.loadPathwayList();
        BNPerturbationAnalyzer analyzer = new BNPerturbationAnalyzer();
        DrugToTargetsMapper mapper = getDrugTargetsMapper(drugDAO);
        StringBuilder builder = new StringBuilder();
        for (GKInstance pathway : pathways) {
            PathwayImpactAnalysisResult results = analyzer.performDrugImpactAnalysis(pathway,
                                                                converter,
                                                                drug,
                                                                mapper);
            if (results != null)
                builder.append(results.toString()).append("\n");
        }
        return builder.toString();
    }
    
    private DrugToTargetsMapper getDrugTargetsMapper(DrugDAO drugDAO) {
        DrugToTargetsMapper mapper = new DrugToTargetsMapper() {
            private DrugTargetInteractionTypeMapper typeMapper = new DrugTargetInteractionTypeMapper();
            
            @Override
            public Set<String> getDrugTargets(String drug) throws Exception {
                List<Interaction> interactions = drugDAO.queryInteractionsForDrugs(new String[]{drug});
                return interactions.stream()
                                   .filter(i -> getMinValue(i) != null)
                                   .map(i -> i.getIntTarget().getTargetName())
                                   .filter(name -> (name != null))
                                   .collect(Collectors.toSet());
            }
            
            @Override
            public Map<String, Double> getGeneToInhibition(Collection<String> genes, String drug) throws Exception {
                List<Interaction> interactions = drugDAO.queryInteractions(genes, drug);
                Map<String, Double> geneToValue = new HashMap<>();
                AffinityToModificationMap affToModMap = new DefaultAffinityToModificationMap();
                interactions.forEach(interaction -> {
                    Double minValue = getMinValue(interaction);
                    if (minValue == null)
                        return;
                    ModificationType type = typeMapper.getModificationType(interaction.getInteractionType());
                    if (type == ModificationType.Inhibition || type == ModificationType.None) {
                        Target target = interaction.getIntTarget();
                        Double strength = affToModMap.getModificationStrenth(minValue);
                        geneToValue.put(target.getTargetName(), strength);
                    }
                });
                return geneToValue;
            }
            
            @Override
            public Map<String, Double> getGeneToActivation(Collection<String> genes, String drug) throws Exception {
                List<Interaction> interactions = drugDAO.queryInteractions(genes, drug);
                Map<String, Double> geneToValue = new HashMap<>();
                AffinityToModificationMap affToModMap = new DefaultAffinityToModificationMap();
                interactions.forEach(interaction -> {
                    Double minValue = getMinValue(interaction);
                    if (minValue == null)
                        return;
                    ModificationType type = typeMapper.getModificationType(interaction.getInteractionType());
                    if (type == ModificationType.Activation) {
                        Target target = interaction.getIntTarget();
                        Double strength = affToModMap.getModificationStrenth(minValue);
                        geneToValue.put(target.getTargetName(), strength);
                    }
                });
                return geneToValue;
            }
        };
        return mapper;
    }
    
    /**
     * Use this method to pick up whatever type of minimum value
     * @param interaction
     * @return
     */
    private Double getMinValue(Interaction interaction) {
        Double rtn = null;
        if (interaction.getExpEvidenceSet() == null)
            return rtn;
        for (ExpEvidence evidence : interaction.getExpEvidenceSet()) {
            if (evidence.getAssayValueMedian() == null ||
                evidence.getAssayValueMedian().trim().length() == 0 ||
                evidence.getAssayType() == null)
                continue;
            Number current = evidence.getAssayValue();
            if (current == null)
                continue;
            if (rtn == null || current.doubleValue() < rtn.doubleValue())
                rtn = current.doubleValue();
        }
        return rtn;
    }

}
