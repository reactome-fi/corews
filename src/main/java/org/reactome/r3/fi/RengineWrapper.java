/*
 * Created on Apr 17, 2014
 *
 */
package org.reactome.r3.fi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.junit.Test;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.RVector;
import org.rosuda.JRI.Rengine;

/**
 * This class is used to wrap an Rengine object from JRI so that the Java code can
 * control its behavior (e.g. threading related things). <p />
 * In order to use this class, the following steps should be performed first:
 * 1). In R, run <code>system.file("jri", package="rJava")</code> to find where JNI
 * library is. This JNI library is required by JRI.
 * 2). After find the above path, add it to the JVM argument: -Djava.library.path=
 * 3). Make sure R_HOME is set. Under mac, R_HOME should be: /Library/Frameworks/R.framework/Resources.
 * Under Eclipse, this parameter can be set using the environment tab in the running dialog.
 * 4). As with libdai, copy three jar files and JNI lib to the tomcat's lib folder to avoid already 
 * loading error.
 * 5). For more detailed information, see run.in script in the downloaded JRI file and the following web
 * page: http://rforge.net/JRI/
 * Updated on July 25, 2017: Most likely we only need to copy the following four files into the lib folder 
 * of tomcat: libjri.jnilib, JRI.jar, JRIEngine.jar, REngine.jar to fix compiling problems. This class
 * has not been used actually. So making compiler happy should be good enough for the time being.
 * @author gwu
 *
 */
public class RengineWrapper {
    
    private static Logger logger = Logger.getLogger(RengineWrapper.class);
    // Cache the REngine so that only one instance is needed. However,
    // the shortcoming is that R will be kept in memory to increase memory
    // usage.
    private static Rengine rEngine;
    
    /**
     * Default constructor.
     */
    public RengineWrapper() {
    }
    
    @Test
    public void testKSTest() {
        List<Double> list1 = new ArrayList<Double>();
        for (int i = 0; i < 100; i++)
            list1.add(Math.random());
        List<Double> list2 = new ArrayList<Double>();
//        for (int i = 0; i < 100; i++)
//            list2.add(Math.random());
        // Check using Gausin
        Random random = new Random();
        for (int i = 0; i < 100; i++)
            list2.add(random.nextGaussian());
        double pvalue = kolmogorovSmirnovTest(list1, list2);
        System.out.println("Pvalue from ks.test: " + pvalue);
    }
    
    public synchronized double kolmogorovSmirnovTest(double[] x,
                                                     double[] y) {
        setUpRengine();
        // Use these specific names to avoid variable conflicts with
        // other native R calling.
        String xVarName = "ks.test.x";
        if(!rEngine.assign(xVarName, x)) {
            throw new IllegalStateException("Cannot assign double array x to Rengine");
        }
        String yVarName = "ks.test.y";
        if(!rEngine.assign(yVarName, y)) {
            throw new IllegalStateException("Cannot assign double array y to Rengine");
        }
        // Call R ks.test function: two-sided and two samples
        REXP exp = rEngine.eval("ks.test(" + xVarName + ", " + yVarName + ")");
        Double pvalue = getPValueFromKSTest(exp);
        if (pvalue == null) {
            throw new IllegalStateException("Cannot get pvalue from ks.test in R");
        }
        //Remove these two assigned variables from R
        rEngine.eval("rm(" + xVarName + ", " + yVarName + ")");
        return pvalue;
    }

    private void setUpRengine() {
        if (!Rengine.versionCheck()) {
            logger.error("Version checking for Rengine failed!");
            throw new IllegalStateException("Rengine version is not correct!");
        }
        // Set up Rengine
        if (rEngine == null) {
            rEngine = new Rengine(new String[]{"--vanilla"}, // no-store and no-save
                                  false, // Just want to run it once so no event loop
                                  null); // Don't want to see anything output
            if (!rEngine.waitForR()) {
                logger.error("Cannot load R into Java!");
                rEngine = null;
                throw new IllegalStateException("Cannot load R into R.");
            }
        }
    }    
    
    /**
     * Do a two samples and two-sided Kolmogorov-Smirnov test by calling a R
     * method using JRI.
     * @param list1
     * @param list2
     * @return
     */
    public double kolmogorovSmirnovTest(List<Double> list1,
                                        List<Double> list2) {
        // Make sure no null in the passed list
        for (Double value : list1)
            if (value == null)
                throw new IllegalArgumentException("The list of Double should not contain null!");
        for (Double value : list2)
            if (value == null)
                throw new IllegalArgumentException("The list of Double should not contain null!");
        // Convert lists into double arrays so that they can be used in Rengine
        double[] x = new double[list1.size()];
        for (int i = 0; i < list1.size(); i++)
            x[i] = list1.get(i);
        double[] y = new double[list2.size()];
        for (int i = 0; i < list2.size(); i++)
            y[i] = list2.get(i);
        return kolmogorovSmirnovTest(x, y);
    }
    
    private Double getPValueFromKSTest(REXP exp) {
        RVector vector = exp.asVector();
        if (vector.getNames() != null) {
            Vector<?> names = vector.getNames();
            for (int i = 0; i < vector.size(); i++) {
                String name = (String) names.get(i);
                if (name.equals("p.value")) {
                    REXP pvalue = (REXP) vector.get(i);
                    return pvalue.asDouble();
                }
            }
        }
        return null;
    }
    
}
