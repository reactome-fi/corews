/*
 * Created on Jun 18, 2010
 *
 */
package org.reactome.r3.fi;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.util.StringUtils;
import org.reactome.annotate.AnnotationType;
import org.reactome.annotate.GeneSetAnnotation;
import org.reactome.annotate.ModuleGeneSetAnnotation;
import org.reactome.annotate.PathwayBasedAnnotator;
import org.reactome.booleannetwork.BooleanNetwork;
import org.reactome.factorgraph.FactorGraph;
import org.reactome.funcInt.FIAnnotation;
import org.reactome.funcInt.Interaction;
import org.reactome.pagerank.HotNetResult;
import org.reactome.pathway.booleannetwork.PathwayToBooleanNetworkConverter;
import org.reactome.r3.fi.ReactomeObjectHandler.GeneInDiagramToGeneToPEIds;
import org.reactome.r3.fi.ReactomeObjectHandler.GeneToPEIds;
import org.reactome.r3.graph.GeneClusterPair;
import org.reactome.r3.graph.NetworkBuilderForGeneSet;
import org.reactome.r3.graph.NetworkClusterResult;
import org.reactome.r3.graph.SpectralPartitionNetworkCluster;
import org.reactome.r3.model.ReactomeInstance;
import org.reactome.r3.service.FIServiceUtilities;
import org.reactome.r3.service.HibernateInteractionDAO;
import org.reactome.r3.service.InteractionService;
import org.reactome.r3.util.InteractionUtilities;
import org.springframework.context.annotation.Scope;

@Path("/network")
@Scope("Singleton") // single object instance per web application
public class FINetworkResource {
    private static final Logger logger = org.apache.log4j.Logger.getLogger(FINetworkResource.class);
    private InteractionAnnotator fiAnnotator;
    private PathwayBasedAnnotator pathwayAnnotator;
    private InteractionService interactionService;
    private HibernateInteractionDAO hibernateDAO;
    private NetworkBuilderForGeneSet networkBuilder;
    private PathwayDiagramHandler diagramHandler;
    private KEGGHelper keggHelper;
    private SurvivalAnalysisHelper survivalAnalysisHelper;
    // For doing MCL network clustering
    private MCLClusteringHelper mclHelper;
    // Set a size cutoff for building network with linker to avoid a very long process
    private Integer networkBuildSizeCutoff;
    // For encode TF/target related queries
    private EncodeTFTargetInteractionQuery encodeTFTargetQuery;
    // For HotNet mutation analysis
    private HotNetAnalysisHelper hotNetHelper;
    // For Pathway and FIs converting
    private PathwayToFIsConverter pathwayToFIsConverter;
    // For Reactome related simple object handling
    private ReactomeObjectHandler reactomeObjectHandler;
    // Used to process factor graph related stuff
    private FactorGraphFacade factorGraphHelper;
    // For doing some R related calculation using JRI
    private RengineWrapper rEngineWrapper;
    // For converting to pathways to boolean networks
    private PathwayToBooleanNetworkConverter bnConverter;
    // For human mouse gene mapper
    private HumanMouseGeneMapper humanMouseGeneMapper;
    
    public FINetworkResource() {
    }
    
    public HumanMouseGeneMapper getHumanMouseGeneMapper() {
        return humanMouseGeneMapper;
    }

    public void setHumanMouseGeneMapper(HumanMouseGeneMapper humanMouseGeneMapper) {
        this.humanMouseGeneMapper = humanMouseGeneMapper;
    }

    public void setBooleanNetworkConverter(PathwayToBooleanNetworkConverter converter) {
        this.bnConverter = converter;
    }
    
    public PathwayToBooleanNetworkConverter getBooleanNetworkConverter() {
        return this.bnConverter;
    }

    public FactorGraphFacade getFactorGraphHelper() {
        return factorGraphHelper;
    }
    
    public RengineWrapper getrEngineWrapper() {
        return rEngineWrapper;
    }

    public void setrEngineWrapper(RengineWrapper rEngineWrapper) {
        this.rEngineWrapper = rEngineWrapper;
    }

    public void setFactorGraphHelper(FactorGraphFacade fgHelper) {
        this.factorGraphHelper = fgHelper;
    }

