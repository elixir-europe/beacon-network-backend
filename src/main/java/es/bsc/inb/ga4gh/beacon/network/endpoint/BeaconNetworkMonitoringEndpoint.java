/**
 * *****************************************************************************
 * Copyright (C) 2026 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
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

import es.bsc.inb.ga4gh.beacon.network.log.BeaconLog;
import es.bsc.inb.ga4gh.beacon.network.log.BeaconLogEntity;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Dmitry Repchevsky
 */

@Path("/")
@ApplicationScoped
public class BeaconNetworkMonitoringEndpoint {
    
    @Resource
    private ManagedExecutorService executor;

    @Inject
    private BeaconLog log;
    
    @GET
    @Path("/kpi")
    @Produces(MediaType.APPLICATION_JSON)
    public void kpi(@Suspended AsyncResponse asyncResponse) {
        executor.submit(() -> {
            asyncResponse.resume(kpi().build());
        });
    }

    private ResponseBuilder kpi() {
        final JsonObjectBuilder kpi = Json.createObjectBuilder();
        
        final List<BeaconLogEntity> records = log.getLastRequests(100);
        
        records.sort((BeaconLogEntity l1, BeaconLogEntity l2) 
                -> Double.compare(l1.getTime(), l2.getTime()));
        
        if (!records.isEmpty()) {
            final Double avg = records.stream().mapToLong(BeaconLogEntity::getTime).average().orElse(Double.NaN);
            final JsonObjectBuilder latency = Json.createObjectBuilder()
                    .add("avg", avg)
                    .add("p99", records.get((int)((records.size() - 1) * 0.99)).getTime())
                    .add("p95", records.get((int)((records.size() - 1) * 0.95)).getTime());
            kpi.add("latency", latency);
        }
        return Response.accepted(kpi.build());
    }

}
