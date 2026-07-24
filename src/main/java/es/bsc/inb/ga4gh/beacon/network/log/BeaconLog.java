/**
 * *****************************************************************************
 * Copyright (C) 2026 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
 * and Barcelona Supercomputing Center (BSC)
 *
 * Modifications to the initial code base are copyright of their respective
 * authors, or their employers as appropriate.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * *****************************************************************************
 */

package es.bsc.inb.ga4gh.beacon.network.log;

import es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity.REQUEST_TYPE;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class BeaconLog {

    @Inject
    private EntityManager em;
        
    public BeaconLogEntity getLastResponse(String url) {
        final EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            final TypedQuery<BeaconLogEntity> query = 
                    em.createQuery("SELECT l FROM log l WHERE code < 300 AND url = :url ORDER BY l.id DESC", 
                            BeaconLogEntity.class);
            query.setParameter("url", url);
            query.setMaxResults(1);
            final BeaconLogEntity entry = query.getSingleResult();
            tx.commit();
            return entry;
        } catch (Exception ex) {
            if (tx.isActive()) {
                try {
                    tx.rollback();
                } catch(Exception e) {}
            }
        }
        return null;
    }
    
/**
     * Get last 'REQUEST' records from the log.
     * 
     * @param n the number of records to read
     * 
     * @return list of log records.
     */
    public List<BeaconLogEntity> getLastRequests(int n) {
        final EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            final TypedQuery<BeaconLogEntity> query = 
                    em.createQuery("SELECT l FROM log l WHERE l.type = QUERY ORDER BY l.id DESC", 
                            BeaconLogEntity.class);
            query.setMaxResults(n);
            final List<BeaconLogEntity> entries = query.getResultList();
            tx.commit();
            return entries;
        } catch (Exception ex) {
            if (tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception e) {}
            }
        }
        
        return getLastRecords(n);
    }
    
    /**
     * Reads last records from the log file (if exists).
     * 
     * @param n the number of records to read
     * 
     * @return list of log records.
     */
    private List<BeaconLogEntity> getLastRecords(int n) {
        if (BeaconFileLogger.filelog != null) {
            
        }
        
        return Collections.EMPTY_LIST;
    }

    public void log(BeaconLogEntity record) {
        log(record, BeaconLogLevel.LEVEL);
    }
    
    public void log(BeaconLogEntity record, BeaconLogLevel level) {

        if (level == BeaconLogLevel.NONE ||
            (level == BeaconLogLevel.METADATA && 
             record.getType() != REQUEST_TYPE.METADATA)) {
            return;
        }

        if (level.compareTo(BeaconLogLevel.RESPONSES) < 0 &&
            record.getType() != REQUEST_TYPE.METADATA) {
            record.setMessage(null);
            record.setResponse(null);
        }
        
        if (BeaconFileLogger.filelog != null) {
            BeaconFileLogger.filelog.info(record.toString());
        }

        final EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(record);
            tx.commit();
        } catch (Exception ex) {
            if (tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception e) {}
            }
        }
    }
}
