/*
 * Created on Sep 4, 2012
 *
 */
package unitTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Test;
import org.reactome.r3.util.FileUtility;

/**
 * This class is used to analyze access logging information.
 * @author gwu
 *
 */
public class AccessLoggingAnalyzer {
//    private final String DIR_NAME = "logs/access/";
//    private final String DIR_NAME = "/Users/gwu/Documents/wgm/work/tomcat_access_logs/032013/";
//    private final String DIR_NAME = "/Users/gwu/Documents/EclipseWorkspace/caBigR3WebApp/logs/access/103013/";
//    private final String DIR_NAME = "/Users/gwu/Documents/EclipseWorkspace/caBigR3WebApp/logs/access/040714/";
//    private final String DIR_NAME = "/Users/gwu/Documents/EclipseWorkspace/caBigR3WebApp/logs/access/040615/";
//    private final String DIR_NAME = "/Users/gwu/Documents/EclipseWorkspace/caBigR3WebApp/logs/access/042116/";
//    private final String DIR_NAME = "/Users/gwu/Documents/EclipseWorkspace/caBigR3WebApp/logs/access/043017/";
//    private final String DIR_NAME = "logs/access/110317/";
//    private final String DIR_NAME = "logs/access/120718/";
    private final String DIR_NAME = "logs/access/100320/";
//    private String URL_PATH = "/caBigR3WebApp";
    // Used to get Date
    private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    // As a cutoff value
    private Date cutoffDateLater = null;
    private Date cutoffDateEarlier = null;
    
    public AccessLoggingAnalyzer() {
    }
    
    @Test
    public void analyzeAccess() throws Exception {
//        URL_PATH = "/ReactomeRESTfulAPI";
//        URL_PATH = "/caBIOWebApp";
        Map<Date, List<String>> dateToIps = new HashMap<Date, List<String>>();
        FileUtility fu = new FileUtility();
        // Used to check daily average
//        SummaryStatistics stat = new SummaryStatistics();
        // For FI plug-in
        String[] urlPaths = new String[] {
              "/caBigR3WebApp",
              "/caBigR3WebApp2012",
              "/caBigR3WebApp2013",
              "/caBigR3WebApp2014",
              "/caBigR3WebApp2015",
              "/caBigR3WebApp2016",
              "/caBigR3WebApp2017",
              "/caBigR3WebApp2018",
              "/caBigR3WebApp2019",
              "/ReactomeRESTfulAPI_PathX"
        };
        // For Reactome RESTFUL API
//        String[] urlPaths = new String[] {
//                "/ReactomeRESTfulAPI"
//        };
//        // for SOAP API
//        String[] urlPaths = new String[] {
//                "POST /caBIOWebApp/services/caBIOService"
//        };
//        cutoffDateLater = dateFormat.parse("2013-04-01");
////        cutoffDateLater = dateFormat.parse("2014-01-01");
//        cutoffDateEarlier = dateFormat.parse("2014-03-31");
        
//        cutoffDateLater = dateFormat.parse("2014-04-01");
//        cutoffDateEarlier = dateFormat.parse("2015-03-31");
        
//        cutoffDateLater = dateFormat.parse("2015-04-01");
//        cutoffDateEarlier = dateFormat.parse("2016-03-31");
        
//        cutoffDateLater = dateFormat.parse("2016-04-01");
//        cutoffDateEarlier = dateFormat.parse("2017-03-31");
//        
//        cutoffDateLater = dateFormat.parse("2016-01-01");
//        cutoffDateEarlier = dateFormat.parse("2016-12-31");
        
//        cutoffDateLater = dateFormat.parse("2016-11-01");
//        cutoffDateEarlier = dateFormat.parse("2017-10-31");
        
//        cutoffDateLater = dateFormat.parse("2018-01-01");
//        cutoffDateEarlier = dateFormat.parse("2018-11-30");
        
        cutoffDateLater = dateFormat.parse("2019-10-01");
        cutoffDateEarlier = dateFormat.parse("2020-09-30");

        for (String urlPath : urlPaths) {
            parseFiles(urlPath,
                       dateToIps,
                       fu);
        }
        List<String> ipList = getTotalIPs(dateToIps);
        Set<String> totalIps = new HashSet<String>(ipList);
        System.out.println("Total IPs: " + totalIps.size());
        final Map<String, Integer> ipToCount = countAccesss(ipList);
        List<String> ipSortedList = new ArrayList<String>(ipToCount.keySet());
        Collections.sort(ipSortedList, new Comparator<String>() {
            public int compare(String ip1, String ip2) {
                Integer count1 = ipToCount.get(ip1);
                Integer count2 = ipToCount.get(ip2);
                return count2 - count1;
            }
        });
        
        System.out.println("\nTop IP addresses:");
        System.out.println("Order\tIP\tOrganization\tAccess");
        for (int i = 0; i < 30 && i < ipSortedList.size(); i ++) {
            String ip = ipSortedList.get(i);
            String organization = getOrganization(ip);
            Integer count = ipToCount.get(ip);
            System.out.println((i + 1) + "\t" + ip + "\t" + organization + "\t" + count);
        }
        System.out.println("\nAccesses based on month:");
        countBasedOnMonth(dateToIps);
    }