    public ReactomeObjectHandler getReactomeObjectHandler() {
        return reactomeObjectHandler;
    }

    public void setReactomeObjectHandler(ReactomeObjectHandler reactomeObjectHandler) {
        this.reactomeObjectHandler = reactomeObjectHandler;
    }

    public void setHotNetHelper(HotNetAnalysisHelper helper) {
        this.hotNetHelper = helper;
    }
    
    public HotNetAnalysisHelper getHotNetHelper() {
        return this.hotNetHelper;
    }
    
    public EncodeTFTargetInteractionQuery getEncodeTFTargetQuery() {
        return encodeTFTargetQuery;
    }

    public void setEncodeTFTargetQuery(EncodeTFTargetInteractionQuery encodeTFTargetQuery) {
        this.encodeTFTargetQuery = encodeTFTargetQuery;
    }

    public MCLClusteringHelper getMclHelper() {
        return mclHelper;
    }

    public void setMclHelper(MCLClusteringHelper mclHelper) {
        this.mclHelper = mclHelper;
    }

    public SurvivalAnalysisHelper getSurvivalAnalysisHelper() {
        return survivalAnalysisHelper;
    }

    public void setSurvivalAnalysisHelper(SurvivalAnalysisHelper survivalAnalysisHelper) {
        this.survivalAnalysisHelper = survivalAnalysisHelper;
    }

    public void setKeggHelper(KEGGHelper helper) {
        this.keggHelper = helper;
    }
    
    public KEGGHelper getKeggHelper() {
        return this.keggHelper;
    }
    
    public void setDiagramHandler(PathwayDiagramHandler handler) {
        this.diagramHandler = handler;
    }
    
    public void setInteractionService(InteractionService service) {
        this.interactionService = service;
    }
    
    public InteractionService getInteractionService() {
        return this.interactionService;
    }
    
    public void setHibernateDAO(HibernateInteractionDAO dao) {
        this.hibernateDAO = dao;
    }
    
    public HibernateInteractionDAO getHibernateDAO() {
        return this.hibernateDAO;
    }
    
    public void setNetworkBuilder(NetworkBuilderForGeneSet builder) {
        this.networkBuilder = builder;
    }
    
    public void setNetworkBuildSizeCutoff(int size) {
        this.networkBuildSizeCutoff = size;
    }
    
    public PathwayToFIsConverter getPathwayToFIsConverter() {
        return pathwayToFIsConverter;
    }

    public void setPathwayToFIsConverter(PathwayToFIsConverter pathwayToFIConverter) {
        this.pathwayToFIsConverter = pathwayToFIConverter;
    }
    
