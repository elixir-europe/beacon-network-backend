/**
 * *****************************************************************************
 * Copyright (C) 2022 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
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

package es.bsc.inb.ga4gh.beacon.network.engine;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.common.SchemaReference;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.BeaconMap;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.Endpoint;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.RelatedEndpoint;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconEntryTypesResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconMapResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.EntryTypeDefinition;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.EntryTypes;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfigUpdatedEvent;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class EndpointsDefinitions {
    
    @Inject 
    private NetworkConfiguration configuration;
    
    /**
     * The map that contains all beacons' endpoints.
     * Map<'beaconId', Map<'url', 'entryType'>>
     * Map<'org.progenetix.beacon', 
     *  'genomicVariant:analysis', Map<'https://progenetix.org/beacon//analyses/{id}/g_variants'>>
     */
    private Map<String, Map<String, String>> endpoints;
    
    /**
     * The map that contains all beacon entities' schemas.
     * Map<'beaconId', Map<'entryType', 'schemaUrl'>>
     */
    private Map<String, Map<String, String>> entities;

    public Map<String, Map<String, String>> getEndpoints() {
        return endpoints;
    }

    public Map<String, Map<String, String>> getEntities() {
        return entities;
    }

    @PostConstruct
    public void init() {
        entities = new HashMap();
        endpoints = new HashMap();
    }
    
    /**
     * Called when beacon network configuration has been updated.
     * 
     * @param event update event
     */
    public void onEvent(@ObservesAsync NetworkConfigUpdatedEvent event) {
        endpoints = getEndpoints(configuration);
        entities = getEntities(configuration);
    }
    
    private Map<String, Map<String, String>> getEntities(NetworkConfiguration configuration) {
        final Map<String, Map<String, String>> entities = new HashMap();
        
        final Map<String, BeaconEntryTypesResponse> entries = configuration.getEntries();
        for (Map.Entry<String, BeaconEntryTypesResponse> entry : entries.entrySet()) {
            final String beaconId = entry.getKey();
            final Map<String, String> schemas = new HashMap();
            entities.put(beaconId, schemas);
            
            final BeaconEntryTypesResponse entry_response = entry.getValue();
            final EntryTypes entry_types = entry_response.getResponse();
            if (entry_types != null) {
                final Map<String, EntryTypeDefinition> definitions = entry_types.getEntryTypes();
                if (definitions != null) {
                    for (Map.Entry<String, EntryTypeDefinition> definition : definitions.entrySet()) {
                        final String name = definition.getKey();
                        final EntryTypeDefinition entryType = definition.getValue();
                        final SchemaReference schemaRef = entryType.getDefaultSchema();
                        if (schemaRef != null) {
                            final String url = schemaRef.getReferenceToSchemaDefinition();
                            if (url != null) {
                                schemas.put(name, url);
                            }
                        }
                    }
                }
            }
        }
        return entities;
    }
    
    private Map<String, Map<String, String>> getEndpoints(NetworkConfiguration configuration) {
        final Map<String, Map<String, String>> endpoints = new HashMap();
        
        final Map<String, BeaconMapResponse> maps = configuration.getMaps();
        for (Map.Entry<String, BeaconMapResponse> entry : maps.entrySet()) {
            final String beaconId = entry.getKey();
            final BeaconMapResponse map_response = entry.getValue();
            final BeaconMap map = map_response.getResponse();
            if (map != null) {
                final Map<String, Endpoint> endpoint_sets = map.getEndpointSets();
                if (endpoint_sets != null) {
                    Map<String, String> urls = endpoints.get(beaconId);
                    if (urls == null) {
                        endpoints.put(beaconId, urls = new HashMap());
                    }
                    addEndpoints(beaconId, urls, endpoint_sets);
                }
            }
        }
        return endpoints;
    }
    
    private void addEndpoints(String beaconId, Map<String, String> urls,
            Map<String, Endpoint> endpoints) {

        final String apiRoot = configuration.getEndpoints().get(beaconId);
        
        for (Map.Entry<String, Endpoint> entry : endpoints.entrySet()) {
            final Endpoint endpoint = entry.getValue();

            String entryType = endpoint.getEntryType();
            if (entryType == null) {
                entryType = entry.getKey();
            }

            URI endpointRoot = URI.create(apiRoot);
            
            final String rootURL = endpoint.getRootUrl();
            if (rootURL != null) {
                try {
                    URI rootURI = URI.create(rootURL);
                    if (!rootURI.isAbsolute()) {
                        rootURI = endpointRoot.resolve(rootURI);
                    }
                    
                    final String root = rootURI.toString();
                    final int lastIdx = root.endsWith("/") ? root.length() - 1 : root.length();
                    final int lastPathIdx = root.lastIndexOf('/', lastIdx);
                    if (lastPathIdx > 0) {
                        final String baseURI = root.substring(0, lastPathIdx);
                        endpointRoot = URI.create(baseURI);
                        urls.put(entryType, baseURI + "/" + root.substring(lastPathIdx, lastIdx));
                    }
                } catch (Exception ex) {
                    Logger.getLogger(EndpointsDefinitions.class.getName()).log(Level.WARNING, null, ex);
                }
            }

            final String singleEntryUrl = endpoint.getSingleEntryUrl();
            if (singleEntryUrl != null) {
                putEndpoint(urls, endpointRoot, entryType + ":" + entryType, singleEntryUrl);
            }

            final Map<String, RelatedEndpoint> related_endpoints = endpoint.getEndpoints();
            if (related_endpoints != null) {
                for (Map.Entry<String, RelatedEndpoint> rel_entry : related_endpoints.entrySet()) {
                    final RelatedEndpoint rel_endpoint = rel_entry.getValue();
                    final String url = rel_endpoint.getUrl();
                    if (url != null && !url.isEmpty()) {
                        String relEntryType = rel_endpoint.getReturnedEntryType();
                        if (relEntryType == null) {
                            relEntryType = rel_entry.getKey();
                        }
                        putEndpoint(urls, endpointRoot, entryType + ":" + relEntryType, url);
                    }
                }
            }
        }
    }
    
    /**
     * Remove the endpoint template, so it wont be considered for the execution.
     * 
     * @param uri the endpoint template
     */
    public void removeEndpoint(String uri) {
        for (Map.Entry<String, Map<String, String>> entry : endpoints.entrySet()) {
            final Map<String, String> urls = entry.getValue();
            final Iterator<Map.Entry<String, String>> iter = urls.entrySet().iterator();
            while (iter.hasNext()) {
                final Map.Entry<String, String> urlEntry = iter.next();
                if (uri.equals(urlEntry.getValue())) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * 
     * @param urls
     * @param endpointRoot
     * @param endpointType the typed endpoint (e.g. 'genomicVariant', 
     * @param url
     * 'genomicVariant:genomicVariant', 'genomicVariant:analysis')
     */
    private void putEndpoint(Map<String, String> urls, 
            URI endpointRoot, String endpointType, String url) {
        try {
            URI uri = URI.create(url.replace("{", "%7B").replace("}", "%7D"));
            if (!uri.isAbsolute()) {
                // resolve relative url
                uri = endpointRoot.resolve(url);
            }
            uri = endpointRoot.relativize(uri);
            urls.put(endpointType, endpointRoot + "//" + URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            Logger.getLogger(EndpointsDefinitions.class.getName()).log(Level.WARNING, null, ex);
        }
    }
}
