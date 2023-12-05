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

package es.bsc.inb.ga4gh.beacon.network.config;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.ServiceConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconEntryTypesResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponseMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconMapResponse;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLog;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity.METHOD;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity.REQUEST_TYPE;
import es.bsc.inb.ga4gh.beacon.network.model.BeaconNetworkInfoResponse;
import es.bsc.inb.ga4gh.beacon.validator.BeaconMetadataSchema;
import es.bsc.inb.ga4gh.beacon.validator.BeaconMetadataValidator;
import es.bsc.inb.ga4gh.beacon.validator.BeaconValidationMessage;
import es.bsc.inb.ga4gh.beacon.validator.BeaconValidationErrorType;
import es.bsc.inb.ga4gh.beacon.validator.ValidationErrorsCollector;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This configuration bean runs on the application deploy 
 * (before any request is executed) and collects configurations of 
 * backed beacons.
 * 
 * @author Dmitry Repchevsky
 */

@Singleton
public class NetworkConfiguration {

    public final static String BEACON_NETWORK_CONFIG_DIR_PROPERTY_NAME = "BEACON_NETWORK_CONFIG_DIR";
    
    public final static String BEACON_NETWORK_CONFIG_DIR = "BEACON-INF/";
    public final static String BEACON_NETWORK_CONFIG_FILE = "beacon-network.json";

    @Inject
    private Event<NetworkConfigUpdatedEvent> config_updated_event;

    @Inject    
    private BeaconMetadataValidator validator;

    @Inject
    private BeaconLog log;

    /**
     * Map of endponts' URLs by the beacon identifier.
     * ( e.g. Map&lt;'es.elixir.bsc.beacon', 'https://beacons.bsc.es/beacon/v2.0.0/'&gt; )
     */
    private Map<String, String> endpoints;
    
    private Map<BeaconMetadataSchema, Map<String, ? extends BeaconInformationalResponse>> metadata;
    
    private Map<String, List<BeaconValidationMessage>> errors;
    
    /**
     * Hashcodes for the Beacons' metadata endpoints.
     * 
     * These hashcodes are used to dynamically update configuration on
     * changes of the metadata of participating Beacons.
     * 
     * ( e.g. Map&lt;'https://beacons.bsc.es/beacon/v2.0.0/info', 1234&gt; )
     */
    private Map<String, Integer> hashes;

    @PostConstruct
    public void init() {
        endpoints = new ConcurrentHashMap();
        metadata = new ConcurrentHashMap();
        errors = new ConcurrentHashMap();
        hashes = new ConcurrentHashMap();

        Arrays.stream(BeaconMetadataSchema.values()).forEach(s -> metadata.put(s, new ConcurrentHashMap()));
    }

    /**
     * Forcible reloads all beacons' metadata.
     * 
     * 
     */
    public void updateBeacons() {
        hashes.clear();
        errors.clear();
        endpoints.values().forEach(e -> updateBeacon(e));
    }
    
    /**
     * Called when there are changes in the beacon network configuration.
     * 
     * @param event the event that contains new beacon network endpoints
     */
    public void onEvent(@ObservesAsync NetworkConfigChangedEvent event) {

        cleanRemovedBeacons(event);
        updateBeacons(event);

        config_updated_event.fireAsync(new NetworkConfigUpdatedEvent());
    }

    private void cleanRemovedBeacons(NetworkConfigChangedEvent event) {
        final Iterator<Map.Entry<String, String>> iter = endpoints.entrySet().iterator();
        
        loop:
        while (iter.hasNext()) {
            final Map.Entry<String, String> entry = iter.next();
            final String endpoint = entry.getValue();
            for (String beacon : event.beacons) {
                if (beacon.equals(endpoint)) {
                    continue loop; // found;
                }
            }
            final String beacon_id = entry.getKey();
            
            iter.remove();

            metadata.values().forEach(m -> m.remove(beacon_id));
            
            errors.remove(endpoint);
        }
    }
    
    private void updateBeacons(NetworkConfigChangedEvent event) {
        for (String endpoint : event.beacons) {
            updateBeacon(endpoint);
        }
    }
    
    /**
     * Update all Beacon's metadata
     * 
     * @param endpoint Beacon's API endpoint
     */
    private void updateBeacon(String endpoint) {
        final List<BeaconValidationMessage> err = new ArrayList();
        if (updateMetadata(endpoint, BeaconMetadataSchema.BEACON_INFO_RESPONSE_SCHEMA, err)) {
            final String beaconId = getBeaconId(endpoint); // beaconId can't be null 
            for (BeaconMetadataSchema schema : BeaconMetadataSchema.values()) {
                if (BeaconMetadataSchema.BEACON_INFO_RESPONSE_SCHEMA != schema) {
                    final int nerrors = err.size();
                    updateMetadata(endpoint, schema, err);
                    if (beaconId != null && nerrors != err.size()) {
                        metadata.get(schema).remove(beaconId);
                    }
                }
            }
            if (err.isEmpty()) {
                errors.remove(endpoint);
            } else {
                errors.put(endpoint, err);
            }
        }
    }

