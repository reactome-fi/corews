/*
 * Created on Oct 12, 2006
 *
 */
package org.reactome.r3.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.NotImplementedException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.reactome.funcInt.Evidence;
import org.reactome.funcInt.Interaction;
import org.reactome.r3.util.InteractionUtilities;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

/**
 * This class is used to talk to the database by using Hibernate OR mapping.
 * @author guanming
 *
 */
public class HibernateInteractionDAO extends HibernateDaoSupport implements InteractionDAO {
    
    public HibernateInteractionDAO() {
    }

    public List<Interaction> load(double cutoff)  throws DataAccessException{
        String query = "SELECT i FROM Interaction AS i JOIN i.evidence AS e WHERE e.probability >=?";
        List list = getHibernateTemplate().find(query, cutoff);
        List<Interaction> rtn = convertToTypedList(list);
        return rtn;
    }
    
    private List<Interaction> convertToTypedList(Collection list) {
        List<Interaction> rtn = new ArrayList<Interaction>();
        if (list != null) {
            for (Iterator it = list.iterator(); it.hasNext();) {
                rtn.add((Interaction)it.next());
            }
        }
        return rtn;
    }

    //TODO: This will be implemented later.
    public List<Interaction> load(Evidence evidence) {
        // Make a copy of evidence as a search criertia to avoid use probability
//        Evidence copy = new Evidence();
//        // Just need these properties
//        copy.setHumanInteraction(evidence.getHumanInteraction());
//        copy.setOrthoInteraction(evidence.getOrthoInteraction());
//        copy.setYeastInteraction(evidence.getYeastInteraction());
//        copy.setGeneExp(evidence.getGeneExp());
//        return null;
        throw new NotImplementedException("load(Evidence) not implemented!");
    }
    
    public Interaction load(long dbId) {
        Interaction rtn = (Interaction) getHibernateTemplate().load(Interaction.class, dbId);
        return rtn;
    }

    public List<Interaction> loadFromReactome(long reactomeId) {
        String query = "SELECT i FROM Interaction as i JOIN i.reactomeSource as src WHERE src.reactomeId = ?";
        List list = getHibernateTemplate().find(query, reactomeId);
        return convertToTypedList(list);
    }
    
    public List<Interaction> searchForAll(List<String> accessions) {
        String paras = FIServiceUtilities.generateInParas(accessions);
        String query = "FROM Interaction as i WHERE i.firstProtein.primaryDbReference.accession IN " + paras + 
                       "AND i.secondProtein.primaryDbReference.accession IN " + paras;
        // Need to use arrays times
        List<String> arrays = new ArrayList<String>(accessions);
        arrays.addAll(accessions);
        List list = getHibernateTemplate().find(query, arrays.toArray());
        return convertToTypedList(list);
    }
    
    public List<Interaction> searchNamesForAll(final List<String> names) {
        HibernateTemplate template = getHibernateTemplate();
        List<Interaction> list = template.execute(new HibernateCallback<List<Interaction>>() {
            public List<Interaction> doInHibernate(Session session) {
                // Do a two-step query to avoid expensive joining
                StringBuilder builder = new StringBuilder();
                builder.append("(");
                for (Iterator<String> it = names.iterator(); it.hasNext();) {
                    builder.append("'").append(it.next()).append("'");
                    if (it.hasNext())
                        builder.append(",");
                }
                builder.append(")");
                String query = "SELECT p.dbId FROM Protein as p WHERE p.shortName IN " + builder.toString();
                List<?> proteinIds = session.createQuery(query).list();
                if (proteinIds == null || proteinIds.size() == 0)
                    return new ArrayList<Interaction>();
                String idText = InteractionUtilities.joinStringElements(",", proteinIds);
                idText = "(" + idText + ")";
                query = "FROM Interaction i WHERE i.firstProtein IN " + idText + " AND i.secondProtein IN " + idText;
                List<Interaction> interactions = session.createQuery(query).list();
                return interactions;
            }
        });
        return list;
    }

    @SuppressWarnings("unchecked")
    public List<Interaction> search(List<String> accessions) {
        String paras = FIServiceUtilities.generateInParas(accessions);
        String queryStr = "FROM Interaction as i WHERE i.firstProtein.primaryDbReference.accession IN " + 
                          paras;
        List list1 = getHibernateTemplate().find(queryStr, accessions.toArray());
        queryStr = "FROM Interaction as i WHERE i.secondProtein.primaryDbReference.accession IN " + 
                    paras;
        List list2 = getHibernateTemplate().find(queryStr, accessions.toArray());
        Set rtn = new HashSet();
        if (list1 != null)
            rtn.addAll(list1);
        if (list2 != null)
            rtn.addAll(list2);
        return convertToTypedList(rtn);
    }

    @SuppressWarnings("unchecked")
    public Interaction search(final String accession1, final String accession2) {
        HibernateTemplate template = getHibernateTemplate();
        List list = (List) template.execute(new HibernateCallback() {
           public Object doInHibernate(Session session) {
               String queryStr = "FROM Interaction as i where i.firstProtein.primaryDbReference.accession = ? and i.secondProtein.primaryDbReference.accession = ?";
               Query query = session.createQuery(queryStr);
               query.setParameter(0, accession1).setParameter(1, accession2);
               List list = query.list();
               if (list != null && list.size() > 0)
                   return list;
               // Try another way
               query.setParameter(0, accession2).setParameter(1, accession1);
               list = query.list();
               return list;
           }
        });
        if (list != null && list.size() > 0)
            return (Interaction) list.get(0);
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public List<Interaction> queryOnNames(final String name1, final String name2) {
        HibernateTemplate template = getHibernateTemplate();
        List<Interaction> list = (List<Interaction>) template.execute(new HibernateCallback() {
           public Object doInHibernate(Session session) {
               String queryText = "FROM Interaction as i WHERE ((i.firstProtein.shortName = ? AND i.secondProtein.shortName = ?) OR " +
                                  "(i.firstProtein.shortName = ? AND i.secondProtein.shortName = ?))"; // For a reverse search
               Query query = session.createQuery(queryText);
               query.setString(0, name1);
               query.setString(1, name2);
               query.setString(2, name2);
               query.setString(3, name1);
               List interactions = query.list();
               return (List<Interaction>) interactions;
           }
        });
        return list;
    }

    @SuppressWarnings("unchecked")
    public List<Interaction> search(String accession) {
        String query = "FROM Interaction as i WHERE i.firstProtein.primaryDbReference.accession = ?";
        List list = getHibernateTemplate().find(query, accession);
        Set set = new HashSet();
        if (list != null)
            set.addAll(list);
        query = "FROM Interaction as i WHERE i.secondProtein.primaryDbReference.accession = ?";
        list = getHibernateTemplate().find(query, accession);
        if (list != null)
            set.addAll(list);
        return convertToTypedList(set);
    }    
}
