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
package es.bsc.inb.ga4gh.beacon.network.model.jsonb.adapter;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInfoResponse;
import es.bsc.inb.ga4gh.beacon.network.model.BeaconNetworkInfoResponse;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconNetworkInfoResponseDeserializer
        implements JsonbDeserializer<BeaconInfoResponse> {

    private static final Jsonb jsonb = JsonbBuilder.create();
    
    @Override
    public BeaconInfoResponse deserialize(JsonParser parser, DeserializationContext ctx, Type type) {
        final JsonValue value = parser.getValue();
        if (JsonValue.ValueType.OBJECT == value.getValueType()) {
            return jsonb.fromJson(value.toString(), BeaconNetworkInfoResponse.class);
        }
        return null;
    }
}
