/*
 * Created on Oct 2, 2013
 *
 */
package org.reactome.r3.fi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.gk.util.StringUtils;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Test;

/**
 * A set of JUnit tests to test the RESTful APIs used for the FI plugins.
 * @author gwu
 *
 */
public class FINetworkResourceTests {
//    private final String HOST_URL = "http://localhost:8080/caBigR3WebApp/FIService";
    private final String HOST_URL = "http://localhost:8080/corews/FIService";
//    private final String HOST_URL = "http://cpws.reactome.org/caBigR3WebApp2019/FIService";
//    private final String HOST_URL = "http://54.236.18.108/corews/FIService";
    private final String NETWORK_URL = HOST_URL + "/network/";
    private final String DRUG_URL = HOST_URL + "/drug/";
//    private String drugDataSource = "targetome";
    private String drugDataSource = "drugcentral";
    private final String REST_URL = HOST_URL + "/ReactomeRestful/";
    private final String CGI_URL = HOST_URL + "/cancerGeneIndex/";
//    private final String URL = "http://reactomews.oicr.on.ca:8080/caBigR3WebApp2014/FIService/network/";
    private final String HTTP_POST = "Post";
    private final String HTTP_GET = "Get";
    
    public FINetworkResourceTests() {
    }
    
    @Test
    public void testCluster() throws Exception {
        String fileName = "/Users/wug/Desktop/test.txt";
        String url = NETWORK_URL + "cluster";
        StringBuilder builder = new StringBuilder();
        Files.lines(Paths.get(fileName))
        .forEach(line -> {
            String[] tokens = line.split("\t");
            if (tokens.length != 3) {
                System.out.println(line);
                return;
            }
            builder.append(tokens[0]).append("\t");
            int compare = tokens[1].compareTo(tokens[2]);
            if (compare < 0) {
                builder.append(tokens[1]).append("\t");
                builder.append(tokens[2]).append("\n");
            }
            else {
                builder.append(tokens[2]).append("\t");
                builder.append(tokens[1]).append("\n");
            }
        });
        System.out.println(builder.toString());
        String result = callHttp(url, HTTP_POST, builder.toString());
        System.out.println(result);
    }
    
    @Test
    public void testCGI() throws Exception {
        String url = CGI_URL + "queryAnnotations";
        String query = "EGFR";
        String text = callHttp(url, HTTP_POST, query);
        prettyPrintXML(text);
    }
    
    @Test
    public void testAnnotateFIs() throws Exception {
        String url = NETWORK_URL + "annotate";
        String fis = "100\tAKT1S1\tAKT3\n101\tAKT3\tAKT1S1";
//        fis = "101\tAKT3\tAKT1S1";
        String text = callHttp(url, HTTP_POST, fis);
        prettyPrintXML(text);
    }
    
    @Test
    public void testFrontPageItems() throws Exception {
        String url = REST_URL + "frontPageItems/Homo+sapiens";
        String text = callHttp(url, HTTP_GET, "");
        System.out.println("url:\n" + text);
    }
    
    @Test
    public void testBuildNetwork() throws Exception {
        String genes = "TP53\tPTEN\tEGFR";
        genes = "DKK3\tNBN\tMYO6\tTP53\tPML\tIFI16\tBRCA1";
        String url = NETWORK_URL + "buildNetwork";
        String text = callHttp(url, HTTP_POST, genes);
        System.out.println("Built Network: ");
        prettyPrintXML(text);
    }
    
    @Test
    public void testAnnotateGeneSetWithReactomePathways() throws Exception {
        String genes = "ACBD3, CNIH3, CNIH4, DEGS1, DNAH14, ENAH, EPHX1, H3F3A, H3F3AP4, LBR, LEFTY1, LEFTY2, LIN9, MIR320B2, MIR4742, MIXL1, NVL, PYCR2, SDE2, SRP9, TMEM63A, WDR26";
        StringBuilder builder = new StringBuilder();
        String[] tokens = genes.split(",( )?");
        System.out.println("Total genes: " + tokens.length);
        for (String token : tokens)
            builder.append(token).append("\n");
        String url = NETWORK_URL + "annotateGeneSetWithReactomePathways";
        String text = callHttp(url, HTTP_POST, builder.toString());
        prettyPrintXML(text);
    }
    
