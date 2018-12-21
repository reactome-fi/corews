/*
 * Created on Jun 29, 2012
 *
 */
package unitTest;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.reactome.r3.util.FileUtility;

/**
 * This class is used to analyze some logging information generating from RESTful API call.
 * @author gwu
 *
 */
public class LoggingAnalyzer {
    private final DateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a");
//    private final String dirName = "logs/062912";
    private final String dirName = "logs/083012";
    
    public LoggingAnalyzer() {
        
    }
    
    @Test
    public void checkCallingTimes() throws IOException, ParseException {
        FileUtility fu = new FileUtility();
        // Previous line is used to extract time information
        String preLine = null;
        String line = null;
        Map<String, APICall> idToCall = new HashMap<String, APICall>();
        APICall currentCall = null;
        File dir = new File(dirName);
        File[] files = dir.listFiles();
        for (File file : files) {
            // Check name pattern
            if (!file.getName().startsWith("restlet"))
                continue;
            fu.setInput(file.getAbsolutePath());
            while ((line = fu.readLine()) != null) {
                if (line.endsWith("Server in-bound request")) {
                    Date date = extractDate(preLine);
                    String callId = getCallId(line, file.getName());
                    APICall call = new APICall();
                    currentCall = call;
                    call.callId = callId;
                    call.startTime = date;
                    idToCall.put(callId, call);
                    // Next line is about the actual call
                    line = fu.readLine();
                    String method = extractMethod(line);
                    call.method = method;
                }
                else if (line.contains("content-length")) {
                    int index = line.indexOf(":");
                    currentCall.contentLength = new Integer(line.substring(index + 1).trim());
                }
                else if (line.endsWith("Server out-bound response")) {
                    String callId = getCallId(line, file.getName());
                    // The current Id may not be directly related to out-bound response for a slow process
                    APICall call = idToCall.get(callId);
                    Date date = extractDate(preLine);
                    call.endTime = date;
                }
                preLine = line;
            }
            fu.close();
        }
        outputAPICalls(idToCall);
    }
    
    private void outputAPICalls(Map<String, APICall> idToCall) {
        Map<String, Integer> methodToCount = new HashMap<String, Integer>();
        StringBuilder builder = new StringBuilder();
        builder.append("ID\tMethod\tContentLength\tDuration (second)");
        System.out.println(builder.toString());
        builder.setLength(0);
        Date earliest = null;
        Date latest = null;
        for (APICall call : idToCall.values()) {
            builder.append(call.callId).append("\t");
            builder.append(call.method).append("\t");
            builder.append(call.contentLength).append("\t");
            builder.append(call.getDuration());
            System.out.println(builder.toString());
            builder.setLength(0);
            Integer count = methodToCount.get(call.method);
            methodToCount.put(call.method, count == null ? 1 : ++count);
            if (earliest == null || earliest.after(call.startTime))
                earliest = call.startTime;
            if (latest == null || (call.endTime != null && call.endTime.after(latest)))
                latest = call.endTime;
        }
        // Do a simple tally
        System.out.println("\nMethod\tCounts");
        int total = 0;
        for (String method : methodToCount.keySet()) {
            System.out.println(method + "\t" + methodToCount.get(method));
            total += methodToCount.get(method);
        }
        System.out.println("Sum (" + earliest + " - " + latest + ")\t"+ total);
    }
    
    private String extractMethod(String line) {
        int index = line.indexOf("http://");
        return line.substring(index);
    }
    
    private String getCallId(String line,
                          String fileName) {
        Pattern pattern = Pattern.compile("(\\d)+");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String text = matcher.group();
            return fileName + "." + text;
        }
        return null;
    }
    
    private Date extractDate(String line) throws ParseException {
        int index = line.indexOf("com.sun");
        String dateText = line.substring(0, index).trim();
        Date date = dateFormat.parse(dateText);
        return date;
    }
    
    private class APICall {
        private String callId;
        private Date startTime;
        private Date endTime;
        private String method;
        private int contentLength;
        
        public APICall() {
            
        }
        
        public Long getDuration() {
            if (endTime == null)
                return null;
            return (endTime.getTime() - startTime.getTime()) / 1000; // In second
        }
    }
}
