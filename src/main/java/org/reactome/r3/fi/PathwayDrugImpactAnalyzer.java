package org.reactome.r3.fi;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
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

import edu.ohsu.bcb.druggability.dataModel.Interaction;
import edu.ohsu.bcb.druggability.dataModel.Target;

/**
 * This class is used to perform a systematic pathway impact data analysis for cancer drugs.
 * @author wug
 *
 */
public class PathwayDrugImpactAnalyzer {
    private static final Logger logger = Logger.getLogger(PathwayDrugImpactAnalyzer.class);
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
            try {
                PathwayImpactAnalysisResult results = analyzer.performDrugImpactAnalysis(pathway,
                                                                                         converter,
                                                                                         drug,
                                                                                         mapper);
                if (results != null)
                    builder.append(results.toString()).append("\n");
            }
            catch(IllegalStateException e) {
                logger.error("Error in " + pathway.toString(), e);
            }
        }
        return builder.toString();
    }
    
    /**
     * Perform a systematic pathway hit analysis for a specified drug. Currently it
     * support one drug only.
     * @param drug
     * @return
     */
    public String performHitAnalysis(String drug, DrugDAO drugDAO) throws Exception {
        List<GKInstance> pathways = reactomeHandler.loadPathwayList();
        BNPerturbationAnalyzer analyzer = new BNPerturbationAnalyzer();
        DrugToTargetsMapper mapper = getDrugTargetsMapper(drugDAO);
        StringBuilder builder = new StringBuilder();
        for (GKInstance pathway : pathways) {
//            if (!pathway.getDBID().equals(8951664L))
//                continue;
            PathwayImpactAnalysisResult result = analyzer.performDrugHitAnalysis(pathway,
                                                                converter,
                                                                drug,
                                                                mapper);
            if (result != null) {
                builder.append(result.getDbId() + "\t" + 
                               result.getPathwayName() + "\t" + 
                               String.join(",", result.getTargetGenes()) + "\n");
            }
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
                                   .filter(i -> i.getMinAssayValue() != null)
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
                    Double rtn = interaction.getMinAssayValue();
                    Double minValue = rtn;
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
                    Double minValue = interaction.getMinAssayValue();
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
}
