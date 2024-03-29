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

package es.bsc.inb.ga4gh.beacon.network.info;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.BeaconMap;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.Endpoint;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.RelatedEndpoint;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.ServiceConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconMapResponse;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfigUpdatedEvent;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import es.bsc.inb.ga4gh.beacon.network.config.URIInfoProducer;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class BeaconMapsProducer {
    
    @Inject
    private ServiceConfiguration configuration;
    
    @Inject
    private NetworkConfiguration network_configuration;
    
    @Inject
    private URIInfoProducer uriInfoProducer;
    
    private BeaconMapResponse map_response;
    
    public void onEvent(@ObservesAsync NetworkConfigUpdatedEvent event) {
        // regenerate /map when metadata was done / updated
        map_response = null;
    }

    private BeaconMap generate() {
        final BeaconMap aggregated_map = new BeaconMap();
        final Map<String, Endpoint> aggregated_endpoints = new HashMap();
        aggregated_map.setEndpointSets(aggregated_endpoints);
        
        final Map<String, String> endpoints = network_configuration.getEndpoints();
        final Map<String, BeaconMapResponse> maps = network_configuration.getMaps();
        if (maps != null) {
            for (Map.Entry<String, BeaconMapResponse> entry : maps.entrySet()) {
                final String beaconId = entry.getKey();
                final String proxy_root = endpoints.get(beaconId);
                final BeaconMapResponse response = entry.getValue();
                final BeaconMap source_map = response.getResponse();
                if (source_map != null) {
                    final Map<String, Endpoint> source_endpoints = source_map.getEndpointSets();
                    if (source_endpoints != null) {
                        aggregate(URI.create(proxy_root), source_endpoints, aggregated_endpoints);
                    }
                }
            }
        }
        return aggregated_map;
    }
    
    private void aggregate(
            URI proxy_base_uri,
            Map<String, Endpoint> proxy_endpoints,
            Map<String, Endpoint> aggregated_endpoints) {
        
        for (Map.Entry<String, Endpoint> entry : proxy_endpoints.entrySet()) {
            final String proxy_endpoint_id = entry.getKey();
            final Endpoint proxy_endpoint = entry.getValue();
            Endpoint aggregated_endpoint = aggregated_endpoints.get(proxy_endpoint_id);
            if (aggregated_endpoint == null) {
                aggregated_endpoint = new Endpoint();
                aggregated_endpoint.setEntryType(proxy_endpoint.getEntryType());
                
                final String root_url = resolve(Paths.get(URI.create(
                        proxy_endpoint.getRootUrl()).getPath()).getFileName().toString());
                final String single_entry_url = resolve(relativize(
                        proxy_base_uri, proxy_endpoint.getSingleEntryUrl()));
                
                aggregated_endpoint.setRootUrl(root_url);
                aggregated_endpoint.setSingleEntryUrl(single_entry_url);

                aggregated_endpoints.put(proxy_endpoint_id, aggregated_endpoint);
            }
            aggregate(proxy_endpoint, aggregated_endpoint);
        }
    }

    private void aggregate(Endpoint proxy_endpoint, Endpoint aggregated_endpoint) {
        
        final URI proxy_root_uri = URI.create(proxy_endpoint.getRootUrl());
        final String aggregated_root_url = aggregated_endpoint.getRootUrl();
        
        String filtering_terms_url = aggregated_endpoint.getFilteringTermsUrl();
        if (filtering_terms_url == null) {
            filtering_terms_url = relativize(proxy_root_uri, proxy_endpoint.getFilteringTermsUrl());
            if (filtering_terms_url != null) {
                aggregated_endpoint.setFilteringTermsUrl(aggregated_root_url + "/filtering_terms");
            }
        }

        Map<String, RelatedEndpoint> aggregated_rel_endpoints = aggregated_endpoint.getEndpoints();
        if (aggregated_rel_endpoints == null) {
            aggregated_endpoint.setEndpoints(aggregated_rel_endpoints = new HashMap());
        }
        aggregate(proxy_endpoint, aggregated_endpoint, aggregated_rel_endpoints);
    }

    private void aggregate(Endpoint proxy_endpoint, Endpoint aggregated_endpoint, 
            Map<String, RelatedEndpoint> aggregated_rel_endpoints) {
        
        final URI proxy_root_uri = URI.create(proxy_endpoint.getRootUrl());
        final String aggregated_root_url = aggregated_endpoint.getRootUrl();
        
        final Map<String, RelatedEndpoint> rel_endpoints = proxy_endpoint.getEndpoints();
        if (rel_endpoints != null) {
            for (Map.Entry<String, RelatedEndpoint> rel_entry : rel_endpoints.entrySet()) {
                final RelatedEndpoint rel_endpoint = rel_entry.getValue();

                final RelatedEndpoint aggregated_rel_endpoint = new RelatedEndpoint();
                aggregated_rel_endpoint.setReturnedEntryType(rel_endpoint.getReturnedEntryType());

                final String rel_url = relativize(proxy_root_uri, rel_endpoint.getUrl());
                aggregated_rel_endpoint.setUrl(aggregated_root_url + "/" + rel_url);

                aggregated_rel_endpoints.put(rel_entry.getKey(), aggregated_rel_endpoint);
            }
        }
    }

    /**
     * Resolves proxied Beacon URL to the Beacon Network one.
     * 
     * @param proxy_base_uri proxied Beacon root endpoint URL
     * @param url proxied Beacon endpoint URL to be resolved
     * 
     * @return Beacon Network endpoint URL 
     */
    private String relativize(URI proxy_base_uri, String url) {
        if (url != null) {
            url = url.replaceAll("\\{", "%7B");
            url = url.replaceAll("\\}", "%7D");
            try {
                final URI proxy_url = URI.create(URI.create(url).getRawPath());
                final URI rel_url = URI.create(proxy_base_uri.getRawPath()).relativize(proxy_url);
                return URLDecoder.decode(rel_url.toString(), StandardCharsets.UTF_8);
            } catch(IllegalArgumentException ex) {}
        }
        return null;
    }

    private String resolve(String rel_url) {
        if (rel_url != null) {
            rel_url = rel_url.replaceAll("\\{", "%7B");
            rel_url = rel_url.replaceAll("\\}", "%7D");
            final URI base_uri = uriInfoProducer.getUriInfo().getBaseUri();
            return URLDecoder.decode(base_uri.resolve(rel_url).toString(), StandardCharsets.UTF_8);
        }
        return null;
    }
    
    @Produces
    public BeaconMapResponse maps() {
        if (map_response == null) {
            map_response = new BeaconMapResponse();
            map_response.setMeta(configuration.getMeta());
            map_response.setResponse(generate());
        }
        return map_response;
    }
}
