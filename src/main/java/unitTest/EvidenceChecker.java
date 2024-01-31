package unitTest;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.reactome.funcInt.Evidence;

/**
 * Use methods in this class to generate a list of evidence propertiest that are used by config.prop at ReactomeFIViz.
 * @author wug
 *
 */
public class EvidenceChecker {

    public EvidenceChecker() {
    }
    
    @Test
    public void checkEvidence() throws Exception {
        Class<Evidence> evidence = Evidence.class;
        Field[] fields = evidence.getDeclaredFields();
        System.out.println("Total declared fields: " + fields.length);
        Map<String, String> name2field = new HashMap<>();
        for (Field field : fields) {
            if (field.getName().equals("dbId"))
                continue; // Escape this
            String fieldName = field.getName();
            String displayName = null;
            if (fieldName.startsWith("TCGA") || fieldName.startsWith("GTEx") || fieldName.equals("score")) {
                displayName = fieldName;
            }
            else if (fieldName.endsWith("PPI")) {
                // Split into two parts
                int index = fieldName.indexOf("PPI");
                displayName = fieldName.substring(index) + "_" + upFirst(fieldName.substring(0, index));
            }
            else if (fieldName.equals("domainInteractions")) {
                displayName = "pFAM_Domain_Interaction";
            }
            else if (fieldName.equals("gOBPSharing")) {
                displayName = "GO_BP_Sharing";
            }
            else { // Harmonizome
                displayName = "Harmonizome_GeneSimilarity_" + fieldName;
            }
            name2field.put(displayName, fieldName);
        }
        name2field.keySet().stream().sorted().forEach(name -> System.out.println(name + "\t" + name2field.get(name)));
        // Generate text for ReactomeFIViz's config
        StringBuilder names = new StringBuilder();
        StringBuilder props = new StringBuilder();
        name2field.keySet().stream().sorted().forEach(name -> {
            names.append(name).append(",");
            props.append(name2field.get(name)).append(",");
        });
        names.deleteCharAt(names.length() - 1);
        props.deleteCharAt(props.length() - 1);
        System.out.println(names.toString());
        System.out.println(props.toString());
    }
    
    private String upFirst(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
    
}
