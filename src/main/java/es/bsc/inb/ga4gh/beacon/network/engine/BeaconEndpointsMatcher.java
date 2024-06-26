package es.bsc.inb.ga4gh.beacon.network.engine;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.BeaconMap;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.Endpoint;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.RelatedEndpoint;
import es.bsc.inb.ga4gh.beacon.network.info.BeaconMapsProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Repchevsky
 */

@ApplicationScoped
public class BeaconEndpointsMatcher {

    @Inject
    private EndpointsDefinitions endpoints;
    
    @Inject
    private BeaconMapsProducer map_response;
    
    public Map<String, Map.Entry<String, String>> match(HttpServletRequest request) {

        final StringBuffer url = request.getRequestURL();

        final Set<String> endpointTypes = getEndpointTypes(url.toString());
        if (endpointTypes.isEmpty()) {
            return Collections.EMPTY_MAP;
        }

        final String path = request.getPathInfo();
        
        final Map<String, Map.Entry<String, String>> matched_endpoints = new HashMap();

        final Map<String, Map<String, String>> all_endpoints = endpoints.getEndpoints();
        for (Map.Entry<String, Map<String, String>> entry : all_endpoints.entrySet()) {
            final Map<String, String> urls = entry.getValue();
            for (Map.Entry<String, String> urlEntry : urls.entrySet()) {
                if (endpointTypes.contains(urlEntry.getKey()) && 
                        match(path, urlEntry.getValue())) {
                    matched_endpoints.put(entry.getKey(), urlEntry);
                }
            }
        }

        return matched_endpoints;
    }

    /**
     * Find if the request path matches any BN endpoints.
     * 
     * @param path current request URL path
     * 
     * @return a set of typed endpoints (e.g. 'genomicVariant', 
     * 'genomicVariant:genomicVariant', 'genomicVariant:analysis') or null
     */
    private Set<String> getEndpointTypes(String path) {
        final Set<String> types = new HashSet();
        
        final BeaconMap map = map_response.maps().getResponse();
        if (map != null) {
            final Map<String, Endpoint> endpoints = map.getEndpointSets();
            if (endpoints != null) {
                for (Map.Entry<String, Endpoint> entry : endpoints.entrySet()) {
                    final Endpoint endpoint = entry.getValue();
                    String entryType = endpoint.getEntryType();
                    if (entryType == null) {
                        entryType = entry.getKey();
                    }
                    
                    if (match(path, endpoint.getRootUrl())) {
                        types.add(entryType);
                        continue;
                    }
                    if (match(path, endpoint.getSingleEntryUrl())) {
                        types.add(entryType + ":" + entryType);
                        continue;
                    }
                    
                    final Map<String, RelatedEndpoint> related_endpont = endpoint.getEndpoints();
                    if (related_endpont != null) {
                        for (Map.Entry<String, RelatedEndpoint> rel_entry : related_endpont.entrySet()) {
                            final RelatedEndpoint rel_endpoint = rel_entry.getValue();
                            if (match(path, rel_endpoint.getUrl())) {
                                String relEntryType = rel_endpoint.getReturnedEntryType();
                                if (relEntryType == null) {
                                    relEntryType = rel_entry.getKey();
                                }
                                types.add(entryType + ":" + relEntryType);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return types;
    }

    /**
     * Check if the request path matches some endpoint template.
     * 
     * @param path the request path to test
     * @param endpoint the template to test
     * 
     * @return true if matches
     */
    private boolean match(String path, String endpoint) {
        if (endpoint != null) {
            final String[] names1 = path.split("/");
            final String[] names2;
            if (path.indexOf("//") > 0) {
                names2 = endpoint.split("/");
            } else {
                final int idx = endpoint.lastIndexOf("//");
                names2 = endpoint.substring(idx < 0 ? 0 : idx + 1).split("/");
            }
            if (names1.length == names2.length) {
                for (int i = 0, n = names2.length; i < n; i++) {
                    if ((names2[i].startsWith("{") && names2[i].endsWith("}")) || 
                         names1[i].equals(names2[i])) {
                        continue;
                    }
                    return false;
                }
                return true;
            }
        }
        return false;
    }

}
