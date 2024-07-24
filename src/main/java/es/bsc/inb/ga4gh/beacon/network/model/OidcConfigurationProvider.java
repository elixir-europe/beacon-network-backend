/**
 * *****************************************************************************
 * Copyright (C) 2024 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
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

package es.bsc.inb.ga4gh.beacon.network.model;

import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Minimal model for the OpenID Provider Configuration Response object.
 * 
 * @author Dmitry Repchevsky
 */

public class OidcConfigurationProvider {
    
    private String issuer;
    private String token_endpoint;
    
    public String getIssuer() {
        return issuer;
    }
    
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    @JsonbProperty("token_endpoint")
    public String getTokenEndpoint() {
        return token_endpoint;
    }
    
    @JsonbProperty("token_endpoint")
    public void setTokenEndpoint(String token_endpoint) {
        this.token_endpoint = token_endpoint;
    }
}
