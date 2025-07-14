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

package es.bsc.inb.ga4gh.beacon.network.endpoint;

import es.bsc.inb.ga4gh.beacon.network.engine.BeaconNetworkAggregator;
import es.bsc.inb.ga4gh.beacon.network.info.BeaconFilteringTermsProducer;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Dmitry Repchevsky
 */

@Path("/")
@ApplicationScoped
public class BeaconNetworkAggregatorEndpoint {
    
    @Resource
    private ManagedExecutorService executor;

    @Inject
    private BeaconNetworkAggregator aggregator;
    
    @Inject
    private BeaconFilteringTermsProducer filtering_terms;

    @GET
    @Path("/{s:.+}")
    @Produces(MediaType.APPLICATION_JSON)
    public void get(@Context HttpServletRequest request,
            @Suspended AsyncResponse asyncResponse) {
        executor.submit(() -> {
            asyncResponse.resume(asyncEndpoint(request));
        });
    }

    @POST
    @Path("/{s:.+}")
    @Produces(MediaType.APPLICATION_JSON)
    public void post(@Context HttpServletRequest request,
        @Suspended AsyncResponse asyncResponse) {
        executor.submit(() -> {
            asyncResponse.resume(asyncEndpoint(request));
        });
    }
    
    private Response asyncEndpoint(HttpServletRequest request) {
        return aggregator.aggregate(request);
    }
}
