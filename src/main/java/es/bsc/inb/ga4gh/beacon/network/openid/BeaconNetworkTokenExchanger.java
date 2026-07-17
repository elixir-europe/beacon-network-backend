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

package es.bsc.inb.ga4gh.beacon.network.openid;

import es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

@ApplicationScoped
public class BeaconNetworkTokenExchanger {
    
    @Inject
    private NetworkConfiguration network_configuration;
    
    private OidcProvider provider;
    
    @PostConstruct
    public void init() {
       if (ConfigurationProperties.BN_OIDC_ENDPOINT != null &&
           ConfigurationProperties.BN_CLIENT_ID != null &&
           ConfigurationProperties.BN_CLIENT_SECRET != null) {
           provider = new OidcProvider(ConfigurationProperties.BN_OIDC_ENDPOINT);
       } else {
            Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                    Level.INFO, "no Beacon Network server Identity Provider defined...");

       }
    }
    
    public List<String> exchange(String beaconId, List<String> headers) {
        return headers.stream().map(h -> exchangeHeader(beaconId, h)).toList();
    }
    
    private String exchangeHeader(String beaconId, String header) {
        if (header != null && header.startsWith("Bearer ")) {
            final String token = exchangeToken(beaconId, header.substring(7));
            if (token != null) {
                return "Bearer " + token;
            }
        }
        return header;
    }
    
    private String exchangeToken(String beaconId, String token) {
        
        final String endpoint = network_configuration.getEndpoints().get(beaconId);
        
        if (provider != null) {
            // restrict audience to the beacon's endpoint
            final String tkn = provider.doTokenExchange(ConfigurationProperties.BN_CLIENT_ID,
                    ConfigurationProperties.BN_CLIENT_SECRET, endpoint, token);
            if (tkn != null) {
                token = tkn;
            }
        }
        
        return token;
    }    
}
