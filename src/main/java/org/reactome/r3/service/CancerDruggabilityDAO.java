/*
 * Created on Dec 14, 2016
 *
 */
package org.reactome.r3.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

import edu.ohsu.bcb.druggability.dataModel.Drug;
import edu.ohsu.bcb.druggability.dataModel.Interaction;
import edu.ohsu.bcb.druggability.dataModel.Target;

/**
 * Access to the cancer drug/target interactions database constructed by Rory via 
 * hibernate.
 * TODO: The current implementation caches a Session object and has not taken fully use of
 * features and Spring and Hibernate. This may need to be changed in the future.
 * @author gwu
 *
 */
public class CancerDruggabilityDAO extends HibernateDaoSupport implements DrugDAO {
    // Add a filter so that only human targets are listed
    private final String TARGET_SPECIES = "Homo sapiens";
    
    /**
     * Default constructor.
     */
    public CancerDruggabilityDAO() {
    }
    
    /**
     * Query interactions for one gene.
     * @param gene
     * @return
     */
    public List<Interaction> queryInteractions(String gene) {
        Set<String> genes = new HashSet<String>();
        genes.add(gene);
        return queryInteractions(genes);
    }
    
    /**
     * Query interactions for a set of genes.
     * @param genes
     * @return
     */
    public List<Interaction> queryInteractions(final Collection<String> genes) {
        List<Interaction> interactions = getHibernateTemplate().execute(new HibernateCallback<List<Interaction>>() {
            public List<Interaction> doInHibernate(Session session) {
                List<String> geneList = new ArrayList<String>(genes);
                String parameters = FIServiceUtilities.generateInParas(geneList);
                String queryText = "FROM Interaction as i WHERE i.intTarget.targetName IN " + parameters + " AND i.intTarget.targetSpecies = ?";
                Query query = session.createQuery(queryText);
                for (int i = 0; i < geneList.size(); i++)
                    query.setString(i, geneList.get(i));
                query.setString(geneList.size(), TARGET_SPECIES);
                @SuppressWarnings("unchecked")
                List<Interaction> interactions = query.list();
                fillInteractions(interactions);
                return interactions;
            }
        });
        
        return interactions;
    }
    
    /**
     * Query a list of interactions between a collection of genes and one specified drug.
     * @param genes
     * @param drug
     * @return
     */
    public List<Interaction> queryInteractions(final Collection<String> genes,
                                               final String drug) {
        List<Interaction> interactions = getHibernateTemplate().execute(new HibernateCallback<List<Interaction>>() {
            public List<Interaction> doInHibernate(Session session) {
                List<String> geneList = new ArrayList<String>(genes);
                String parameters = FIServiceUtilities.generateInParas(geneList);
                String queryText = "FROM Interaction as i" +
                        " WHERE i.intTarget.targetName IN " + parameters + 
                        " AND i.intDrug.drugName = ?" + 
                        " AND i.intTarget.targetSpecies = ?";
                Query query = session.createQuery(queryText);
                for (int i = 0; i < geneList.size(); i++)
                    query.setString(i, geneList.get(i));
                query.setString(geneList.size(), drug);
                query.setString(geneList.size() + 1, TARGET_SPECIES);
                @SuppressWarnings("unchecked")
                List<Interaction> interactions = query.list();
                fillInteractions(interactions);
                return interactions;
            }
        });
        return interactions;
    }
    
    private void fillInteractions(List<Interaction> interactions) {
        interactions.forEach(interaction -> {
            Hibernate.initialize(interaction);
            Target target = interaction.getIntTarget();
            Hibernate.initialize(target);
            Hibernate.initialize(target.getTargetSynonyms());
            Drug drug = interaction.getIntDrug();
            Hibernate.initialize(drug);
            Hibernate.initialize(drug.getDrugSynonyms());
            if (interaction.getExpEvidenceSet() != null)
                interaction.getExpEvidenceSet().forEach(exp -> {
                    Hibernate.initialize(exp);
                    if (exp.getExpSourceSet() != null) {
                        exp.getExpSourceSet().forEach(source -> {
                            Hibernate.initialize(source);
                            Hibernate.initialize(source.getParentDatabase());
                            Hibernate.initialize(source.getSourceDatabase());
                            Hibernate.initialize(source.getSourceLiterature());
                        });
                    }
                });
            if (interaction.getInteractionSourceSet() != null)
                interaction.getInteractionSourceSet().forEach(source -> {
                    Hibernate.initialize(source);
                    Hibernate.initialize(source.getParentDatabase());
                    Hibernate.initialize(source.getSourceDatabase());
                    Hibernate.initialize(source.getSourceLiterature());
                });
        });
    }
    
    /**
     * Query interactions for one drug.
     * @param drugName
     * @return
     */
    public List<Interaction> queryInteractionsForDrugs(String[] drugNames) {
        List<Interaction> interactions = getHibernateTemplate().execute(new HibernateCallback<List<Interaction>>() {
            public List<Interaction> doInHibernate(Session session) {
                List<String> list = Arrays.asList(drugNames);
                String parameters = FIServiceUtilities.generateInParas(list);
                String queryText = "FROM Interaction as i WHERE i.intDrug.drugName IN " + parameters + " AND i.intTarget.targetSpecies = ?";
                Query query = session.createQuery(queryText);
                for (int i = 0; i < list.size(); i++) {
                    query.setString(i, list.get(i));
                }
                query.setString(list.size(), TARGET_SPECIES);
                @SuppressWarnings("unchecked")
                List<Interaction> interactions = query.list();
                fillInteractions(interactions);
                return interactions;
            }
        });
        return interactions;
    }

    public List<Drug> listDrugs() {
//        Session currentSession = ensureSession();
//        Query query = currentSession.createQuery("FROM Drug");
        @SuppressWarnings("unchecked")
        List<Drug> drugs = getHibernateTemplate().execute(new HibernateCallback<List<Drug>>() {
            public List<Drug> doInHibernate(Session session) {
                Query query = session.createQuery("FROM Drug");
                List<Drug> list = query.list();
                // Make sure everything can be loaded to avoid annoying
                // session close. It seems that Spring's OpenSessionInViewFilter
                // can work only for one SessionFactory, which is sessionFactory.
                // This is very weird. Most likely a bug!
                list.forEach(drug -> {
                    Hibernate.initialize(drug);
                    Hibernate.initialize(drug.getDrugSynonyms());
                });
                return list;
            }
        });
        return drugs;
    }
    
    @Test
    public void testQueryInteractions() {
        String configFileName = "WebContent/WEB-INF/drugHibernate.cfg.xml";
        Configuration configuration = new Configuration().configure(new File(configFileName));
        SessionFactory sf = configuration.buildSessionFactory();
        setSessionFactory(sf);
        
        Set<String> genes = new HashSet<String>();
        genes.add("EGFR");
        genes.add("ESR1");
        genes.add("BRAF");
        
        List<Interaction> interactions = queryInteractions(genes);
        System.out.println("Total interactions: " + interactions.size());
        // Wrapped test cannot get detailed information without in a spring context..
        for (Interaction interaction : interactions)
            System.out.println(interaction.getIntDrug().getDrugName() + "\t" + interaction.getIntTarget().getTargetName());
    }
    
}