    /**
     * Updats Beacon's metadata.
     * 
     * @param endpoint Beacon's API endpoint
     * @param schema Beacon's metadata type (INFO, MAP, ENTRY_TYPES, etc.)
     * @param errors Loading, Json Schema validation or parsing errors
     * 
     * @return false if no changes in metadata happened, true otherwise
     */
    private boolean updateMetadata(String endpoint, BeaconMetadataSchema schema,
            List<BeaconValidationMessage> errors) {
        
        boolean changed = true;
        
        List<BeaconValidationMessage> err = new ArrayList();
        final String json = validator.loadMetadata(endpoint, schema, new ValidationErrorsCollector(err));
        errors.addAll(err);
        
        final BeaconLogEntity log_entry = new BeaconLogEntity(REQUEST_TYPE.METADATA, 
                METHOD.GET, endpoint + validator.ENDPOINTS.get(schema), 
                err.isEmpty() ? (Integer)200 : err.get(0).code,
                err.isEmpty() ? null : err.get(0).message, null, json);

        if (json != null) {
            final String metadata_endpoint = endpoint + validator.ENDPOINTS.get(schema);
            
            final Integer hash = hashes.get(metadata_endpoint);
            if (Integer.valueOf(json.hashCode()).equals(hash)) {
                changed = false;
                log_entry.setResponse(null);
                log_entry.setCode(304); // HTTP Not Modified
            } else {
                try (JsonReader reader = Json.createReader(new StringReader(json))) {
                    final JsonValue value = reader.readValue();
                    err = validator.validate(schema, value);
                    if (err.isEmpty()) {
                        final BeaconInformationalResponse response = validator.parseMetadata(json, schema);
                        if (response != null) {
                            hashes.put(metadata_endpoint, json.hashCode());
                            final BeaconInformationalResponseMeta rmeta = response.getMeta();
                            if (rmeta != null) {
                                final String beacon_id = rmeta.getBeaconId();
                                if (beacon_id != null) {
                                    endpoints.put(beacon_id, endpoint);
                                    final Map<String, BeaconInformationalResponse> map = (Map<String, BeaconInformationalResponse>)metadata.get(schema);
                                    map.put(beacon_id, response);
                                }
                            } else { // should never happen if json schema is correct!
                                log_entry.setMessage("missed BeaconInformationalResponseMeta in response");
                                err.add(new BeaconValidationMessage(
                                        BeaconValidationErrorType.CONTENT_ERROR,
                                        null, metadata_endpoint, null,
                                        log_entry.getMessage()));
                                
                            }
                        }
                    } else {
                        log_entry.setMessage(err.get(err.size() - 1).message);
                    }
                    errors.addAll(err);
                } catch (Exception ex) {
                    log_entry.setMessage(String.format("error parsing response from %s", ex.getMessage()));
                    errors.add(new BeaconValidationMessage(
                            BeaconValidationErrorType.CONTENT_ERROR, null, metadata_endpoint, null,
                            log_entry.getMessage()));
                }
            }
        }
        
        log.log(log_entry);
        
        return changed;
    }
    
    public Map<String, BeaconNetworkInfoResponse> getInfos() {
        return (Map<String, BeaconNetworkInfoResponse>) metadata.get(BeaconMetadataSchema.BEACON_INFO_RESPONSE_SCHEMA);
    }

    public Map<String, ServiceConfiguration> getConfigurations() {
        return (Map<String, ServiceConfiguration>) metadata.get(BeaconMetadataSchema.BEACON_CONFIGURATION_SCHEMA);
    }    

    public Map<String, BeaconMapResponse> getMaps() {
        return (Map<String, BeaconMapResponse>) metadata.get(BeaconMetadataSchema.BEACON_MAP_RESPONSE_SCHEMA);
    }

    public Map<String, BeaconEntryTypesResponse> getEntries() {
        return (Map<String, BeaconEntryTypesResponse>) metadata.get(BeaconMetadataSchema.BEACON_ENTRY_TYPES_SCHEMA);
    }

    public Map<String, BeaconFilteringTermsResponse> getFilteringTerms() {
        return (Map<String, BeaconFilteringTermsResponse>) metadata.get(BeaconMetadataSchema.BEACON_FILTERING_TERMS_SCHEMA);
    }

    public Map<String, String> getEndpoints() {
        return endpoints;
    }

    /**
     * Get the metadata JSON Schema parsing errors.
     * 
     * @return the map of parsing errors using the beacon's endpoint as a key.
     */
    public Map<String, List<BeaconValidationMessage>> getErrors() {
        return errors;
    }

    private String getBeaconId(String endpoint) {
        for (Map.Entry<String, String> entry : endpoints.entrySet()) {
            if (endpoint.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Load filtering terms by the endpoint.
     * 
     * Unlike global '/filtering_terms', per-entry filtering terms
     * (e.g. '/individuals/filtering_terms') have no fixed endpoint and
     * are not managed by this class.
     * 
     * @param endpoint
     * @return 
     */
    public BeaconFilteringTermsResponse loadFilteringTerms(String endpoint) {
        try {
            final List<BeaconValidationMessage> err = new ArrayList();
            final String json = validator.loadMetadata(endpoint, new ValidationErrorsCollector(err));
            if (err.isEmpty()) {
                return (BeaconFilteringTermsResponse)validator.parseMetadata(json, BeaconMetadataSchema.BEACON_FILTERING_TERMS_SCHEMA);
            }
        } catch(Exception ex) {
            Logger.getLogger(NetworkConfiguration.class.getName()).log(
                    Level.SEVERE, "error loading from {0} {1}", 
                    new Object[]{endpoint, ex.getMessage()});
        }
        return null;
    }
}