    private void parseFiles(String urlPath,
                            Map<Date, List<String>> dateToIps, 
                            FileUtility fu)
            throws ParseException, IOException {
        String line;
        int index;
        File dir = new File(DIR_NAME);
        List<File> files = listFiles(dir);
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".txt"))
                continue;
            Date date = extractDate(name);
            if (cutoffDateLater != null && date.before(cutoffDateLater))
                continue; // Escape this file
            if (cutoffDateEarlier != null && date.after(cutoffDateEarlier))
                continue; // Escape this file
            List<String> ips = dateToIps.get(date);
            if (ips == null) {
                ips = new ArrayList<String>();
                dateToIps.put(date, ips);
            }
//            else {
//                System.out.println(date + " has been checked!");
//            }
            fu.setInput(file.getAbsolutePath());
            while ((line = fu.readLine()) != null) {
                if (line.contains(urlPath)) {
                    index = line.indexOf("-");
                    String ip = line.substring(0, index).trim();
                    ips.add(ip);
                }
            }
            fu.close();
        }
    }
    
    private List<File> listFiles(File dir) {
        List<File> files = new ArrayList<>();
        List<File> currentDirs = new ArrayList<>();
        List<File> nextDirs = new ArrayList<>();
        currentDirs.add(dir);
        while (currentDirs.size() > 0) {
            for (File tmpDir : currentDirs) {
                File[] tmpFiles = tmpDir.listFiles();
                if (tmpFiles == null || tmpFiles.length == 0)
                    continue;
                for (File tmpFile : tmpFiles) {
                    if (tmpFile.isDirectory())
                        nextDirs.add(tmpFile);
                    else if (tmpFile.getName().endsWith(".txt"))
                        files.add(tmpFile);
                }
            }
            currentDirs.clear();
            currentDirs.addAll(nextDirs);
            nextDirs.clear();
        }
        return files;
    }
    
    private List<String> getTotalIPs(Map<Date, List<String>> dateToIps) {
        List<String> totalIPs = new ArrayList<String>();
        for (Date date : dateToIps.keySet()) 
            totalIPs.addAll(dateToIps.get(date));
        return totalIPs;
    }
    
    private Map<String, Integer> countAccesss(List<String> ips) {
        Map<String, Integer> ipToCount = new HashMap<String, Integer>();
        for (String ip : ips) {
            Integer c = ipToCount.get(ip);
            if (c == null)
                ipToCount.put(ip, 1);
            else
                ipToCount.put(ip, ++c);
        }
        return ipToCount;
    }
    
    private void countBasedOnMonth(Map<Date, List<String>> dateToIPs) {
        Calendar calendar = Calendar.getInstance();
        Map<String, List<String>> monthToIps = new HashMap<String, List<String>>();
        for (Date date : dateToIPs.keySet()) {
            List<String> ips = dateToIPs.get(date);
            calendar.setTime(date);
            Integer year = calendar.get(Calendar.YEAR);
            Integer month = calendar.get(Calendar.MONTH) + 1;
            String key = year + "_" + month; // Month is 0 ~ 11
            List<String> mIps = monthToIps.get(key);
            if (mIps == null) {
                mIps = new ArrayList<String>();
                monthToIps.put(key, mIps);
            }
            mIps.addAll(ips);
        }
        System.out.println("Month\tTotalAccess\tTotalIPs");
        List<String> monthList = new ArrayList<String>(monthToIps.keySet());
        Collections.sort(monthList, new Comparator<String>() {
            public int compare(String m1, String m2) {
                String[] tokens = m1.split("_");
                Integer year1 = new Integer(tokens[0]);
                Integer month1 = new Integer(tokens[1]);
                tokens = m2.split("_");
                Integer year2 = new Integer(tokens[0]);
                Integer month2 = new Integer(tokens[1]);
                if (year1.equals(year2)) {
                    return month1.compareTo(month2);
                }
                return year1.compareTo(year2);
            }
        });
        SummaryStatistics accessStat = new SummaryStatistics();
        SummaryStatistics ipStat = new SummaryStatistics();
        for (String month : monthList) {
            List<String> ips = monthToIps.get(month);
            if (ips.size() == 0)
                continue; // No need to count: most likely access has not turned on
            Set<String> ipSet = new HashSet<String>(ips);
            System.out.println(month + "\t" + ips.size() + "\t" + ipSet.size());
            accessStat.addValue(ips.size());
            ipStat.addValue(ipSet.size());
        }
        System.out.println("Total accesses: " + (int) (accessStat.getSum() + 0.5d));
        System.out.println("Monthly average accesses: " + (int)(accessStat.getMean()  + 0.5) + " +- " + (int)(accessStat.getStandardDeviation() + 0.5));
        System.out.println("Monthly different IPs: " + (int) ipStat.getMean() + " +- " + (int) (ipStat.getStandardDeviation() + 0.5d));
    }
    
    private Date extractDate(String fileName) throws ParseException {
        int index = fileName.indexOf(".");
        int lastIndex = fileName.lastIndexOf(".");
        String date = fileName.substring(index + 1, lastIndex);
        return dateFormat.parse(date);
    }
    
    @Test
    public void testIPAddress() throws Exception {
        String ip = "206.108.125.170"; // www.reactome.org
//        ip = "127.64.84.2";
        getOrganization(ip);
    }
    
    @Test
    public void checkSOAPAPILogging() throws Exception {
        String dirName = "/Users/gwu/Documents/EclipseWorkspace/caBigR3WebApp/logs/access/092614/";
        String fileName = dirName + "caBIOWeb_SOAP.txt";
        String url = "POST /caBIOWebApp/services/caBIOService HTTP/1.0";
        FileUtility fu = new FileUtility();
        fu.setInput(fileName);
        String line = null;
        Set<String> times = new HashSet<String>();
        Set<String> ips = new HashSet<String>();
        while ((line = fu.readLine()) != null) {
            if (!line.contains(url))
                continue;
            int index1 = line.indexOf(".");
            int index2 = line.indexOf(".", index1 + 1);
            String time = line.substring(index1 + 1, index2);
            times.add(time);
            index1 = line.indexOf(":");
            index2 = line.indexOf(" -");
            String ip = line.substring(index1 + 1, index2).trim();
            ips.add(ip);
        }
        fu.close();
        System.out.println("Total days: " + times.size());
        for (String time : times)
            System.out.println(time);
        System.out.println("\nTotal ips: " + ips.size());
        for (String ip : ips)
            System.out.println(ip);
    }

    private String getOrganization(String ip) throws IOException {
        Process ps = Runtime.getRuntime().exec("whois " + ip);
        InputStream os = ps.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(os));
        String line = null;
        String organization = null;
        String netName = null;
        while ((line = br.readLine()) != null) {
//            System.out.println(line);
            if (line.startsWith("OrgName:")) {
                int index = line.indexOf(":");
                organization = line.substring(index + 1).trim();
            }
            else if (line.startsWith("netname:")) {
                int index = line.indexOf(":");
                netName = line.substring(index + 1).trim();
            }
        }
        br.close();
        os.close();
        if (netName != null)
            return netName;
        return organization;
    }
    
}
