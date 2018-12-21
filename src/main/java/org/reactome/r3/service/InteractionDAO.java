/*
 * Created on Oct 12, 2006
 *
 */
package org.reactome.r3.service;

import java.util.List;

import org.reactome.funcInt.Evidence;
import org.reactome.funcInt.Interaction;
import org.springframework.dao.DataAccessException;

public interface InteractionDAO {
    
    /**
     * Load an Interaction for the specified Interaction dbId.
     * @param dbId
     * @return
     */
    public Interaction load(long dbId) throws DataAccessException;
    
    /**
     * Search for interactions that have protein with the provided accession participating.
     * @param accession
     * @return
     */
    public List<Interaction> search(String accession) throws DataAccessException;
    
    /**
     * Query Interaction based on two protein or gene names.
     * @param name1
     * @param name2
     * @return
     * @throws DataAccessException
     */
    public List<Interaction> queryOnNames(String name1, String name2) throws DataAccessException;
    
    /**
     * Search for interactions that have both proteins specified by accessions participating.
     * The order of these two proteins are not considered. There should be only one returned
     * if such an interaction existing.
     * @param accession1
     * @param accession2
     * @return
     */
    public Interaction search(String accession1, String accession2) throws DataAccessException;
    
    /**
     * Search for interactions with proteins specified by their accession participating.
     * @param accessions
     * @return
     */
    public List<Interaction> search(List<String> accessions) throws DataAccessException;
    
    /**
     * Search for interactions with proteins specified by their accession numbers. This method
     * is different from the previous one, {@link search(List<String>)}.
     * @param accessions
     * @return
     * @throws DataAccessException
     */
    public List<Interaction> searchForAll(List<String> accessions) throws DataAccessException;
    
    public List<Interaction> searchNamesForAll(List<String> names) throws DataAccessException;
    
    /**
     * Load a list of Interaction that are extracted from the specified Reactome instance.
     * @param source
     * @return
     */
    public List<Interaction> loadFromReactome(long reactomeId) throws DataAccessException;
    
    /**
     * Load a list of Interaction that match the provided evidence. The value of
     * probability is not considered. See <@link>load(double).
     * @param evidence
     * @return
     * @ref load(double)
     */
    public List<Interaction> load(Evidence evidence) throws DataAccessException;
    
    /**
     * Load a list of Interaction that are supported by evidences and their probablity
     * is no less than the cutoff value.
     * @param cutoff
     * @return
     */
    public List<Interaction> load(double cutoff) throws DataAccessException;
}
