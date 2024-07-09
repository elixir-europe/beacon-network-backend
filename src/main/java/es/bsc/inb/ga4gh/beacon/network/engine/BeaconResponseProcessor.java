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

import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.AbstractBeaconResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconError;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconErrorResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResponseMeta;
import es.bsc.inb.ga4gh.beacon.network.model.jsonb.adapter.BeaconResponseDeserializer;
import es.elixir.bsc.json.schema.ValidationError;
import es.elixir.bsc.json.schema.model.JsonSchema;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconResponseProcessor implements BodyPublisher,
        BodyHandler<AbstractBeaconResponse> {

    private final static Jsonb JSONB = JsonbBuilder.create(new JsonbConfig()
            .withDeserializers(new BeaconResponseDeserializer()));

    public final UUID xid;
    public final String beaconId;
    public final String entityType;
    public final String template;
    public final Boolean testMode;
    
    public byte[] req;
    public byte[] res;
    
    public final long time;
    
    private final BodyPublisher delegate;
    
    private final JsonSchema schema;
    
    public BeaconResponseProcessor(UUID xid, String beaconId, String entityType, 
            String template, Boolean testMode, byte[] data, JsonSchema schema) {        
        this.xid = xid;
        this.beaconId = beaconId;
        this.entityType = entityType;
        this.template = template;
        this.testMode = testMode;
        this.req = data;
        delegate = HttpRequest.BodyPublishers.ofByteArray(data);
        this.schema = schema;
        
        time = System.currentTimeMillis();
    }

    @Override
    public long contentLength() {
        return req.length;
    }
        
    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(subscriber);
    }

    @Override
    public BodySubscriber<AbstractBeaconResponse> apply(ResponseInfo responseInfo) {
        if (responseInfo.statusCode() >= 300) {
            
            final String msg = String.format("%s error getting response from %s", 
                    beaconId, template);
        
            Logger.getLogger(BeaconResponseProcessor.class.getName())
                    .log(Level.INFO, msg);
            
            final BeaconErrorResponse response = new BeaconErrorResponse();
            final BeaconResponseMeta meta = new BeaconResponseMeta();
            meta.setBeaconId(beaconId);
            response.setMeta(meta);
            final BeaconError error = new BeaconError();
            error.setErrorCode(responseInfo.statusCode());
            error.setErrorMessage(msg);
            response.setBeaconError(error);

            return BodySubscribers.replacing(response);
        }

        return Boolean.TRUE.equals(testMode) ? 
                BodySubscribers.mapping(BodySubscribers.ofByteArray(), this::apply) :
                BodySubscribers.mapping(BodySubscribers.ofInputStream(), this::apply);
    }
    
    private AbstractBeaconResponse apply(InputStream in) {
        return apply(new DynamicBufferedInputStream(in));
    }
    
    private AbstractBeaconResponse apply(DynamicBufferedInputStream in) {
        try {
            return JSONB.fromJson(new InputStreamReader(
                in, StandardCharsets.UTF_8), AbstractBeaconResponse.class);
        } catch (Exception ex) {
            if (!Boolean.TRUE.equals(testMode)) {
                final List<ValidationError> errors = validate(in.buf, in.pos);
                if (errors == null || !errors.isEmpty()) {
                    return createErrorResponse(errors);
                }
            }
            final String msg = String.format(
                    "internal server error: %s failed deserialize valid document %s from %s", 
                            beaconId, entityType, template);
            return createErrorResponse(msg);
        } finally {
            res = Arrays.copyOf(in.buf, in.pos);
        }
    }
    
    private AbstractBeaconResponse apply(byte[] payload) {
        final List<ValidationError> errors = validate(payload, payload.length);
        if (errors != null && errors.isEmpty()) {
            return apply(new ByteArrayInputStream(payload));
        }
        res = payload;
        return createErrorResponse(errors);
    }
    
    private List<ValidationError> validate(byte[] payload, int length) {
        if (schema != null) {
            final List<ValidationError> errors = new ArrayList();
            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(payload, 0, length))) {
                final JsonValue v = reader.readValue();
                schema.validate(v, errors);
            } catch(Exception ex) {
                final String msg = String.format("%s error parsing %s from %s", 
                                beaconId, entityType, template);
                errors.add(new ValidationError(msg));
            }
            return errors;
        }
        return null;
    }
    
    private BeaconErrorResponse createErrorResponse(List<ValidationError> errors) {
        if (errors != null) {
            final StringBuilder sb = new StringBuilder();
            for (ValidationError error : errors) {
                sb.append(error.path).append(' ').append(error.message).append('\n');
            }
            return createErrorResponse(sb.toString());
        }
        return createErrorResponse("internal server error: no Beacon Schema found!");
    }

    private BeaconErrorResponse createErrorResponse(String msg) {
        Logger.getLogger(BeaconResponseProcessor.class.getName())
                .log(Level.INFO, msg);

        final BeaconErrorResponse response = new BeaconErrorResponse();
        final BeaconResponseMeta meta = new BeaconResponseMeta();
        meta.setBeaconId(beaconId);
        response.setMeta(meta);
        final BeaconError error = new BeaconError();
        error.setErrorCode(0);
        error.setErrorMessage(msg);
        response.setBeaconError(error);
        return response;
    }
}
