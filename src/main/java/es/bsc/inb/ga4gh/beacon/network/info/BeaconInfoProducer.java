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

package es.bsc.inb.ga4gh.beacon.network.info;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInfoResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInfoResults;
import es.bsc.inb.ga4gh.beacon.network.config.BeaconNetworkConfiguration;
import static es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties.BEACON_NETWORK_INFO_FILE;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfigUpdatedEvent;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import es.bsc.inb.ga4gh.beacon.network.model.BeaconNetworkInfoResponse;
import es.bsc.inb.ga4gh.beacon.validator.BeaconValidationMessage;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class BeaconInfoProducer implements Serializable {

    @Inject 
    private BeaconNetworkConfiguration cfg;

    @Inject
    private NetworkConfiguration config;

    private BeaconNetworkInfoResponse beacon_info;
    
    @PostConstruct
    public void init() {
        beacon_info = cfg.loadConfiguration(BEACON_NETWORK_INFO_FILE, BeaconNetworkInfoResponse.class);

    }

    /**
     * Called when beacon network configuration has been updated.
     * 
     * @param event update event
     */
    public void onEvent(@ObservesAsync NetworkConfigUpdatedEvent event) {
        injectResponses();
    }
    
    /**
     * Inject Beacons` '/info' responses into the BN '/info'
     */
    private void injectResponses() {
        if (beacon_info != null) {
            synchronized(BeaconNetworkInfoResponse.class) {
                if (beacon_info != null) {
                    final List<BeaconInfoResponse> responses = new ArrayList();
                    for (BeaconNetworkInfoResponse response : config.getInfos().values()) {
                        responses.add(response);
                    }
                    beacon_info.setResponses(responses.isEmpty() ? null : responses);
                    updateMetadataParsingErrors();
                }
            }
        }
    }

    public void updateMetadataParsingErrors() {
        BeaconInfoResults results = beacon_info.getResponse();
        if (results == null) {
            beacon_info.setResponse(results = new BeaconInfoResults());
        }
            
        final Map<String, List<BeaconValidationMessage>> errors = config.getErrors();
        if (errors.isEmpty()) {
            results.setInfo(null);
        } else {
            final JsonArrayBuilder endpoints = Json.createArrayBuilder();
            for (Map.Entry<String, List<BeaconValidationMessage>> entry : errors.entrySet()) {
                final JsonObjectBuilder endpoint = Json.createObjectBuilder();
                endpoint.add("endpoint", entry.getKey());
                final JsonArrayBuilder arr = Json.createArrayBuilder();
                for (BeaconValidationMessage err : entry.getValue()) {
                    final JsonObjectBuilder obj = Json.createObjectBuilder();
//                    if (err.id != null && err. != null) {
//                        final String schema = err.id.getPath();
//                        obj.add("schema", schema + "#" + err.pointer);
//                    }
                    if (err.code != null) {
                        obj.add("code", err.code);
                    }
                    if (err.path != null) {
                        obj.add("path", err.path);
                    }
                    if (err.location != null) {
                        obj.add("location", err.location);
                    }
                    if (err.message != null) {
                        obj.add("message", err.message);
                    }
                    arr.add(obj);
                }
                endpoint.add("errors", arr);
                endpoints.add(endpoint);
            }
            results.setInfo(Json.createObjectBuilder().add("metadata_errors", endpoints).build());
        }
    }

    @Produces
    public BeaconNetworkInfoResponse beaconInfo() {        
        return beacon_info;
    }

}
