/**
 * *****************************************************************************
 * Copyright (C) 2025 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
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

package es.bsc.inb.ga4gh.beacon.network.config;

/**
 * @author Dmitry Repchevsky
 */

public final class ConfigurationProperties {
    
    public final static String BN_CONFIG_DIR_PROPERTY_NAME = "BEACON_NETWORK_CONFIG_DIR";
    
    public final static String BN_REQUEST_TIMEOUT_PROPERTY_NAME = "BEACON_NETWORK_REQUEST_TIMEOUT";
    public final static String BN_DISCARD_REQUEST_TIMEOUT_PROPERTY_NAME = "BEACON_NETWORK_DISCARD_REQUEST_TIMEOUT";
    public final static String BN_REFRESH_METADATA_TIMEOUT_PROPERTY_NAME = "BEACON_NETWORK_REFRESH_METADATA_TIMEOUT";
    
    public final static String BEACON_NETWORK_CONFIG_DIR = "BEACON-INF/";
    public final static String BEACON_NETWORK_CONFIG_FILE = "beacon-network.json";
    public final static String BEACON_NETWORK_INFO_FILE = "beacon-network-info.json";
    public final static String BEACON_NETWORK_MAP_FILE = "beacon-network-map.json";
    public final static String BEACON_NETWORK_CONFIGURATION_FILE = "beacon-network-configuration.json";
    
    public final static String BN_CONFIG_DIR_PROPERTY;
    
    public final static long BN_DISCARD_REQUEST_TIMEOUT_PROPERTY;
    public final static long BN_REQUEST_TIMEOUT_PROPERTY;
    public final static long BN_REFRESH_METADATA_TIMEOUT_PROPERTY;
    
    static {
        BN_CONFIG_DIR_PROPERTY = System.getenv(BN_CONFIG_DIR_PROPERTY_NAME);
        BN_DISCARD_REQUEST_TIMEOUT_PROPERTY = readProperty(BN_DISCARD_REQUEST_TIMEOUT_PROPERTY_NAME, 5);
        BN_REQUEST_TIMEOUT_PROPERTY = readProperty(BN_REQUEST_TIMEOUT_PROPERTY_NAME, 600);
        BN_REFRESH_METADATA_TIMEOUT_PROPERTY = readProperty(BN_REFRESH_METADATA_TIMEOUT_PROPERTY_NAME, 60);
    }
    
    private static long readProperty(String property, long def) {
        final String val = System.getenv(property);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ex) {}
        }
        return def;
    }
}
