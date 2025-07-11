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

import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResults;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.FilteringTerm;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.Resource;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfigUpdatedEvent;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class BeaconFilteringTermsProducer {
    
    @Inject
    private ServiceConfigurationProducer configuration;

    @Inject
    private NetworkConfiguration network_configuration;
    
    private BeaconFilteringTermsResponse filtering_terms;

    /**
     * Called when beacon network configuration has been updated.
     * 
     * @param event update event
     */
    public void onEvent(@ObservesAsync NetworkConfigUpdatedEvent event) {
        filtering_terms = null;
    }
    
    private void setGlobalFilteringTerms() {
        final Map<String, BeaconFilteringTermsResponse> filters = 
                                        network_configuration.getFilteringTerms();
        
        filtering_terms = new BeaconFilteringTermsResponse();
        filtering_terms.setMeta(configuration.serviceConfiguration().getMeta());
        
        final Map<String, Resource> resources = new HashMap();
        final Map<String, FilteringTerm> terms = new HashMap();
        
        for (BeaconFilteringTermsResponse response : filters.values()) {
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

        filtering_terms.setResponse(results);
    }

    @Produces
    public BeaconFilteringTermsResponse filteringTerms() {
        if (filtering_terms == null) {
            setGlobalFilteringTerms();
        }
        return filtering_terms;
    }
}
