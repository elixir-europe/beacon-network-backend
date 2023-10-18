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

import es.bsc.inb.ga4gh.beacon.validator.BeaconEndpointValidator;
import es.bsc.inb.ga4gh.beacon.validator.BeaconMetadataModel;
import es.bsc.inb.ga4gh.beacon.validator.BeaconValidationErrorType;
import es.bsc.inb.ga4gh.beacon.validator.BeaconValidationMessage;
import es.bsc.inb.ga4gh.beacon.validator.ValidationObserver;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Dmitry Repchevsky
 */

@Path("/")
@ApplicationScoped
public class BeaconNetworkValidatorEndpoint {

    @Resource
    private ManagedExecutorService executor;

    @GET
    @Path("/validate")
    @Produces(MediaType.APPLICATION_JSON)
    public void validate(@QueryParam("endpoint") String endpoint,
            @Suspended AsyncResponse asyncResponse) {
        executor.submit(() -> {
            asyncResponse.resume(validate(endpoint).build());
        });
    }

    private ResponseBuilder validate(String endpoint) {
        StreamingOutput stream = (OutputStream out) -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                gen.writeStartArray();
                final StreamingObserver observer = new StreamingObserver(gen);
                if (checkUrl(observer, endpoint)) {
                    final BeaconMetadataModel model = BeaconMetadataModel.load(endpoint, observer);
                    final BeaconEndpointValidator validator = new BeaconEndpointValidator(model);
                    validator.validate(endpoint, observer);
                }
                gen.writeEnd();
            }
        };

        return Response.accepted(stream);
    }
    
    private boolean checkUrl(StreamingObserver observer, String endpoint) {
        try {
            final URI uri = new URI(endpoint);
            if (uri.isAbsolute()) {
                return true;
            }                
            final BeaconValidationMessage message = new BeaconValidationMessage(
                    BeaconValidationErrorType.CONNECTION_ERROR,
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    endpoint, null,
                    String.format("relative Beacon endpoint '%s'", endpoint));
            observer.error(message);
        }
        catch(URISyntaxException ex) {
            final BeaconValidationMessage message = new BeaconValidationMessage(
                    BeaconValidationErrorType.CONNECTION_ERROR,
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    endpoint, null,
                    String.format("invalid Beacon endpoint '%s' %s", endpoint, ex.getMessage()));
            observer.error(message);
        }
        return false;
    }
    public static class StreamingObserver implements ValidationObserver {

        private final JsonGenerator gen;
        
        public StreamingObserver(JsonGenerator gen) {
            this.gen = gen;
        }
                
        @Override
        public void error(BeaconValidationMessage message) {
            gen.writeStartObject();
            if (message.code != null) {
                gen.write("code", message.code);
            }
            if (message.path != null) {
                gen.write("path", message.path);
            }
            if (message.location != null) {
                gen.write("location", message.location);
            }
            if (message.message != null) {
                gen.write("message", message.message);
            }
            gen.writeEnd();
            gen.flush();
        }
        
        @Override
        public void message(String message) {
            gen.writeStartObject();
            gen.write("message", message);
            gen.writeEnd();
            gen.flush();
        }
    }
}