    @Path("mouse2HumanGeneMap")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getMouseToHumaGeneMap() {
        try {
            Map<String, Set<String>> map = humanMouseGeneMapper.getMouseToHumanMap();
            StringBuilder builder = new StringBuilder();
            map.forEach((key, set) -> {
                set.forEach(hg -> builder.append(key + "\t" + hg + "\n"));
            });
            return builder.toString();
        }
        catch(IOException e) {
            logger.error(e.getMessage(), e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Path("/encodeTFTarget/query/{accession}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getEncodeTFTargetInteractions(@PathParam("accession") String accession) {
        return encodeTFTargetQuery.getInteractionsInMITab(accession);
    }
    
    @Path("/networkBuildSizeCutoff")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getNetworkBuildlSizeCutoff() {
        return this.networkBuildSizeCutoff + "";
    }
    
    /**
     * This method is used to construct a functional interaction network for a set of genes.
     * @param genes
     * @return
     * @throws Exception
     */
    @Path("/queryFIs")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Interaction> queryFIs(String query) throws Exception {
        List<Interaction> interactions = interactionService.queryForAnd(query);
        return interactions;
    }
    
    /**
     * This method is used to query FIs for a PE that is specified by its DB_ID.
     * @param peDBId
     * @return
     * @throws Exception
     */
    @Path("/queryFIsForPEInPathwayDiagram/{pdId}/{peId}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<GeneInDiagramToGeneToPEIds> queryFIsForPEInPathwayDiagram(@PathParam("pdId") Long pdId,
                                                                          @PathParam("peId") Long peId) throws Exception {
        String genes = reactomeObjectHandler.getContainedGenesInPE(peId);
        List<Interaction> interactions = interactionService.queryForOr(genes);
        // Remove FIs that can be extracted from the displayed PathwayDiagram.
        // Since we want to show extra information by overlaying FIs, there is
        // no need to overlay FIs that can be extracted from the displayed pathway diagram.
        // Otherwise, there are too many unnecessary FIs. For example, a complex is involved
        // in multiple association. FIs for the first complex may be extracted later on.
        List<Interaction> pdFIs = pathwayToFIsConverter.convertPathwayToFIs(pdId);
        FIServiceUtilities.filterFIs(interactions, pdFIs);
        return reactomeObjectHandler.convertFIsForPEinDiagram(pdId, 
                                                              genes,
                                                              interactions,
                                                              fiAnnotator);
    }
    
    /**
     * This method is used to send all FIs to the client.
     */
    @Path("/queryAllFIs")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String fetchAllFIs() throws Exception {
        Set<String> fis = interactionService.loadAllFIs();
        StringBuilder builder = new StringBuilder();
        for (String fi : fis)
            builder.append(fi).append("\n");
        return builder.toString();
    }
    
    /**
     * This method is used to query FIs between two gene sets.
     * @param query
     * @return
     * @throws Exception
     */
    @Path("/queryFIsBetween")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Interaction> queryFIsBetween(String query) throws Exception {
        String[] lines = query.split("\n");
        if (lines.length < 2) {
            logger.error("Wrong parameter in queryFIsBetween: " + query);
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
        List<Interaction> interactions = interactionService.queryFIs(lines[0], lines[1]);
        return interactions;
    }
    
    /**
     * Check if any gene or protein names are matched in a set of entity DB_IDs. There are two lines in the
     * query String: the first line is a common-delimited PE entity ids for checking, and the second line is 
     * a list of gene/protein names delimited by common.
     * @param query
     * @return
     */
    @Path("/pathwayDiagram/highlight")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String checkMatchEntityIds(String query) {
        String[] lines = query.split("\n");
        if (lines[0].length() == 0)
            return ""; // In case there is nothing displayed
        String[] dbIds = lines[0].split(",");
        String[] geneNames = lines[1].split(",");
        try {
            Set<String> matched =  diagramHandler.checkMatchEntityIds(dbIds, 
                                                                      geneNames,
                                                                      fiAnnotator.getSourceDBA());
            return InteractionUtilities.joinStringElements(",", matched);
        }
        catch(Exception e) {
            logger.error("Error in checkMatchEntityIds: " + query,
                         e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Get detailed information (e.g. Reactome data source and scores) of FIs by using this method.
     * @param query
     * @return
     * @throws Exception
     */
    @Path("/queryEdge")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Interaction> queryFIsForEdge(String query) throws Exception {
        // Get source and target from edge
        String[] tokens = query.split("\t");
        List<Interaction> interactions = hibernateDAO.queryOnNames(tokens[0], tokens[1]);
        return interactions;
    }
    
    /**
     * Get detailed information (e.g. Reactome data source and scores) of FIs by using this method.
     * @param query
     * @return
     * @throws Exception
     */
    @Path("/queryEdges")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Interaction> queryFIsForEdges(String query) throws Exception {
        String[] fis = query.split("\n");
        if (fis.length == 0)
            return new ArrayList<Interaction>();
        Set<Interaction> set = new HashSet<Interaction>();
        for (String fi : fis) {
            // Get source and target from edge
            String[] tokens = fi.split("\t");
            List<Interaction> interactions = hibernateDAO.queryOnNames(tokens[0], tokens[1]);
            set.addAll(interactions);
        }
        return new ArrayList<Interaction>(set);
    }
    
    /**
     * Get the Reactome DB_IDs for a set of FIs. This method has not been used by
     * ReactomeFIViz and was implemented for other people.
     * @param query
     * @return
     * @throws Exception
     */
    @Path("/queryPathwayFIsSources")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String queryPathwayFIsSources(String query) throws Exception {
        String[] fis = query.split("\n");
        if (fis.length == 0)
            return "";
        Map<String, Set<Long>> fiToSourceIds = fiAnnotator.queryPathwayFIsSources(fis);
        if (fiToSourceIds == null || fiToSourceIds.size() == 0)
            return "";
        StringBuilder builder = new StringBuilder();
        for (String fi : fiToSourceIds.keySet()) {
            Set<Long> sourceIds = fiToSourceIds.get(fi);
            builder.append(fi + "\t");
            for (Long sourceId : sourceIds)
                builder.append(sourceId).append(",");
            // Remove the last ","
            builder.deleteCharAt(builder.length() - 1);
            builder.append("\n");
        }
        return builder.toString();
    }
    
    /**
     * Get genes contained in a PE specificied by its DB_ID.
     * @param peId
     * @return formatted as following: gene1, gene2, gene3 | gene4. This is a gene list for a
     * complex contains three subunits, gene1, gene2, and an EntitySet contains gene3 and gene4.
     * @throws Exception
     */
    @Path("/getGenesInPE/{peId}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getGenesInPE(@PathParam("peId") Long peId) throws Exception {
        return reactomeObjectHandler.getContainedGenesInPE(peId);
    }
    
    /**
     * Construct a FI network for the queried genes. Linker genes should be used if any gene is not linked
     * to each other.
     * @param query
     * @return
     * @throws Exception
     */
    @Path("/buildNetwork")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Interaction> buildFINetwork(String query) throws Exception {
        String[] accessions = FIServiceUtilities.splitQuery(query);
        Set<String> queryGenes = new HashSet<String>();
        if (networkBuildSizeCutoff != null && networkBuildSizeCutoff <= queryGenes.size())
            throw new WebApplicationException(Status.BAD_REQUEST);
        for (String id : accessions)
            queryGenes.add(id);
        Set<String> set = networkBuilder.constructFINetworkForGeneSet(queryGenes);
        return FIServiceUtilities.convertFIsToInteractions(set);
    }
    
    /**
     * Convert a Pathway specified by its DB_ID to a list of Interactions.
     * @param pathwayId pathway DB_ID
     */
    @Path("/convertPathwayToFIs/{pathwayId}")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<Interaction> convertPathwayToFIs(@PathParam("pathwayId") Long pathwayId) throws Exception {
        if (pathwayToFIsConverter == null)
            return new ArrayList<Interaction>();
        return pathwayToFIsConverter.convertPathwayToFIs(pathwayId);
    }
    
    /**
     * Convert a pathway specified by its DB_ID into a FactorGraph object.
     * @param pathwayId
     * @namesForList a list of names for entities to be escaped. Names are delimited by ",".
     * @return
     * @throws Exception
     */
    @Path("/convertPathwayToFactorGraph/{pathwayId}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public FactorGraph convertPathwayToFactorGraph(@PathParam("pathwayId") Long pathwayId,
                                                   String namesForList) throws Exception {
        GKInstance pathway = pathwayToFIsConverter.getMySQLAdaptor().fetchInstance(pathwayId);
        String[] names = namesForList.split(",");
        return factorGraphHelper.convertToFactorGraph(pathway,
                                                      Arrays.asList(names));
    }
    
    /**
     * Convert a pathway specified by its DB_ID into a BooleanNetwork object.
     * @param pathwayId
     * @return
     * @throws Exception
     */
    @Path("/convertPathwayToBooleanNetwork/{pathwayId}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public BooleanNetwork convertPathwayToBooleanNetwork(@PathParam("pathwayId") Long pathwayId) throws Exception {
        GKInstance pathway = pathwayToFIsConverter.getMySQLAdaptor().fetchInstance(pathwayId);
        return bnConverter.convert(pathway);
    }
    
    /**
     * Convert a pathway specified by its DB_ID into a BooleanNetwork object and a list of focused gene names.
     * @param pathwayId
     * @return
     * @throws Exception
     */
    @Path("/convertPathwayToBooleanNetworkViaPost")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public BooleanNetwork convertPathwayToBooleanNetworkViaPost(String query) throws Exception {
        String[] lines = query.split("\n");
        Long pathwayId = new Long(lines[0]); // This should be guaranteed
        List<String> genes = null;
        if (lines.length > 1) {
            String[] tokens = lines[1].split(",");
            genes = Arrays.asList(tokens);
        }
        GKInstance pathway = pathwayToFIsConverter.getMySQLAdaptor().fetchInstance(pathwayId);
        return bnConverter.convert(pathway, genes);
    }
    
    /**
     * Perform two-samples and two-sided Kolmogorov-Smirnov test.
     * @param multiple pairs of double arrays can be used. The format should be like the following:
     * v11,v12...;v21,v22...\nw11,w12,...;w21,w22,... (Pairs of double arrays are separated by a space,
     * and two arrays inside a pair are delimited by semi-comma, and elements in an array are separated
     * by comma).
     * @return
     */
    @Path("/ksTest")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String performKolmogorovSmirnovTest(String values) {
        String[] tokens = values.split("\n");
        List<Double> rtn = new ArrayList<Double>();
        for (String token : tokens) {
            String[] tokens1 = token.split(";");
            if (tokens.length < 2) {
                rtn.add(null);
                continue;
            }
            List<Double> list1 = new ArrayList<Double>();
            String[] tokens2 = tokens1[0].split(",");
            for (String token2 : tokens2)
                list1.add(new Double(token2));
            List<Double> list2 = new ArrayList<Double>();
            tokens2 = tokens1[1].split(",");
            for (String token2 : tokens2)
                list2.add(new Double(token2));
            Double pvalue = rEngineWrapper.kolmogorovSmirnovTest(list1, list2);
            rtn.add(pvalue);
        }
        return StringUtils.join(",", rtn);
    }
    
    /**
     * Get a list of gene to PE ids for a pathway specified by its DB_ID.
     * @param annotator
     */
    @Path("/getGeneToIdsInPathwayDiagram/{pathwayDiagramId}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<GeneToPEIds> getGeneToIdsInPathwayDiagram(@PathParam("pathwayDiagramId") Long pathwayDiagramId) throws Exception {
        if (reactomeObjectHandler == null)
            return new ArrayList<ReactomeObjectHandler.GeneToPEIds>();
        return reactomeObjectHandler.getGeneToIdsInPathwayDiagram(pathwayDiagramId);
    }
    
    /**
     * Get a list of gene to EWAS ids for a list of DB_IDs delimited by "," in a String object.
     * @param dbIds
     * @return
     * @throws Exception
     */
    @Path("/getGeneToEWASIds")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<GeneToPEIds> getGeneToEWASIds(String dbIds) throws Exception {
        if (reactomeObjectHandler == null)
            return new ArrayList<ReactomeObjectHandler.GeneToPEIds>();
        return reactomeObjectHandler.getGeneToEWASIds(dbIds);
    }
    
    public void setFIAnnotator(InteractionAnnotator annotator) {
        this.fiAnnotator = annotator;
    }
    
    public InteractionAnnotator getFIAnnotator() {
        return this.fiAnnotator;
    }
    
    public void setPathwayAnnotator(PathwayBasedAnnotator annotator) {
        this.pathwayAnnotator = annotator;
    }
    
    public PathwayBasedAnnotator getPathwayAnnotator() {
        return this.pathwayAnnotator;
    }
    
    /**
     * Annotate FIs using pathway information.
     * @param queryFIs
     * @throws Exception
     */
    @Path("/annotate")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<FIAnnotation> annotateFIs(String queryFIs) {
        try {
            String[] lines = queryFIs.split("\n");
            Map<String, String[]> idToNames = new HashMap<String, String[]>();
            for (String line : lines) {
                if (line.length() == 0)
                    continue; // Just in case
                String[] tokens = line.split("\t");
                idToNames.put(tokens[0],
                              new String[]{tokens[1], tokens[2]});
            }
            //return annotations;
            List<FIAnnotation> rtn = fiAnnotator.annotate(idToNames);
            return rtn;
        }
        catch(Exception e) {
            logger.error("Error in annotating: \n" + e.getMessage(),
                         e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Path("/queryKEGGPathwayId")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String queryKEGGPathwayId(String pathwayName) {
        return keggHelper.getPathwayId(pathwayName);
    }

    @Path("/queryKEGGGeneIds")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String queryKEGGGeneIds(String query) {
        String[] geneNames = FIServiceUtilities.splitQuery(query);
        return keggHelper.getGeneIds(geneNames);
    }
    
    @Path("/queryReactomeInstance/{dbId}")
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ReactomeInstance queryReactomeInstance(@PathParam("dbId") Long dbId) {
        try {
            ReactomeInstance rtn = reactomeObjectHandler.queryInstance(dbId);
            return rtn;
        }
        catch(Exception e) {
            logger.error("queryReactomeInstance for " + dbId, e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Path("/queryPathwayId")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String queryPathwayID(String pathwayName) {
        // Get rid of the last three letters: database source
        String queryName = pathwayName.substring(0, pathwayName.length() - 3);
        MySQLAdaptor dba = fiAnnotator.getSourceDBA();
        try {
            Collection<?> collection = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Pathway,
                                                                    ReactomeJavaConstants._displayName,
                                                                    "=", 
                                                                    queryName);
            if (collection.size() == 1) {
                GKInstance pathway = (GKInstance) collection.iterator().next();
                return pathway.getDBID().toString();
            }
            else if (collection.size() > 1) {
                String querySrcLetter = pathwayName.substring(pathwayName.length() - 2, pathwayName.length() - 1);
                // Need to find the matched data source
                for (Iterator<?> it = collection.iterator(); it.hasNext();) {
                    GKInstance pathway = (GKInstance) it.next();
                    // Interesting with human species only
                    GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
                    // 48887 is the fixed DB_ID for homo sapiens in Reactome
                    if (species != null && !species.getDBID().equals(48887L))
                        continue;
                    if (pathway.getSchemClass().isValidAttribute(ReactomeJavaConstants.dataSource)) {
                        GKInstance dataSource = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.dataSource);
                        String sourceLetter = InteractionUtilities.getPathwayDBSourceLetter(dataSource);
                        if (sourceLetter.equals(querySrcLetter))
                            return pathway.getDBID().toString();
                    }
                }
            }
            else // Cannot find!
                throw new WebApplicationException(Status.NOT_FOUND);
        }
        catch(Exception e) {
            logger.error("Error in queryPathwayID: " + pathwayName,
                       e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
        // Should not get here
        throw new WebApplicationException(Status.NOT_FOUND);
    }
    
    /**
     * The passed query (pathwayName) may be a pathway name or DB_ID.
     * @param query
     * @return
     */
    @Path("/queryPathwayDiagram")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_XML)
    public String queryPathwayDiagram(String query) {
        try {
            String idString = null;
            if (query.matches("(\\d)+")) // If it is a number
                idString = query;
            else
                idString = queryPathwayID(query);
            GKInstance pathway = fiAnnotator.getSourceDBA().fetchInstance(new Long(idString));
            String xml = diagramHandler.queryDiagramXML(pathway);
            return xml;
        }
        catch(Exception e) {
            logger.error("Error in queryPathwayID: " + query,
                       e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * This method is used to cluster a FI network based on Spectral based network clustering
     * algorithm. Make sure FIs in the query text should be order alphabetically as the following:
     * gene1\tgene2\ngene2\tgene3. Gene1 should be alalphatiecaly lower than gene2.
     * @param queryFIs
     * @return
     */
    @Path("/cluster")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public NetworkClusterResult cluster(String queryFIs) {
        // Re-load Fis
        Set<String> fis = new HashSet<String>();
        String[] lines = queryFIs.split("\n");
        for (String line : lines) {
            String[] tokens = line.split("\t");
            // Note: the first token is edge id
            String node1 = tokens[1];
            String node2 = tokens[2]; 
            fis.add(node1 + "\t" + node2);
        }
        SpectralPartitionNetworkCluster clusterEngine = new SpectralPartitionNetworkCluster();
        List<Set<String>> clusters = clusterEngine.cluster(fis);
        List<GeneClusterPair> geneClusterPairs = new ArrayList<GeneClusterPair>();
        for (int i = 0; i < clusters.size(); i++) {
            for (String gene : clusters.get(i)) {
                GeneClusterPair pair = new GeneClusterPair();
                pair.setGeneId(gene);
                pair.setCluster(i);
                geneClusterPairs.add(pair);
            }
        }
        double modularity = clusterEngine.calculateModualarity(clusters,
                                                               fis);
        NetworkClusterResult rtn = new NetworkClusterResult();
        rtn.setClsName(clusterEngine.getClass().getName());
        rtn.setModularity(modularity);
        rtn.setGeneClusterPairs(geneClusterPairs);
        return rtn;
    }
    
    /**
     * This method is used to annotate network modules using pathways and GO terms.
     */
    @Path("/annotateGeneSet/{type}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<ModuleGeneSetAnnotation> annotateGeneSet(String query,
                                                         @PathParam("type") String type) {
        Set<String> geneSet = new HashSet<String>();
        String[] lines = query.split("\n");
        for (String line : lines)
            geneSet.add(line);
        List<ModuleGeneSetAnnotation> rtn = new ArrayList<ModuleGeneSetAnnotation>();
        try {
            AnnotationType annotationType = AnnotationType.valueOf(type);
            List<GeneSetAnnotation> annotations = pathwayAnnotator.annotateGenesWithFDR(geneSet,
                                                                                        annotationType);
            ModuleGeneSetAnnotation moduleAnnotation = new ModuleGeneSetAnnotation();
            moduleAnnotation.setAnnotations(annotations);
            moduleAnnotation.setIds(geneSet);
            rtn.add(moduleAnnotation);
            return rtn;
        }
        catch(Exception e) {
            logger.error("Error in annotating: \n" + query,
                       e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * This method is used to annotate a set of reactions provided as DB_IDs.
     */
    @Path("/annotateReactions")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<ModuleGeneSetAnnotation> annotateReactions(String query) {
        Set<String> reactionIds = new HashSet<String>();
        String[] lines = query.split("\n");
        for (String line : lines)
            reactionIds.add(line);
        List<ModuleGeneSetAnnotation> rtn = new ArrayList<ModuleGeneSetAnnotation>();
        try {
            List<GeneSetAnnotation> annotations = pathwayAnnotator.annotateReactionsWithReactomePathways(reactionIds);
            ModuleGeneSetAnnotation moduleAnnotation = new ModuleGeneSetAnnotation();
            moduleAnnotation.setAnnotations(annotations);
            moduleAnnotation.setIds(reactionIds);
            rtn.add(moduleAnnotation);
            return rtn;
        }
        catch(Exception e) {
            logger.error("Error in annotating: \n" + query,
                       e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Use this method to annotate a set of genes using all Reactome pathways, which are organized
     * in a hierarchical way.
     * @param query
     * @return
     */
    @Path("/annotateGeneSetWithReactomePathways/{species}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<ModuleGeneSetAnnotation> annotateGeneSetWithReactomePathways(String query,
                                                                             @PathParam("species") String species) {
        Set<String> geneSet = new HashSet<String>();
        String[] lines = query.split("\n");
        for (String line : lines)
            geneSet.add(line);
        try {
            String decoded = URLDecoder.decode(species, "utf-8");
            List<GeneSetAnnotation> annotations = null;
            if (decoded.equals("Mus musculus"))
                annotations = pathwayAnnotator.annotateMouseGenesWithReactomePathways(geneSet);
            else 
                annotations = pathwayAnnotator.annotateGenesWithReactomePathways(geneSet);
            ModuleGeneSetAnnotation moduleAnnotation = new ModuleGeneSetAnnotation();
            moduleAnnotation.setAnnotations(annotations);
            moduleAnnotation.setIds(geneSet);
            List<ModuleGeneSetAnnotation> rtn = new ArrayList<ModuleGeneSetAnnotation>();
            rtn.add(moduleAnnotation);
            return rtn;
        }
        catch(Exception e) {
            logger.error("Error in annotateWithReactomePathways:\n" + query, 
                         e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * This method is used to do a R-based survival analysis.
     * @param query
     * @param model
     * @param module
     * @return
     */
    @Path("/survivalAnalysis/{model}/{module}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public SurvivalAnalysisResult doSurvivalAnalysis(String query,
                                     @PathParam("model") String model,
                                     @PathParam("module") String module) {
        try {
            if (module.equals("null"))
                module = null;
            return survivalAnalysisHelper.doSurvivalAnalysis(query, model, module);
        }
        catch(Exception e) {
            // Avoid to print out the query, which should be very long!
            logger.error("Error in doSurvivalAnalysis: \n" + e.getMessage(),
                         e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * This method is used to do HotNet based mutation analysis.
     * @param query
     * @return
     */
    @Path("/hotnetAnalysis")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public HotNetResult doHotNetAnalysis(String query) {
        // Parse the query
        String[] tokens = query.split("\n");
        Map<String, Double> geneToScore = new HashMap<String, Double>();
        // The following are special lines
//        builder.append("delta:" + delta);
//        builder.append("fdrCutoff:" + fdrCutoff);
//        builder.append("permutationNumber:" + permutation);
        Double delta = null;
        Double fdrCutoff = null;
        Integer permutationNumber = null;
        for (String token : tokens) {
            if (token.contains(":")) {
                int index = token.indexOf(":");
                String key = token.substring(0, index);
                String value = token.substring(index + 1);
                if (key.equals("delta")) {
                    if (!value.equals("null"))
                        delta = new Double(value);
                }
                else if (key.equals("fdrCutoff")) {
                    if (!value.equals("null"))
                        fdrCutoff = new Double(value);
                }
                else if (key.equals("permutationNumber")) {
                    if (!value.equals("null"))
                        permutationNumber = new Integer(value);
                }
            }
            else {
                int index = token.indexOf("\t");
                String gene = token.substring(0, index);
                String score = token.substring(index + 1);
                geneToScore.put(gene, new Double(score));
            }
        }
        try {
            HotNetResult result = hotNetHelper.doHotNetAnalysis(geneToScore, 
                                                                delta, 
                                                                fdrCutoff, 
                                                                permutationNumber);
            return result;
        }
        catch(Exception e) {
            logger.error("Error in doHotNetAnalysis: \n" + e.getMessage(), e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * This method is used to MCL clustering for a set of FIs.
     * @param query contains two parts: the first one should be FIs (\t) weights,
     * the second part should be parameters (inflation), which starts with "inflation:".
     * @return
     */
    @Path("/mclClustering")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public MCLClusteringResult doMCLClustering(String query) {
        // Need to parse the returned query
        String[] tokens = query.split("\n");
        Set<String> fisWithCorrs = new HashSet<String>();
        for (int i = 0; i < tokens.length - 1; i++)
            fisWithCorrs.add(tokens[i]);
        // Last line should be inflation
        String lastLine = tokens[tokens.length - 1];
        int index = lastLine.indexOf(":");
        double inflation = new Double(lastLine.substring(index + 1).trim());
        try {
            MCLClusteringResult result = mclHelper.cluster(fisWithCorrs, inflation);
            return result;
        }
        catch(Exception e) {
            logger.error("Error in doMCLClustering:\n" + e.getMessage(), e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * This method is used to annotate network modules using pathways and GO terms.
     */
    @Path("/annotateModules/{type}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public List<ModuleGeneSetAnnotation> annotateModules(String query,
                                                         @PathParam("type") String type) {
        // Recover the original modules
        Map<String, Integer> geneToModule = new HashMap<String, Integer>();
        String[] lines = query.split("\n");
        for (String line : lines) {
            String[] tokens = line.split("\t");
            geneToModule.put(tokens[0], new Integer(tokens[1]));
        }
        Map<Integer, Set<String>> clusterToGenes = new HashMap<Integer, Set<String>>();
        for (String gene : geneToModule.keySet()) {
            Integer module = geneToModule.get(gene);
            InteractionUtilities.addElementToSet(clusterToGenes, module, gene);
        }
        List<ModuleGeneSetAnnotation> rtn = new ArrayList<ModuleGeneSetAnnotation>();
        try {
            for (Integer module : clusterToGenes.keySet()) {
                Set<String> genes = clusterToGenes.get(module);
                AnnotationType annotationType = AnnotationType.valueOf(type);
                List<GeneSetAnnotation> annotations = pathwayAnnotator.annotateGenesWithFDR(genes,
                                                                                            annotationType);
                ModuleGeneSetAnnotation moduleAnnotation = new ModuleGeneSetAnnotation();
                moduleAnnotation.setAnnotations(annotations);
                moduleAnnotation.setIds(genes);
                moduleAnnotation.setModule(module);
                rtn.add(moduleAnnotation);
            }
            return rtn;
        }
        catch(Exception e) {
            logger.error("Error in annotating: \n" + query,
                         e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }
    
}
