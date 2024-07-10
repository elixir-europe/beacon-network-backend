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

import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconRequestBody;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconRequestMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.requests.BeaconRequestQuery;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.AbstractBeaconResponse;
import es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties;
import es.bsc.inb.ga4gh.beacon.validator.BeaconFrameworkSchema;
import es.elixir.bsc.json.schema.JsonSchemaReader;
import es.elixir.bsc.json.schema.model.JsonSchema;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

@ApplicationScoped
public class BeaconNetworkAggregator {

    @Inject
    private BeaconNetworkRequestAnalyzer requestAnalyzer;
    
    @Inject
    private BeaconEndpointsMatcher matcher;
    
    @Inject
    private BeaconNetworkResponseBuilder responseBuilder;
    
    private JsonSchema schema;
    
    private HttpClient http_client;
    
    @PostConstruct
    public void init() {
        http_client = HttpClient.newBuilder()
        .version(Version.HTTP_2)
        .followRedirects(Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
        
        try {
            final URL url = BeaconNetworkAggregator.class.getClassLoader().getResource(BeaconFrameworkSchema.BEACON_RESPONSE_SCHEMA.SCHEMA);
            if (url != null) {
                schema = JsonSchemaReader.getReader().read(url);
            }
        } catch(Exception ex) {
            Logger.getLogger(BeaconNetworkAggregator.class.getName()).log(Level.SEVERE, "error loading schema {0} {1}", 
                    new Object[]{BeaconFrameworkSchema.BEACON_RESPONSE_SCHEMA.SCHEMA, ex.getMessage()});
        }
    }

    public Response aggregate(HttpServletRequest request) {                
        final byte[] data;
        BeaconRequestMeta meta = null;
        BeaconRequestQuery query = null;

        if (HttpMethod.POST.equals(request.getMethod())) {
            data = requestAnalyzer.getContent(request);
            final BeaconRequestBody beaconRequest = requestAnalyzer.getBeaconRequest(data);
            if (beaconRequest != null) {
                meta = beaconRequest.getMeta();
                query = beaconRequest.getQuery();
            }
        } else {
            data = new byte[0];
            query = requestAnalyzer.getRequestQuery(request);
        }

        final UUID xid = UUID.randomUUID();

        final List<CompletableFuture<HttpResponse<AbstractBeaconResponse>>> invocations = new ArrayList();
        
        Map<String, Map.Entry<String, String>> matched_endpoints = matcher.match(request);
        for (Map.Entry<String, Map.Entry<String, String>> entry : matched_endpoints.entrySet()) {
            final Map.Entry<String, String> endpoint = entry.getValue();
            final BeaconResponseProcessor processor = new BeaconResponseProcessor(
                    xid, entry.getKey(), endpoint.getKey(), endpoint.getValue(), 
                    query != null ? query.getTestMode() : null, data, schema);

            final Builder builder = getInvocation(endpoint.getValue(), request);
            builder.method(request.getMethod(), processor);

            CompletableFuture<HttpResponse<AbstractBeaconResponse>> future =
                    http_client.sendAsync(builder.build(), processor)
                            .orTimeout(ConfigurationProperties.BN_REQUEST_TIMEOUT_PROPERTY, TimeUnit.SECONDS)
                            .handle((r, ex) -> {
                                if (ex != null) {
                                    throw new BeaconTimeoutException(processor);
                                }
                                return r;
                            });

            invocations.add(future);
        }
        
        if (invocations.isEmpty()) {
            // return error response;
        }
        
        return responseBuilder.build(meta, query, invocations);
    }

    private Builder getInvocation(String endpoint, HttpServletRequest request) {
        endpoint = endpoint.substring(0, endpoint.lastIndexOf("//"));
        Builder builder = HttpRequest.newBuilder(UriBuilder.fromUri(endpoint)
                .path(request.getPathInfo())
                .replaceQuery(request.getQueryString()).build())
                .header(HttpHeaders.USER_AGENT, "BN/2.0.0")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
//                .timeout(Duration.ofMillis(1));
        
        final Enumeration<String> authorization = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.hasMoreElements()) {
            Collections.list(authorization).stream()
                    .forEach(h -> builder.header(HttpHeaders.AUTHORIZATION, h));
        }

        return builder;        
    }
}
