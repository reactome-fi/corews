/*
 * Created on Sep 22, 2010
 *
 */
package org.reactome.r3.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.reactome.cancerindex.model.Roles;
import org.reactome.cancerindex.model.Sentence;
import org.reactome.r3.util.InteractionUtilities;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

/**
 * This class is used to as a DAO for the cancer gene index database.
 * @author wgm
 *
 */
public class CancerGeneIndexDAO extends HibernateDaoSupport {
    
    public CancerGeneIndexDAO() {
    }

    public List<Sentence> queryAnnotations(final String gene) throws Exception {
        List<Sentence> sentences = getHibernateTemplate().execute(new HibernateCallback<List<Sentence>>() {
            public List<Sentence> doInHibernate(Session session) throws HibernateException {
                String queryText = "SELECT s FROM GeneEntry as g inner join g.sentence as s WHERE g.hugoGeneSymbol = ?";
                Query query = session.createQuery(queryText).setString(0, gene);
                List<Sentence> list = query.list();
                for (Sentence sentence : list) {
                    // Need to manually initialize so that these objects can be converted into XML.
                    Hibernate.initialize(sentence);
                    Hibernate.initialize(sentence.getDiseaseData());
                    Hibernate.initialize(sentence.getEvidenceCode());
                    Hibernate.initialize(sentence.getRoles());
                    if (sentence.getRoles() != null) {
                        for (Roles roles : sentence.getRoles()) {
                            Hibernate.initialize(roles);
                            Hibernate.initialize(roles.getOtherRole());
                            Hibernate.initialize(roles.getPrimaryNCIRoleCode());
                        }
                    }
                }
                return list;
            }
        });
        return sentences;
    }
    
    public Map<String, Set<String>> queryGeneToDiseaseCodes(Set<String> genes) throws Exception {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (String gene : genes) {
            builder.append("'").append(gene).append("'");
            index ++;
            if (index < genes.size())
                builder.append(",");
        }
        final String query = "SELECT g.HUGO_GENE_SYMBOL, d.NCI_DISEASE_CONCEPT_CODE " +
                       "FROM Sentence s, GENE_ENTRY g, DISEASE_DATA d " +
                       "WHERE s.GENE_ENTRY_ID = g.ID AND s.DISEASE_DATA = d.ID AND " +
                       "s.NEGATION_INDICATOR != 'yes' AND g.HUGO_GENE_SYMBOL in (" +
                       builder.toString() + ")";
        // Need a SQL query: no native support from template
        HibernateTemplate template = getHibernateTemplate();
        List<?> list = template.execute(new HibernateCallback<List<?>>() {
            public List<?> doInHibernate(Session session) throws HibernateException {
                Query queryObject = session.createSQLQuery(query);
                return queryObject.list();
            }
        });
        Map<String, Set<String>> geneToDiseaseCodes = new HashMap<String, Set<String>>();
        for (Iterator<?> it = list.iterator(); it.hasNext();) {
            Object[] values = (Object[]) it.next();
            InteractionUtilities.addElementToSet(geneToDiseaseCodes,
                                                 values[0].toString(), 
                                                 values[1].toString());
        }
        return geneToDiseaseCodes;
    }
    
}