    @Test
    public void testConvertPathwayToFIs() throws Exception {
        String url = NETWORK_URL + "convertPathwayToFIs/8878159";
        System.out.println(url);
        String text = callHttp(url, HTTP_GET, "");
        prettyPrintXML(text);
    }
    
    @Test
    public void testConvertPathwayToBooleanNetwork() throws Exception {
        Long dbId = 5621481L;
        String url = NETWORK_URL + "convertPathwayToBooleanNetwork/" + dbId;
        String text = callHttp(url, HTTP_GET, null);
        System.out.println("Size of returned text: " + text.length());
        prettyPrintXML(text);
        
        System.out.println("\nconvertPathwayToBooleanNetworkViaPost:");
        dbId = 1257604L; // PIP3 activates AKT signaling
        url = NETWORK_URL + "convertPathwayToBooleanNetworkViaPost";
        String query = dbId + "\nEGFR";
        text = callHttp(url, HTTP_POST, query);
        prettyPrintXML(text);
    }
    
    @Test
    public void testConvertPathwayToFactorGraph() throws Exception {
        String url = NETWORK_URL + "convertPathwayToFactorGraph/69620";
        String[] escapeNames = new String[] {
                "ATP",
                "ADP",
                "Pi",
                "H2O",
                "GTP",
                "GDP",
                "CO2",
                "H+"
        };
        List<String> escapeList = Arrays.asList(escapeNames);
        String namesForList = StringUtils.join(",", escapeList);
        String text = callHttp(url, HTTP_POST, namesForList);
        System.out.println("Size of returned text: " + text.length());
        prettyPrintXML(text);
    }
    
    @Test
    public void testKSTest() throws Exception {
        StringBuilder builder = new StringBuilder();
        List<Double> list1 = new ArrayList<Double>();
        for (int i = 0; i < 100; i++)
            list1.add(Math.random());
        builder.append(StringUtils.join(",", list1));
        List<Double> list2 = new ArrayList<Double>();
        for (int i = 0; i < 100; i++)
            list2.add(Math.random());
        builder.append(";");
        builder.append(StringUtils.join(",", list2));
        // Check using Gaussian
        Random random = new Random();
        list2.clear();
        for (int i = 0; i < 100; i++)
            list2.add(random.nextGaussian());
        builder.append(" ");
        builder.append(StringUtils.join(",", list1));
        builder.append(";").append(StringUtils.join(",", list2));
        String url = NETWORK_URL + "ksTest";
        String text = callHttp(url, HTTP_POST, builder.toString());
        System.out.println(text);
    }
    
