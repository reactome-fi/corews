/*
 * Created on Feb 13, 2014
 *
 */
package org.reactome.r3.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 * I need to a really very simple class to describe a GKInstance's information so
 * that such a class can be used by JAXB directly. This class and its associated
 * class ReactomeAttribute is used for this purpose.
 * @author gwu
 */
@XmlRootElement
public class ReactomeInstance {
    private String schemaClass;
    private List<ReactomeAttribute> attributes;
    // Two most important attributes
    private String displayName;
    private Long dbId;
    
    /**
     * Default constructor.
     */
    public ReactomeInstance() {
    }

    public String getSchemaClass() {
        return schemaClass;
    }

    public void setSchemaClass(String schemaClass) {
        this.schemaClass = schemaClass;
    }

    public List<ReactomeAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<ReactomeAttribute> attributes) {
        this.attributes = attributes;
    }
    
    public void addAttribute(ReactomeAttribute att) {
        if (attributes == null)
            attributes = new ArrayList<ReactomeAttribute>();
        attributes.add(att);
    }
    
    /**
     * Sort attributes alphabetically based on their names.
     */
    public void sortAttributes() {
        if (attributes == null || attributes.size() == 0)
            return;
        Collections.sort(attributes, new Comparator<ReactomeAttribute>() {
            public int compare(ReactomeAttribute att1, ReactomeAttribute att2) {
                return att1.getName().compareTo(att2.getName());
            }
        });
    }
    
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getDbId() {
        return dbId;
    }

    public void setDbId(Long dbId) {
        this.dbId = dbId;
    }

    @Override
    public String toString() {
        return "[" + schemaClass + ": " + dbId + "]: " + displayName;
    }
    
}
