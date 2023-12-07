/**
 * *****************************************************************************
 * Copyright (C) 2023 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
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

import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity.REQUEST_TYPE;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class BeaconLog {

    @Inject
    private EntityManager em;
    
    public void log(BeaconLogEntity record) {

        if (BeaconLogLevel.LEVEL == BeaconLogLevel.NONE ||
            (BeaconLogLevel.LEVEL == BeaconLogLevel.METADATA && 
             record.getType() != REQUEST_TYPE.METADATA)) {
            return;
        }

        if (BeaconLogLevel.LEVEL.compareTo(BeaconLogLevel.RESPONSES) < 0 &&
            record.getType() == REQUEST_TYPE.QUERY) {
            record.setMessage(null);
            record.setResponse(null);
        }
        
        final EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(record);
            tx.commit();
        } catch (Exception ex) {
            tx.rollback();
        }
    }
}
