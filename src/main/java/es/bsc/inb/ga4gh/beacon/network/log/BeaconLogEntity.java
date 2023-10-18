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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * @author Dmitry Repchevsky
 */

@Entity(name = "log")
@Table(name = "log")
public class BeaconLogEntity implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    
    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP", 
            insertable=false, updatable=false)
    private ZonedDateTime timestamp;
    
    @Enumerated(EnumType.ORDINAL)
    private REQUEST_TYPE type;

    @Enumerated(EnumType.ORDINAL)
    private METHOD method;
    
    private String url;
    
    @Column(columnDefinition = "text")
    private String request;
    
    @Column(columnDefinition = "text")
    private String response;

    private Integer code;
    private String message;
    
    public BeaconLogEntity() {}
    
    public BeaconLogEntity(REQUEST_TYPE type, METHOD method, String url,
            Integer code, String message, String request, String response) {
        
        this.type = type;
        this.method = method;
        this.url = url;
        this.code = code;
        this.message = message;
        this.request = request;
        this.response = response;
    }

    public long getId() {
        return id;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public REQUEST_TYPE getType() {
        return type;
    }
    
    public void setType(REQUEST_TYPE type) {
        this.type = type;
    }

    public METHOD getMethod() {
        return method;
    }
    
    public void setMethod(METHOD method) {
        this.method = method;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }

    public String getRequest() {
        return request;
    }
    
    public void setRequest(String request) {
        this.request = request;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }

    public static enum METHOD {
        GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE;
    }
    
    public static enum REQUEST_TYPE {
        METADATA, QUERY;
    }
}