    @Test
    public void testQueryFIsForEdges() throws Exception {
        String url = NETWORK_URL + "queryEdges";
        StringBuilder query = new StringBuilder();
//        query.append("PIK3R1\tRB1");
        query.append("EGF\tEGFR");
//        query.append("\nATM\tTP53");
//        query.append("\nCDC25A\tCHEK2");
        String text = callHttp(url, HTTP_POST, query.toString());
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryPathwayFIsForSources() throws Exception {
        String url = NETWORK_URL + "queryPathwayFIsSources";
        StringBuilder query = new StringBuilder();
        query.append("EGF\tEGFR");
        query.append("\nATM\tTP53");
        query.append("\nCDC25A\tCHEK2");
        String text = callHttp(url, HTTP_POST, query.toString());
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryDorotheaFIs() throws Exception {
        String url = NETWORK_URL + "fetchDorotheaFIs/mouse";
        System.out.println("URL: " + url);
        String text = callHttp(url, HTTP_GET, null);
        System.out.println(text);
        url = NETWORK_URL + "fetchDorotheaFIs/human";
        System.out.println("URL: " + url);
        text = callHttp(url, HTTP_GET, null);
        System.out.println(text);
        // For mouse annotation
        String fis = "Hoxb8\tPkdrej\nHes5\tIfit3b\nDmap1\tC8g";
        url = NETWORK_URL + "annotateDorotheaFIs/mouse";
        text = callHttp(url, HTTP_POST, fis);
        System.out.println("URL: " + url);
        System.out.println(text);
        // For human annotation
        fis = "ZBTB5\tLIN7C\nTBX6\tMKS1\nPAX7\tDNAJC1";
        url = NETWORK_URL + "annotateDorotheaFIs/human";
        text = callHttp(url, HTTP_POST, fis);
        System.out.println("URL: " + url);
        System.out.println(text);
    }
    
    @Test
    public void tetsGetMouseToHumanGeneMap() throws Exception {
        String url = NETWORK_URL + "mouse2HumanGeneMap";
        System.out.println("URL : " + url);
        String text = callHttp(url, HTTP_GET, null);
        System.out.println(text);
    }
    
    @Test
    public void testGetGenesInPE() throws Exception {
        String url = NETWORK_URL + "getGenesInPE/187750";
        System.out.println("URL: " + url);
        String text = callHttp(url, HTTP_GET, "");
        System.out.println("Genes in PE: " + text);
        // A complex EntitySet containing two dimers
        url = NETWORK_URL + "getGenesInPE/205013";
        text = callHttp(url, HTTP_GET, "");
        System.out.println("Genes in PE (should be CASP2|CASP3): " + text);
    }
    
    @Test
    public void testQueryFIsForPEInPathwayDiagram() throws Exception {
        // A PE in Diagram of Cell Cycle Checkpoints
        String url = NETWORK_URL + "queryFIsForPEInPathwayDiagram/451785/176229";
        String text = callHttp(url, HTTP_GET, "");
        System.out.println("FIs for PE 176229:\n");
        prettyPrintXML(text);
        // A gene contained by the above complex
        url = NETWORK_URL + "queryFIsForPEInPathwayDiagram/451785/176282";
        text = callHttp(url, HTTP_GET, "");
        System.out.println("FIs for PE 176282:\n");
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryReactomeInstance() throws Exception {
        String dbId = "6815559";
//        Long dbId = "109581";
        String url = NETWORK_URL + "queryReactomeInstance/" + dbId;
        String text = callHttp(url, HTTP_GET, null);
        System.out.println("queryReactomeInstance:");
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryPathwayDiagram() throws Exception {
    	String url = NETWORK_URL + "queryPathwayDiagram";
    	String text = callHttp(url, HTTP_POST, "5368287");
    	System.out.println("Diagram: " + text);
    }
    
    @Test
    public void testGetGenesToPEIdsInPathway() throws Exception {
        // Query for Diagram of Cell Cycle Checkpoints
        String url = NETWORK_URL + "getGeneToIdsInPathwayDiagram/451785";
        String text = callHttp(url, HTTP_GET, null);
        System.out.println("Genes to PE DB_IDs:");
        prettyPrintXML(text);
    }
    
    @Test
    public void testGetGeneToEWASIds() throws Exception {
        String url = NETWORK_URL + "getGeneToEWASIds";
        String query = "68756,68730";
        String text = callHttp(url, HTTP_POST, query);
        System.out.println("Query for " + query + ":");
        prettyPrintXML(text);
    }
    
    @Test
    public void testFIsForEdges() throws Exception {
        String url = NETWORK_URL + "queryEdge";
        String edge = "EGF\tEGFR";
        String text = callHttp(url, HTTP_POST, edge);
        System.out.println("Query for " + edge + ":");
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryDrugTargetInteractions() throws Exception {
        String url = DRUG_URL + "queryDrugTargetInteractions/" + drugDataSource;
        String query = "EGFR\nESR1\nBRAF";
        String text = callHttp(url, HTTP_POST, query);
        System.out.println("Query for " + url + ": ");
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryDrugInteractionsForPEInDiagram() throws Exception {
        String url = DRUG_URL + "queryInteractionsForPEInDiagram/" + drugDataSource + "/507988/1220578"; // EGFR signaling pathway diagram
        String text = callHttp(url, HTTP_GET, null);
        System.out.println("Query for " + url + ":");
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryDrugInteractionsForDiagram() throws Exception {
        String url = DRUG_URL + "queryInteractionsForDiagram/" + drugDataSource + "/507988"; // EGFR signaling pathway diagram
        String text = callHttp(url, HTTP_GET, null);
        System.out.println("Query for " + url + ":");
        prettyPrintXML(text);
    }
    
    @Test
    public void testListDrugs() throws Exception {
        String url = DRUG_URL + "listDrugs/" + drugDataSource;
        String text = callHttp(url, HTTP_GET, null);
        System.out.println("Query for " + url + ":");
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryInteractionsForDrug() throws Exception {
        String drugName = "Aldesleukin\nBosutinib";
        drugName = "Gefitinib";
        String url = DRUG_URL + "queryInteractionsForDrugs/" + drugDataSource;
        String text = callHttp(url, HTTP_POST, drugName);
        System.out.println("Query for " + url + ":");
        prettyPrintXML(text);
    }
    
    @Test
    public void testQueryDrugInteractionsForPEs() throws Exception {
        String url = DRUG_URL + "queryInteractionsForPEs/" + drugDataSource;
        String query = "350052\n1604465";
        query = "350052";
        String rtn = callHttp(url, HTTP_POST, query);
        System.out.println("Query for " + url + ":");
        prettyPrintXML(rtn);
    }
    
    @Test
    public void testPerformDrugImpactAnalysis() throws Exception {
        String drug = "Imatinib Mesylate";
        drug = "Gefitinib";
//        drugDataSource = "targetome";
        String url = DRUG_URL + "performImpactAnalysis/" + drugDataSource;
        System.out.println(url);
        String rtn = callHttp(url, HTTP_POST, drug);
        System.out.println(rtn);
    }
    
    private void prettyPrintXML(String xml) throws JDOMException, IOException {
        //      System.out.println(xml);
        // Check if it is an XML
        if (xml.startsWith("<?xml")) {
            SAXBuilder builder = new SAXBuilder();
            Reader reader = new StringReader(xml);
            Document doc = builder.build(reader);
            XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
            outputter.output(doc, System.out);
        }
        else {
            //          FileUtilities fu = new FileUtilities();
            //          fu.setOutput("tmp.txt");
            //          fu.printLine(xml);
            //          fu.close();
            System.out.println(xml);
        }
    }
    
    private String callHttp(String url,
                            String type,
                            String query) throws IOException {
        HttpMethod method = null;
        HttpClient client = null;
        if (type.equals(HTTP_POST)) {
            method = new PostMethod(url);
            client = initializeHTTPClient((PostMethod) method, query);
        } else {
            method = new GetMethod(url); // Default
            client = new HttpClient();
        }
        method.setRequestHeader("Accept", "text/plain, application/xml");
//              method.setRequestHeader("Accept", "application/json");
        int responseCode = client.executeMethod(method);
        if (responseCode == HttpStatus.SC_OK) {
            InputStream is = method.getResponseBodyAsStream();
            return readMethodReturn(is);
        } else {
            System.err.println("Error from server: " + method.getResponseBodyAsString());
            System.out.println("Response code: " + responseCode);
            throw new IllegalStateException(method.getResponseBodyAsString());
        }
    }
    
    private String readMethodReturn(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null)
            builder.append(line).append("\n");
        reader.close();
        isr.close();
        is.close();
        // Remove the last new line
        String rtn = builder.toString();
        // Just in case an empty string is returned
        if (rtn.length() == 0)
            return rtn;
        return rtn.substring(0, rtn.length() - 1);
    }
    
    private HttpClient initializeHTTPClient(PostMethod post, String query) throws UnsupportedEncodingException {
        RequestEntity entity = new StringRequestEntity(query, "text/plain", "UTF-8");
//        RequestEntity entity = new StringRequestEntity(query, "application/XML", "UTF-8");
        post.setRequestEntity(entity);
//        post.setRequestHeader("Accept", "application/JSON, application/XML, text/plain");
              post.setRequestHeader("Accept", "application/json");
        HttpClient client = new HttpClient();
        return client;
    }
    
}
