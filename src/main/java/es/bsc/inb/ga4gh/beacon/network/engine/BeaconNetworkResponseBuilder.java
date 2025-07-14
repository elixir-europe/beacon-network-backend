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

package es.bsc.inb.ga4gh.beacon.network.engine;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.common.SchemaPerEntity;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconQueryFilter;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconRequestMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconRequestQuery;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.AbstractBeaconResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconCollections;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconCollectionsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconError;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconErrorResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponseMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconReceivedRequestSummary;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResponseMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResponseSummary;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResultset;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResultsets;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResultsetsResponse;
import es.bsc.inb.ga4gh.beacon.network.info.BeaconInfoProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Dmitry Repchevsky
 */

@ApplicationScoped
public class BeaconNetworkResponseBuilder {
    
    @Inject
    private BeaconInfoProducer beacon_info;
    
    public Response build(
            BeaconRequestMeta meta, 
            BeaconRequestQuery query, 
            List<AbstractBeaconResponse> beacons_responses) {

        final String include_resultset_responses = query.getIncludeResultsetResponses();

        AbstractBeaconResponse aggregated;
        
        if (beacons_responses.stream().anyMatch(BeaconCollectionsResponse.class::isInstance)) {
            final BeaconCollectionsResponse response = new BeaconCollectionsResponse();
            response.setMeta(new BeaconResponseMeta());
            response.setResponseSummary(new BeaconResponseSummary(false));
            
            for (AbstractBeaconResponse beacon_response : beacons_responses) {
                if (beacon_response instanceof BeaconCollectionsResponse res) {
                    if (checkIncludeResponse(include_resultset_responses, res)) {
                        mergeMeta(response, res);
                        mergeCollections(response, res);
                        mergeSummary(response, res);
                    }
                }
            }
            aggregated = response;
        } else {
            final BeaconResultsetsResponse response = new BeaconResultsetsResponse();
            final BeaconResultsets resultsets = new BeaconResultsets();
            
            response.setResponse(resultsets);
            response.setMeta(new BeaconResponseMeta());
            response.setResponseSummary(new BeaconResponseSummary(false));
          
            for (AbstractBeaconResponse beacon_response : beacons_responses) {
                if (beacon_response instanceof BeaconResultsetsResponse res) {
                    if (checkIncludeResponse(include_resultset_responses, res)) {
                        mergeMeta(response, res);
                        mergeResultsets(response, res);
                        mergeSummary(response, res);
                    }
                } else if (beacon_response instanceof BeaconErrorResponse err) {
                    
                    if (!"NONE".equals(include_resultset_responses) &&
                        !"HIT".equals(include_resultset_responses)) {
                        final BeaconResultset empty = new BeaconResultset();
                        empty.setExists(false);
                        BeaconError error = err.getError();
                        if (error != null) {
                            JsonObjectBuilder b = Json.createObjectBuilder();
                            String errorMessage = error.getErrorMessage();
                            b.add("errorCode", error.getErrorCode());
                            if (errorMessage != null) {
                                b.add("errorMessage", error.getErrorMessage());
                            }

                            empty.setInfo(Json.createObjectBuilder().add("error", b).build());
                        }

                        final BeaconResponseMeta err_meta = err.getMeta();
                        if (err_meta != null) {
                            empty.setBeaconId(err_meta.getBeaconId());
                        }

                        List<BeaconResultset> list = resultsets.getResultSets();
                        if (list == null) {
                            resultsets.setResultSets(list = new ArrayList());
                        }

                        list.add(empty);
                    }
                }
            }
            aggregated = response;
        }

        final BeaconResponseMeta beacon_network_response_meta = this.getMeta(meta, query);
        aggregated.setMeta(beacon_network_response_meta);
        return Response.ok(aggregated).build();
    }
    
    /**
     * Merge beacon response into the beacon network response object.
     * 
     * @param target Beacon Network aggregated response object
     * @param source Beacon response object to aggregate
     */
    private void mergeResultsets(BeaconResultsetsResponse target, BeaconResultsetsResponse source) {
        final String beacon_id = source.getMeta() == null ? null 
                : source.getMeta().getBeaconId();
        
        BeaconResultsets target_response = target.getResponse();

        List<BeaconResultset> target_resultsets = target_response.getResultSets();
        if (target_resultsets == null) {
            target_response.setResultSets((List)(target_resultsets = new ArrayList()));
        }

        BeaconResultsets source_response = source.getResponse();
        if (source_response != null) {
            List<BeaconResultset> source_resultsets = source_response.getResultSets();
            if (source_resultsets != null && !source_resultsets.isEmpty()) {
                // set 'beaconId' only when it is not set already
                source_resultsets.stream()
                        .filter(rs -> Objects.isNull(rs.getBeaconId()))
                        .forEach(rs -> rs.setBeaconId(beacon_id));
                target_resultsets.addAll(source_resultsets);
                return;
            }
        }

        // emulate a resultset for boolean or count responses
        BeaconResultset result_set = new BeaconResultset();
        result_set.setBeaconId(beacon_id);
        result_set.setInfo(source.getInfo());
        result_set.setResults(new ArrayList());
        result_set.setResultsHandovers(source.getBeaconHandovers());
        BeaconResponseSummary summary = source.getResponseSummary();
        if (summary != null) {
            result_set.setExists(summary.getExists());
            result_set.setResultsCount(summary.getNumTotalResults());
        }

        target_resultsets.add(result_set);
    }
    
