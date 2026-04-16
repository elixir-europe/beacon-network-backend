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

package es.bsc.inb.ga4gh.beacon.network.model.jsonb.adapter;

import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.AlphanumericFilter;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconQueryFilter;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconQueryFilterGroup;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconQueryFilterGroupInterface;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.OntologyFilter;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconQueryFilterDeserializer implements JsonbDeserializer<BeaconQueryFilterGroupInterface> {

    @Override
    public BeaconQueryFilterGroupInterface deserialize(JsonParser parser, 
            DeserializationContext ctx, Type type) {
        
        final JsonValue value = parser.getValue();
        if (value instanceof JsonObject obj) {
            return deserialize(obj);
        } else if (value instanceof JsonArray arr) {
            final BeaconQueryFilterGroup g = new BeaconQueryFilterGroup();
            for (int i = 0, n = arr.size(); i < n; i++) {
                final JsonValue v = arr.get(i);
                if (v instanceof JsonObject o) {
                    g.add(deserialize(o));
                }
            }
            return g;
        }
        return null;
    }
    
    private BeaconQueryFilter deserialize(JsonObject obj) {
        final JsonValue id = obj.get("id");
        if (id != null && id.getValueType() == JsonValue.ValueType.STRING) {
            final JsonValue scope = obj.get("scope");
            final JsonValue operator = obj.get("operator");
            if (operator != null) {
                if (operator.getValueType() == JsonValue.ValueType.STRING) {
                    final JsonValue val = obj.get("value");
                    if (val.getValueType() == JsonValue.ValueType.STRING) {
                        final AlphanumericFilter filter = new AlphanumericFilter(
                                ((JsonString)id).getString(),
                                ((JsonString)operator).getString(),
                                ((JsonString)val).getString());
                        if (scope != null && scope.getValueType() == JsonValue.ValueType.STRING) {
                            filter.setScope(((JsonString)scope).getString());
                        }
                        return filter;
                    }
                }
            } else {
                final OntologyFilter filter = new OntologyFilter(((JsonString)id).getString());
                final JsonValue include_descendant_terms = obj.get("includeDescendantTerms");
                if (include_descendant_terms != null && 
                    include_descendant_terms.getValueType() == JsonValue.ValueType.FALSE) {
                    filter.setIncludeDescendantTerms(Boolean.FALSE);
                }

                final JsonValue similarity = obj.get("similarity");
                if (similarity != null && similarity.getValueType() == JsonValue.ValueType.STRING) {
                    filter.setSimilarity(((JsonString)similarity).getString());
                }

                if (scope != null && scope.getValueType() == JsonValue.ValueType.STRING) {
                    filter.setScope(((JsonString)scope).getString());
                }
                return filter;
            }
        }
        return null;
    }
}
