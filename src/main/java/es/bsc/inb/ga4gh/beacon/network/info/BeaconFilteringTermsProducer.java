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
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResults;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconMapResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.FilteringTerm;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.Resource;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfigUpdatedEvent;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import es.bsc.inb.ga4gh.beacon.network.config.URIInfoProducer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class BeaconFilteringTermsProducer {
    
    @Inject
    private URIInfoProducer uriInfoProducer;

    @Inject
    private ServiceConfigurationProducer configuration;

    @Inject
    private NetworkConfiguration network_configuration;

    @Inject
    private BeaconMapsProducer map_response;
    
    private Map<String, BeaconFilteringTermsResponse> filtering_terms;

    @PostConstruct
    public void init() {
        filtering_terms = new ConcurrentHashMap();
    }

    /**
     * Called when beacon network configuration has been updated.
     * 
     * @param event update event
     */
    public void onEvent(@ObservesAsync NetworkConfigUpdatedEvent event) {
        filtering_terms.clear();
    }
    
    private void addGlobalFilteringTerms() {
        final Map<String, BeaconFilteringTermsResponse> filters = 
                                        network_configuration.getFilteringTerms();
        
        final BeaconFilteringTermsResponse response = aggregateFilteringTerms(null, filters.values());
        final URI path = uriInfoProducer.getUriInfo().getBaseUri().resolve("filtering_terms");
        
        filtering_terms.put(path.toString(), response);
    }
    
    private void addEndpointsFilteringTerms() {
        final Map<String, BeaconMapResponse> maps = network_configuration.getMaps();
        if (maps != null) {
            final Map<String, List<BeaconFilteringTermsResponse>> aggregated_filtering_terms = new HashMap();
            final Map<String, String> endpoints = network_configuration.getEndpoints();
            for (Map.Entry<String, BeaconMapResponse> entry : maps.entrySet()) {
                final BeaconMap source_map = entry.getValue().getResponse();
                if (source_map != null) {
                    final Map<String, Endpoint> source_endpoints = source_map.getEndpointSets();
                    if (source_endpoints != null) {
                        final String beaconId = entry.getKey();
                        final String proxy_root = endpoints.get(beaconId);
                        final URI proxy_base_uri = URI.create(proxy_root);
                        for (Map.Entry<String, Endpoint> entry2 : source_endpoints.entrySet()) {
                            final String proxy_endpoint_id = entry2.getKey();
                            final Endpoint proxy_endpoint = entry2.getValue();
                            final String filtering_url = proxy_endpoint.getFilteringTermsUrl();
                            if (filtering_url != null) {
                                final String root_url = proxy_endpoint.getRootUrl();
                                final URI root_uri = root_url == null ? null : URI.create(root_url);
                                final String filtering_endpoint = getFilteringTermsURL(proxy_base_uri, root_uri, filtering_url);
                                final BeaconFilteringTermsResponse response = 
                                        network_configuration.loadFilteringTerms(filtering_endpoint);
                                if (response != null) {
                                    List<BeaconFilteringTermsResponse> list = aggregated_filtering_terms.get(proxy_endpoint_id);
                                    if (list == null) {
                                        aggregated_filtering_terms.put(proxy_endpoint_id, list = new ArrayList());
                                    }
                                    list.add(response);
                                }
                            }
                        }
                    }
                }
            }
            
            for (Map.Entry<String, List<BeaconFilteringTermsResponse>> entry : aggregated_filtering_terms.entrySet()) {
                final BeaconFilteringTermsResponse aggregated = aggregateFilteringTerms(entry.getKey(), entry.getValue());
                
                final Endpoint endpoint = map_response.maps().getResponse().getEndpointSets().get(entry.getKey());
                final String filtering_terms_url = endpoint.getFilteringTermsUrl();
                // TODO: null means we didn't generate well the url!
                if (filtering_terms_url != null) {
                    filtering_terms.put(filtering_terms_url, aggregated);
                }
            }
        }
    }

    private BeaconFilteringTermsResponse aggregateFilteringTerms(String scope,
            Collection<BeaconFilteringTermsResponse> filters) {
        
        final BeaconFilteringTermsResponse r = new BeaconFilteringTermsResponse();
        r.setMeta(configuration.serviceConfiguration().getMeta());
        
        final Map<String, Resource> resources = new HashMap();
        final Map<String, FilteringTerm> terms = new HashMap();
        
        for (BeaconFilteringTermsResponse response : filters) {
            final BeaconFilteringTermsResults proxy_results = response.getResponse();
            if (proxy_results != null) {
                final List<Resource> proxy_resources = proxy_results.getResources();
                if (proxy_resources != null) {
                    for (Resource resource : proxy_resources) {
                        final String id = resource.getId();
                        if (id != null) {
                            resources.put(id, resource);
                        }
                    }
                }
                final List<FilteringTerm> filtering_terms = proxy_results.getFilteringTerms();
                if (filtering_terms != null) {
                    for (FilteringTerm filtering_term : filtering_terms) {
                        final String id = filtering_term.getId();
                        if (id != null) {
                            final FilteringTerm duplicated = terms.get(id);
                            if (duplicated != null) {
                                final List<String> scopes = duplicated.getScopes();
                                final List<String> other_scopes = filtering_term.getScopes();
                                if (other_scopes != null) {
                                    for (String other_scope : other_scopes) {
                                        if (!scopes.contains(other_scope)) {
                                            scopes.add(other_scope);
                                        }
                                    }
                                }
                            } else {
                                List<String> scopes = filtering_term.getScopes();
                                if (scopes == null) {
                                    filtering_term.setScopes(scopes = new ArrayList());
                                }
                                if (scope != null && !scopes.contains(scope)) {
                                    scopes.add(scope);
                                }
                                terms.put(id, filtering_term);
                            }
                        }
                    }
                }
            }
        }

        final BeaconFilteringTermsResults results = new BeaconFilteringTermsResults();
        results.setResources(new ArrayList(resources.values()));
        results.setFilteringTerms(new ArrayList(terms.values()));

        r.setResponse(results);
        return r;
    }

    /**
     * Construct an absolute filtering terms URL to fetch filters from.
     * 
     * @param base_uri the base uri (beacon API uri)
     * @param root_uri entry type's root endpoint ('rootURL')
     * @param url entry type's endpoint url ('filteringTermsUrl')
     * 
     * @return absolute URI
     */
    private String getFilteringTermsURL(URI base_uri, URI root_uri, String url) {
        if (url != null) {
            try {
                URI uri = URI.create(url.replaceAll("\\{", "%7B").replaceAll("\\}", "%7D"));
                if (!uri.isAbsolute()) {
                    uri = getAbsoluteRootURI(base_uri, root_uri).resolve(uri);
                }
                return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
            } catch(IllegalArgumentException ex) {}
        }
        return null;
    }

    private URI getAbsoluteRootURI(URI base_uri, URI root_uri) {
        if (root_uri == null) {
            return base_uri;
        } else if (root_uri.isAbsolute()) {
            return root_uri;
        }
        return base_uri.resolve(root_uri);
    }

    @Produces
    public BeaconFilteringTermsResponse filteringTerms() {
        if (filtering_terms.isEmpty()) {
            addGlobalFilteringTerms();
            addEndpointsFilteringTerms();
        }
        return filtering_terms.get(uriInfoProducer.getUriInfo().getAbsolutePath().toString());
    }
}
