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
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLog;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity.METHOD;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity.REQUEST_TYPE;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Dmitry Repchevsky
 */

@ApplicationScoped
public class BeaconNetworkResponseBuilder {
    
    @Inject
    private BeaconInfoProducer beacon_info;
    
    @Inject
    private BeaconLog log;
    
    public Response build(
            BeaconRequestMeta meta, 
            BeaconRequestQuery query, 
            List<CompletableFuture<HttpResponse<AbstractBeaconResponse>>> invocations) {

        BeaconResponse aggregated = null;

        final List<AbstractBeaconResponse> beacons_responses = getResultsets(invocations);
        for (AbstractBeaconResponse beacon_response : beacons_responses) {
            if(beacon_response instanceof BeaconResponse r) {
                if (aggregated == null) {
                    try {
                        aggregated = r.getClass().getDeclaredConstructor().newInstance();
                        aggregated.setMeta(new BeaconResponseMeta());
                        aggregated.setResponseSummary(new BeaconResponseSummary(false));
                    } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException ex) {
                        Logger.getLogger(BeaconNetworkResponseBuilder.class.getName()).log(Level.SEVERE, null, ex);
                        continue;
                    }
                } else if (!aggregated.getClass().equals(beacon_response.getClass())) {
                    continue; // error!!
                }
                mergeMeta(aggregated, r);
                if (beacon_response instanceof BeaconResultsetsResponse resultsets) {
                    mergeResultsets((BeaconResultsetsResponse)aggregated, resultsets);
                } else if (beacon_response instanceof BeaconCollectionsResponse collections) {
                    mergeCollections((BeaconCollectionsResponse)aggregated, collections);
                }
                mergeSummary(aggregated, r);
            }
        }
        
        AbstractBeaconResponse response = aggregated;
        if (response == null) {
            // all beacons returned errors
            BeaconError error = new BeaconError();
            BeaconErrorResponse error_response = new BeaconErrorResponse();
            error_response.setBeaconError(error);
            response = error_response;
        } else if (aggregated instanceof BeaconResultsetsResponse resultsets) {
            final BeaconResultsets results = resultsets.getResponse();
            for (AbstractBeaconResponse beacon_response : beacons_responses) {
                if(beacon_response instanceof BeaconErrorResponse err) {
                    final BeaconResultset empty = new BeaconResultset();
                    empty.setExists(false);
                    
                    final BeaconError error = err.getBeaconError();
                    if (error != null) {
                        final JsonObjectBuilder b = Json.createObjectBuilder();
                        final String errorMessage = error.getErrorMessage();
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
                    
                    results.getResultSets().add(empty);
                }
            }
        } else if (aggregated instanceof BeaconResultsetsResponse collections) {
            // ???
        }

        final BeaconResponseMeta beacon_network_response_meta = getMeta(meta, query);
        response.setMeta(beacon_network_response_meta);
        
        return Response.ok(aggregated).build();
    }

    private List<AbstractBeaconResponse> getResultsets(List<CompletableFuture<HttpResponse<AbstractBeaconResponse>>> invocations) {

        final List<AbstractBeaconResponse> responses = new ArrayList();
        
        for (CompletableFuture<HttpResponse<AbstractBeaconResponse>> invocation : invocations) {
            try {
                final HttpResponse<AbstractBeaconResponse> response = invocation.get(10, TimeUnit.MINUTES);

                if (response != null) {
                    if (response.body() != null) {
                        responses.add(response.body());
                    }
                    log(response);
                }
            } catch (Exception ex) {
                Logger.getLogger(BeaconNetworkResponseBuilder.class.getName()).log(
                        Level.INFO, ex.getMessage());
            }
        }

        return responses;
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
        
        final BeaconResultsets source_response = source.getResponse();
        if (source_response != null) {
            final List<BeaconResultset> source_resultsets = source_response.getResultSets();
            if (source_resultsets != null && !source_resultsets.isEmpty()) {
                
                // set 'beaconId' only when it is not set already
                source_resultsets.stream()
                        .filter(rs -> Objects.isNull(rs.getBeaconId()))
                        .forEach(rs -> rs.setBeaconId(beacon_id));
                
                BeaconResultsets target_response = target.getResponse();
                if (target_response == null) {
                    target.setResponse(target_response = new BeaconResultsets());
                }
                final List<BeaconResultset> target_resultsets = target_response.getResultSets();
                if (target_resultsets != null) {
                    target_resultsets.addAll(source_resultsets);
                } else {
                    target_response.setResultSets(new ArrayList(source_resultsets));
                }             
            }
        }
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
                        .map(BeaconQueryFilter::toString)
                        .collect(Collectors.toList()));
            }

            request_summary.setRequestedGranularity(request_query.getGranularity());
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

    private void log(HttpResponse<AbstractBeaconResponse> response) {
        
        final METHOD method = METHOD.valueOf(response.request().method());
        
        final BeaconResponseProcessor publisher = 
                (BeaconResponseProcessor)response.request().bodyPublisher().get();
        
        final String req = publisher.req.length == 0 ? null : 
                new String(publisher.req, StandardCharsets.UTF_8);
        
        final String res = publisher.res == null ? null :
                new String(publisher.res, StandardCharsets.UTF_8);
        
        String message = null;
        if (response.body() instanceof BeaconErrorResponse error) {
            final BeaconError err = error.getBeaconError();
            if (err != null) {
                message = err.getErrorMessage();
            }
        }

        final BeaconLogEntity log_entry = new BeaconLogEntity(REQUEST_TYPE.QUERY, 
            method, response.request().uri().toString(), response.statusCode(), 
            message, req, res);
        
        log.log(log_entry);
    }
}