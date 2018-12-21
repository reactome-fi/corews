/*
 * Created on Feb 13, 2014
 *
 */
package org.reactome.r3.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * This class and ReactomeInstance are used to encode GKInstance in a very simple way
 * so that the content of a GKInstance can be serialized into XML/JSON by JAXB.
 * @author gwu
 *
 */
@XmlRootElement
public class ReactomeAttribute {
    private String name;
    private List<Object> values;
    
    /**
     * Default constructor.
     */
    public ReactomeAttribute() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }
    
    public void addValue(Object value) {
        if (values == null)
            values = new ArrayList<Object>();
        values.add(value);
    }
    
    @Override
    public String toString() {
        return name + ": " + values;
    }
    
}
