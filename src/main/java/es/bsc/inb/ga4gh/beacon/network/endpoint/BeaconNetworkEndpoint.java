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

package es.bsc.inb.ga4gh.beacon.network.endpoint;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.ServiceConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconEntryTypesResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInfoResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconMapResponse;
import es.bsc.inb.ga4gh.beacon.framework.v200.BeaconInterface;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import es.bsc.inb.ga4gh.beacon.network.info.BeaconEntryTypesProducer;
import es.bsc.inb.ga4gh.beacon.network.info.BeaconFilteringTermsProducer;
import es.bsc.inb.ga4gh.beacon.network.info.BeaconInfoProducer;
import es.bsc.inb.ga4gh.beacon.network.info.BeaconMapsProducer;
import es.bsc.inb.ga4gh.beacon.network.model.BeaconNetworkInfoResponse;
import es.bsc.inb.ga4gh.service_info.model.v100.Service;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Dmitry Repchvsky
 */

@Path("/")
@RequestScoped
public class BeaconNetworkEndpoint implements BeaconInterface {
    
    @Inject
    private Service service;
    
    @Inject
    private NetworkConfiguration config;

    @Inject
    private ServiceConfiguration configuration;
    
    @Inject
    private BeaconInfoProducer beacon_info;
    
    @Inject
    private BeaconMapsProducer beacon_map;
    
    @Inject
    private BeaconEntryTypesProducer entry_types;
    
    @Inject
    private BeaconFilteringTermsProducer filtering_terms;

    @Inject
    private HttpHeaders httpHeaders;

    @Path("/{s: .*}")
    @OPTIONS
    public Response compliance() {
        return Response.ok().header("Allow", "GET")
                            .header("Allow", "HEAD")
                            .header("Allow", "PUT")
                            .header("Allow", "POST")
                            .header("Allow", "PATCH")
                            .header("Allow", "DELETE")
                            .header("Allow", "OPTIONS")
                            .build();
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public BeaconInfoResponse getBeaconInfoRoot(
            @QueryParam("requestedSchema") String requestedSchema) {
        return beacon_info.beaconInfo();
    }
    
    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public BeaconNetworkInfoResponse getBeaconInfo(
            @QueryParam("requestedSchema") String requestedSchema) {
        final String cache = httpHeaders.getHeaderString("Cache-Control");
        if ("no-cache".equalsIgnoreCase(cache)) {
            config.updateBeacons();
        }

        return beacon_info.beaconInfo();
    }
    
    @GET
    @Path("/service-info")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Service getBeaconServiceInfo() {
        return service;
    }
    
    @GET
    @Path("/configuration")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public ServiceConfiguration getBeaconConfiguration() {
        return configuration;
    }

    @GET
    @Path("/map")
    @Produces(MediaType.APPLICATION_JSON)        
    @Override
    public BeaconMapResponse getBeaconMap() {
        return beacon_map.maps();
    }

    @GET
    @Path("/entry_types")
    @Produces(MediaType.APPLICATION_JSON)        
    @Override
    public BeaconEntryTypesResponse getEntryTypes() {
        return entry_types.entryTypes();
    }

    @GET
    @Path("/filtering_terms")
    @Produces(MediaType.APPLICATION_JSON)        
    @Override
    public BeaconFilteringTermsResponse getFilteringTerms() {
        return filtering_terms.filteringTerms();
    }

}
