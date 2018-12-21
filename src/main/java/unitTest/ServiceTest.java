/*
 * Created on Oct 13, 2006
 *
 */
package unitTest;

import java.util.List;

import junit.framework.TestCase;

import org.apache.log4j.PropertyConfigurator;
import org.reactome.funcInt.Interaction;
import org.reactome.r3.service.InteractionService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class ServiceTest extends TestCase {
    
    protected void setUp() throws Exception {
        PropertyConfigurator.configure("WebContent/WEB-INF/log4j.properties");
    }
    
    public void testQueryForUniProts() {
        ApplicationContext context = new FileSystemXmlApplicationContext("WebContent/WEB-INF/ClientApplicationContext.xml");
        InteractionService service = (InteractionService) context.getBean("client");
        String proteins = "P61254, P61513, P55795, P62424, P62910, P46781, P63220";
        List<Interaction> interactions = service.queryForAnd(proteins);
        System.out.println("Found Interactions: " + interactions.size());
    }
//    
//    public void testXFireWSForWSDL() throws Exception {
//        URL url = new URL("http://brie8.cshl.edu:8080/caBigR3WebApp/FIService?wsdl");
//        Client client = new Client(url);
//        Object[] results = client.invoke("queryForAnd", new Object[] {"Q9UII8, Q9Y5E9"});
//        Document doc = (Document) results[0];
//        XMLOutputter output = new XMLOutputter(Format.getPrettyFormat());
//        org.jdom.Document jdomDoc = new DOMBuilder().build(doc);
//        String fileName = "/Users/guanming/Documents/tmp/Interactions.xml";
//        FileOutputStream fos = new FileOutputStream(fileName);
//        output.output(jdomDoc, fos);
//    }
//    
//    public void testXFireExample() throws Exception {
//        URL url = new URL("http://localhost:8080/xfire-spring-example-1.2.2/EchoService?wsdl");
//        Client client = new Client(url);
//        Object[] results = client.invoke("echo", new Object[]{"Hello!"});
//        System.out.println("results: " + results);
//    }
//    
//    public void testXFireWSForService() throws Exception {
//        Service serviceModel = new ObjectServiceFactory().create(InteractionService.class);
//        InteractionService service = (InteractionService) new XFireProxyFactory().create(serviceModel, 
//        "http://localhost:8080/caBigR3WebApp/FIService");
//        List<Interaction> interactions = service.queryForOr("Q9UII8, Q9Y5E9"); 
//        System.out.println("Interactions: " + interactions.size());
//    }
//    
//    public void checkForNewInteractions() throws Exception {
//        String ids = "Q9UII8, Q9Y5E9, P55289, Q9Y6N8, Q9HBT6, Q9Y5G1, Q9HC56, Q16248, P35222, Q9UN71," +
//        " P55287, Q5QGS1, Q9BZA7, Q9Y5H8, P61160, Q8N5B3, Q9UN73, Q9Y5I1, Q5XKN2, Q9Y5F6, " +
//        "Q5VVX0, Q96QU1, Q6NTE3, Q9Y5E8, P60709, Q9H159, P26232, Q9NYQ7, Q9Y5F8, Q7Z3Y0, " +
//        "Q9BZA8, Q9Y5F0, Q8N173, Q8N5Z2, P55283, Q9Y5E3, Q9NYQ8, Q9Y5I2, P22223, Q96JQ0," +
//        " Q9Y5H5, P62736, Q08174, Q12864, Q7Z3L1, Q9Y5F7, Q9HCL0, Q9H158, Q9HCU4, Q7Z738, " +
//        "Q9UN75, Q5R3A8, Q5T8M7, Q9Y5H1, Q9H349, P68133, Q9NRJ7, Q9UN74, Q9Y5F9, P63261, " +
//        "P12931, P19022, Q5R3A6, P33151, Q8N5D7, P21860, P42685, Q9Y5E7, Q5VTE1, Q6V1P9, " +
//        "Q9BYE9, P68032, P12830, Q5VT82, Q9BYX7, Q9Y5E4, Q9P2E7, Q6UW70, Q9NPG4, Q9UII7, " +
//        "P04626, O60330, Q96T98, Q9Y5G9, Q9H251, Q8IY78, Q8IVQ1, P35221, Q9Y5F3, Q9UN66, " +
//        "Q86UP0, P55291, Q6P152, Q9Y5G8, Q5VVW9, Q8TAB3, Q5T8M8, Q9Y5H7, Q8IXY5, Q9UN67, " +
//        "Q9Y5H6, Q9Y5G3, Q5VTE4, Q9Y5F2, Q9Y5G5, O95206, Q8IY98, P63267, Q14517, Q9Y5H0, " +
//        "Q96SF0, Q9Y5G4, Q9Y5I3, Q9UI47, Q9Y5E1, P06241, Q9Y5H2, Q5VY39, Q9Y5I4, O75309, " +
//        "Q9Y5G6, Q13634, Q9Y5E5, Q9Y5I0, Q9NYQ6, Q9UN72, Q9Y5H4, P55286, P55285, P55290, " +
//        "Q86UD2, O60245, Q9ULB5, Q9Y5G7, Q15303, Q9Y5G0, O14917, Q9Y5E6, Q96CZ9, Q9Y5H3, " +
//        "Q8IUP2, Q9Y5G2, Q8IXH8, Q9Y5E2, Q76P87, Q96TA0, Q9Y5H9, Q6P4R2, Q9UN70, Q9Y5F1, " +
//        "Q9ULB4, Q9UJ99, Q8IUP8";
//        String[] idArrays = ids.split(",");
//        String egfrId = "P00533";
//        Service serviceModel = new ObjectServiceFactory().create(InteractionService.class);
//        InteractionService service = (InteractionService) new XFireProxyFactory().create(serviceModel, 
//        "http://localhost:8080/caBigR3WebApp/FIService");
//        int total = 0;
//        for (String id : idArrays) {
//            List<Interaction> list = service.queryForAnd(egfrId + ", " + id);
//            Interaction i = list.get(0);
//            if (i.getEvidence() != null) // This should be new interaction
//                total ++;
//            break;
//        }
//        System.out.println("total: " + total);
//    }
//    
//    private String loadSet(String fileName) throws IOException {
//        FileReader fileReader = new FileReader(fileName);
//        BufferedReader reader = new BufferedReader(fileReader);
//        String line = null;
//        StringBuilder builder = new StringBuilder();
//        while ((line = reader.readLine()) != null) {
//            builder.append(line);
//            builder.append(",");
//        }
//        reader.close();
//        fileReader.close();
//        return builder.toString();
//    }
//    
//    public void checkInteractionsFromRandomFile() throws Exception {
//        String fileName = "/Users/guanming/Documents/gkteam/marcela/random95a.txt";
//        String query = loadSet(fileName);
//        Service serviceModel = new ObjectServiceFactory().create(InteractionService.class);
//        InteractionService service = (InteractionService) new XFireProxyFactory().create(serviceModel, 
//        "http://localhost:8080/caBigR3WebApp/FIService");
//        
//        List<Interaction> interactions = service.queryForAnd(query);
//        System.out.println("Interactions: " + interactions.size());
//    }
//    
//    public void checkRaffaellaResults() throws Exception {
//        String partners = "Q14146,P78346,Q99731,P40925,Q96E40,P30042,P41222,P21953,O43290,P10909," +
//        "Q96IT1,Q8WXD2,Q9Y5K5,Q9Y6I8,Q9HAU5,Q5SRQ3,Q86W21," +
//        "P68400,Q5TIG5,Q9UBT7,Q9NUY3,P21851,Q14247,Q86YI6," +
//        "Q96AG4,O15121,33598948,21361193,Q13546,Q9NVI7,P03971,P15941,Q9UJM3";
//        String egfrId = "P00533";
//        Service serviceModel = new ObjectServiceFactory().create(InteractionService.class);
//        InteractionService service = (InteractionService) new XFireProxyFactory().create(serviceModel, 
//        "http://localhost:8080/caBigR3WebApp/FIService");
//        String partnerArray[] = partners.split(",");
//        for (String partner : partnerArray) {
//            List<Interaction> list = service.queryForAnd(partner + "," + egfrId);
//            if (list.size() == 0)
//                System.out.println(partner + ": none");
//            else {
//                Interaction interaction = list.iterator().next();
//                Evidence evidence = interaction.getEvidence();
//                System.out.println(partner + ": " + evidence.getProbability() + " " +
//                                   "humanPPI: " + evidence.getHumanInteraction() + " " + 
//                                   "OrthoPPI: " + evidence.getOrthoInteraction() + " " + 
//                                   "YeastPPI: " + evidence.getYeastInteraction() + " " + 
//                                   "GeneExp: " + evidence.getGeneExp() + " " + 
//                                   "GOBP: " + evidence.getGoBPSemanticSimilarity());
//            }
//        }
//    }
//    
//    public void checkForInteractionNumbers() throws Exception {
//        Service serviceModel = new ObjectServiceFactory().create(InteractionService.class);
//        InteractionService service = (InteractionService) new XFireProxyFactory().create(serviceModel, 
//        "http://localhost:8080/caBigR3WebApp/FIService");
//        
//        String[] ids = new String[] {
//                "O43290",
//                "P10909",
//                "P21953",
//                "P30042",
//                "P40925",
//                "P41222",
//                "P78346",
//                "Q14146",
//                "Q8WXD2",
//                "Q96E40",
//                "Q96IT1",
//                "Q99731",
//                "Q9HAU5",
//                "Q9Y5K5",
//                "Q9Y6I8"
//        };
//        for (String id : ids) {
//            List<Interaction> interactions = service.queryForOr(id);
//            System.out.println(interactions.size());
//        }
//    }
//    
}
