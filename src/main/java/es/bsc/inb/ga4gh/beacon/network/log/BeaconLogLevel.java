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

/**
 * @author Dmitry Repchevsky
 */

public enum BeaconLogLevel {
   
    NONE, // no logging enabled
    METADATA, 
    QUERIES,
    REQUESTS,
    RESPONSES,
    ALL;
    
    public final static String BEACON_NETWORK_LOG_LEVEL = "BEACON_NETWORK_LOG_LEVEL";
    
    public final static BeaconLogLevel LEVEL;
    
    static {
        
        final String level = System.getenv(BEACON_NETWORK_LOG_LEVEL);
        if (level == null) {
            LEVEL = NONE;
        } else {
            BeaconLogLevel value = NONE;
            try {
                value = BeaconLogLevel.valueOf(level);
            } catch (IllegalArgumentException ex) {}
            LEVEL = value;
        }
    }
}
