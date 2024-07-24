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
import java.util.List;

/**
 * @author Dmitry Repchevsky
 */

public class OauthProtectedResource {
    
    private String resource;
    private String client_id;
    private List<String> authorization_servers;
    
    public String getResource() {
        return resource;
    }
    
    public void setResource(String resource) {
        this.resource = resource;
    }

    @JsonbProperty("client_id")
    public String getClientId() {
        return client_id;
    }
    
    @JsonbProperty("client_id")
    public void setClientId(String client_id) {
        this.client_id = client_id;
    }
    
    @JsonbProperty("authorization_servers")
    public List<String> getAuthorizationServers() {
        return authorization_servers;
    }
    
    @JsonbProperty("authorization_servers")
    public void setAuthorizationServers(List<String> authorization_servers) {
        this.authorization_servers = authorization_servers;
    }
}
