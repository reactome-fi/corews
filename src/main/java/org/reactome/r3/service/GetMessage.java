/*
 * Created on Sep 6, 2007
 *
 */
package org.reactome.r3.service;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "GetMessage")
public class GetMessage {
    private String name;
    
    public GetMessage() {
        
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
}