    private void mergeCollections(BeaconCollectionsResponse target, BeaconCollectionsResponse source) {
        final BeaconCollections source_response = source.getResponse();
        if (source_response != null) {
            final List source_collections = source_response.getCollections();
            if (source_collections != null && !source_collections.isEmpty()) {
                BeaconCollections target_response = target.getResponse();
                if (target_response == null) {
                    target.setResponse(target_response = new BeaconCollections());
                }
                final List target_collections = target_response.getCollections();
                if (target_collections != null) {
                    target_collections.addAll(source_collections);
                } else {
                    target_response.setCollections(new ArrayList(source_collections));
                }             
            }
        }
    }

    /**
     * Merge beacon metadata into the beacon network metadata object.
     * 
     * @param target Beacon Network aggregated response object
     * @param source Beacon response object to aggregate
     */
    private void mergeMeta(AbstractBeaconResponse target, AbstractBeaconResponse source) {
        final BeaconResponseMeta source_meta = source.getMeta();
        if (source_meta != null) {
            final BeaconResponseMeta target_meta = target.getMeta();
            
            final List<SchemaPerEntity> source_schemas = source_meta.getReturnedSchemas();
            if (source_schemas != null && !source_schemas.isEmpty()) {
                final List<SchemaPerEntity> target_schemas = target_meta.getReturnedSchemas();
                if (target_schemas != null) {
                    target_schemas.addAll(source_schemas);
                } else {
                    target_meta.setReturnedSchemas(new ArrayList(source_schemas));
                }
            }

            final BeaconReceivedRequestSummary source_request_summary = source_meta.getReceivedRequestSummary();
            if (source_request_summary != null) {
            }
        }
    }

    /**
     * Merge beacon metadata into the beacon network metadata object.
     * 
     * @param target Beacon Network aggregated response object
     * @param source Beacon response object to aggregate
     */
    private void mergeSummary(BeaconResponse target, BeaconResponse source) {
        final BeaconResponseSummary source_summary = source.getResponseSummary();
        if (source_summary != null) {
            final Integer source_total = source_summary.getNumTotalResults();
            if (source_total != null && source_total > 0) {
                final BeaconResponseSummary target_summary = target.getResponseSummary();
                target_summary.setExists(true);
                
                final Integer target_total = target_summary.getNumTotalResults();
                if (target_total == null) {
                    target_summary.setNumTotalResults(source_total);
                } else {
                    target_summary.setNumTotalResults(target_total + source_total);
                }
            }
        }
    }
    
    protected BeaconResponseMeta getMeta(
            BeaconRequestMeta request_meta, 
            BeaconRequestQuery request_query) {
        
        final BeaconResponseMeta response_meta = new BeaconResponseMeta();

        final BeaconReceivedRequestSummary request_summary = 
                new BeaconReceivedRequestSummary();

        if (request_meta != null) {
            request_summary.setApiVersion(request_meta.getApiVersion());
            request_summary.setRequestedSchemas(request_meta.getRequestedSchemas());
        }

        if (request_query != null) {
            request_summary.setPagination(request_query.getPagination());
            request_summary.setBeaconRequestParameters(request_query.getRequestParameters());

            final List<BeaconQueryFilter> filters = request_query.getFilters();
            if (filters != null) {
                request_summary.setFilters(filters.stream()
                        .filter(Objects::nonNull)
                        .map(BeaconQueryFilter::toString)
                        .collect(Collectors.toList()));
            }

            request_summary.setRequestedGranularity(request_query.getRequestedGranularity());
            request_summary.setTestMode(request_query.getTestMode());
        }
        
        final BeaconInformationalResponseMeta meta = beacon_info.beaconInfo().getMeta();
        if (request_summary.getApiVersion() == null) {
            if (meta != null) {
                request_summary.setApiVersion(meta.getApiVersion());
            }
        }

//        if (request_summary.getRequestedGranularity() == null) {
//            request_summary.setRequestedGranularity(defaultGranularity);
//        }

        if (request_summary.getRequestedSchemas() == null) {
            request_summary.setRequestedSchemas(Collections.EMPTY_LIST);
        }
        
        if (request_summary.getPagination() == null) {
            request_summary.setPagination(new es.bsc.inb.ga4gh.beacon.framework.model.v200.common.Pagination());
        }

        response_meta.setReceivedRequestSummary(request_summary);
      
        if (meta != null) {
            response_meta.setBeaconId(meta.getBeaconId());
            response_meta.setApiVersion(meta.getApiVersion());
        }

        response_meta.setReturnedGranularity(request_summary.getRequestedGranularity());

//        if (defaultSchema != null) {
//            response_meta.setReturnedSchemas(Arrays.asList(defaultSchema));
//        }

        return response_meta;
    }
    
    private boolean checkIncludeResponse(String include_resultset_responses, BeaconResponse response) {
        if ("ALL".equals(include_resultset_responses)) {
            return true;
        }
        if ("NONE".equals(include_resultset_responses)) {
            return false;
        }
        
        final boolean hit = "HIT".equals(include_resultset_responses);
        
        final BeaconResponseSummary response_summary = response.getResponseSummary();
        if (response_summary != null) {
            if (Boolean.TRUE.equals(response_summary.getExists())) {
                return hit;
            }
            final Integer num_total_results = response_summary.getNumTotalResults();
            if (num_total_results != null && num_total_results > 0) {
                return hit;
            }
        }
        
        return !hit;
    }
}
