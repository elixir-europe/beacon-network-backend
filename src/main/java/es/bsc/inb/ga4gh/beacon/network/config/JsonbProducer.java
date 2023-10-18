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

package es.bsc.inb.ga4gh.beacon.network.config;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconQueryFilter;
import es.bsc.inb.ga4gh.beacon.network.model.jsonb.adapter.BeaconNetworkInfoResponseDeserializer;
import es.bsc.inb.ga4gh.beacon.network.model.jsonb.adapter.BeaconResponseDeserializer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * @author Dmitry Repchevsky
 */

@Singleton
public class JsonbProducer {

    private Jsonb jsonb;
    
    @PostConstruct
    public void init() {
        jsonb = JsonbBuilder.newBuilder().withConfig(
                new JsonbConfig().withDeserializers(
                    new BeaconResponseDeserializer(),
                    new BeaconNetworkInfoResponseDeserializer(),
                    new BeaconQueryFilter.BeaconQueryFilterDeserializer())).build();
    }

    @Produces
    public Jsonb beaconInfo() {
        return jsonb;
    }
}
