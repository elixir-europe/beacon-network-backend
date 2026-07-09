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
 * The model for the OpenID Access Token Response (RFC6749 5.1)
 * that is returned by successful Token Exchange (RFC8693) operation.
 * 
 * @author Dmitry Repchevsky
 */

public class AccessTokenResponse {
    
    private String token_type;
    private String access_token;
    private String refresh_token;
    private String expires_in;
    private String scope;
    
    @JsonbProperty("token_type")
    public String getTokenType() {
        return token_type;
    }
    
    @JsonbProperty("token_type")
    public void setTokenType(String token_type) {
        this.token_type = token_type;
    }
    
    @JsonbProperty("access_token")
    public String getAccessToken() {
        return access_token;
    }
    
    @JsonbProperty("access_token")
    public void setAccessToken(String access_token) {
        this.access_token = access_token;
    }

    @JsonbProperty("refresh_token")
    public String getRefreshToken() {
        return refresh_token;
    }
    
    @JsonbProperty("refresh_token")
    public void setRefreshToken(String refresh_token) {
        this.refresh_token = refresh_token;
    }

    @JsonbProperty("expires_in")
    public String getExpiresIn() {
        return expires_in;
    }
    
    @JsonbProperty("expires_in")
    public void setExpiresIn(String expires_in) {
        this.expires_in = expires_in;
    }

    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }

}
