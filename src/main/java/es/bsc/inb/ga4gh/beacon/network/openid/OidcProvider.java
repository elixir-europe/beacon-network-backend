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

package es.bsc.inb.ga4gh.beacon.network.openid;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.security.enterprise.authentication.mechanism.http.openid.OpenIdConstant;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

public class OidcProvider {
    
    private final static String WELL_KNOWN_SUFFIX = "/.well-known/openid-configuration";
    
    public final String endpoint;
    
    private final HttpClient http_client;
    
    private JsonObject configuration;
    
    private final ConcurrentHashMap<String, String> client_supported_scopes;
    private final Map<String, JsonObject> jwks;

    public OidcProvider(String endpoint) {
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
       
        if (!endpoint.endsWith(WELL_KNOWN_SUFFIX)) {
            endpoint += WELL_KNOWN_SUFFIX;
        }

        this.endpoint = endpoint;
        
        client_supported_scopes = new ConcurrentHashMap();
        jwks = new ConcurrentHashMap();
        
        http_client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(30))
        .build();        
    }

    public final String getIssuer() {
        final JsonObject cfg = getConfiguration();
        return cfg == null ? null : cfg.getString(OpenIdConstant.ISSUER, null);
    }

    public final String getTokenEndpoint() {
        final JsonObject cfg = getConfiguration();
        return cfg == null ? null : cfg.getString(OpenIdConstant.TOKEN_ENDPOINT, null);
    }

    public JsonObject getConfiguration() {
        if (configuration == null) {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header(HttpHeaders.USER_AGENT, "BN/2.0.0")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON).GET().build();

            configuration = invoke(request);
        }
        return configuration;
    }
    
    /**
     * Get the key JSON object by its key identifier.
     * 
     * @param kid - key identifier
     * 
     * @return this provider key object.
     */
    public JsonObject getKey(String kid) {
        JsonObject jwk = jwks.get(kid);
        if (jwk == null) {
            final JsonArray keys = readKeys();
            if (keys != null) {
                for (int i = 0; i < keys.size(); i++) {
                    final JsonValue value = keys.get(i);
                    if (value instanceof JsonObject o) {
                        final String key = o.getString("kid", null);
                        if (key != null) {
                            jwks.put(key, o);
                            if (jwk == null && key.equals(kid)) {
                                jwk = o;
                            }
                        }
                    }
                }
            }
        }
        return jwk;
    }
    
    private JsonArray readKeys() {
        final JsonObject cfg = getConfiguration();
        if (cfg != null) {
            final String jwks_uri = cfg.getString(OpenIdConstant.JWKS_URI, null);
            if (jwks_uri == null) {
                Logger.getLogger(OidcProvider.class.getName()).log(
                        Level.WARNING, "no 'jwks_uri' in the provider configuration.");
            } else {
                final HttpRequest request = HttpRequest.newBuilder(URI.create(jwks_uri))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON).GET().build();

                final JsonObject response = invoke(request);
                if (response != null) {
                    return response.getJsonArray("keys");
                }
            }
        }
        return null;
    }

    private String getClientSupportedScopes(String client_id, String client_secret) {
        String scopes = client_supported_scopes.get(client_id);
        if (scopes == null) {
            final String token = getClientToken(client_id, client_secret);
            if (token != null) {
                final JsonObject payload = parse(token);
                if (payload != null) {
                   scopes = payload.getString("scope", null);
                   if (scopes != null) {
                       client_supported_scopes.put(client_id, scopes);
                   }
                }
            }
        }
        return scopes;
    }
    
    public String doTokenExchange(String client_id, String client_secret, 
            String endpoint, String token) {

        final JsonObject payload = parse(token);
        if (payload == null) {
            return null;
        }
        
        final String subject_issuer = payload.getString(OpenIdConstant.ISSUER_IDENTIFIER, null);
        
        final StringBuilder data = new StringBuilder();

        data.append(OpenIdConstant.CLIENT_ID).append('=').append(client_id)
            .append("&subject_token").append('=').append(token)
            .append("&grant_type").append('=')
                .append(URLEncoder.encode("urn:ietf:params:oauth:grant-type:token-exchange", StandardCharsets.UTF_8))
                
            // special case for extternal token to internal token exchange supported 
            // by legacy (v1) Keycloak's token exchange implementation
            .append("&subject_token_type").append('=')
                .append(URLEncoder.encode(
                        Objects.equals(subject_issuer, getIssuer())
                        ? "urn:ietf:params:oauth:token-type:access_token"
                        : "urn:ietf:params:oauth:token-type:jwt", StandardCharsets.UTF_8))

            .append("&requested_token_type").append('=')
                .append(URLEncoder.encode("urn:ietf:params:oauth:token-type:access_token", StandardCharsets.UTF_8));
        
        // keep only scopes supported by the target client
        String scopes_supported = getClientSupportedScopes(client_id, client_secret);
        if (scopes_supported == null) {
            scopes_supported = configuration.getString(OpenIdConstant.SCOPES_SUPPORTED, null);
        }
        
        if (scopes_supported != null) {
            final String scope = payload.getString("scope", "");
            final List<String> scopes = new ArrayList(Arrays.asList(scope.split("\\s+")));

            scopes.retainAll(Arrays.asList(scopes_supported.split("\\s+")));
            if (!scopes.isEmpty()) {
                data.append("&scope=");
                data.append(String.join(" ", scopes));
            }
        }
        
        if (client_secret != null) {
            data.append("&client_secret").append('=').append(client_secret);
        }

        if (endpoint != null) {
            data.append("&resource").append('=').append(endpoint);
        }
        
        return getAccessToken(data.toString());
    }
    
    public String getClientToken(String client_id, String client_secret) {
        final StringBuilder data = new StringBuilder();

        data.append(OpenIdConstant.CLIENT_ID).append('=').append(client_id);
        if (client_secret != null) {
            data.append('&').append(OpenIdConstant.CLIENT_SECRET).append('=').append(client_secret);
        }
        data.append("&grant_type=client_credentials");
        
        return getAccessToken(data.toString());
    }

    /**
     * Call the request and get back the access token.
     * 
     * @param data the request body
     * 
     * @return access token or null
     */
    private String getAccessToken(String data) {
        
        final String token_endpoint = getTokenEndpoint();
        if (token_endpoint == null) {
            Logger.getLogger(OidcProvider.class.getName()).log(
                    Level.WARNING, "no 'token_endpoint' in the provider configuration.");
            return null;
        }

        final HttpRequest.Builder builder = HttpRequest.newBuilder(UriBuilder.fromUri(token_endpoint)
                .build())
                .header(HttpHeaders.USER_AGENT, "BN/2.0.0")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        
        final JsonObject token_response = invoke(builder.build());
        if (token_response != null) {
            final String access_token = token_response.getString(OpenIdConstant.ACCESS_TOKEN, null);
            if (access_token != null) {
                return access_token;
            }
            Logger.getLogger(OidcProvider.class.getName()).log(
                    Level.WARNING, "no access token found in the client token response");

        }
        return null;        
    }

    private JsonObject invoke(HttpRequest request) {
        try {
            final HttpResponse<String> response = http_client.send(request, 
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response != null && response.statusCode() < 300) {
                final String body = response.body();
                if (body == null) {
                    Logger.getLogger(OidcProvider.class.getName()).log(
                                Level.WARNING, "empty response {0}", request.uri());
                    return null;
                }
                try (JsonReader reader = Json.createReader(new StringReader(body))) {
                    return reader.readObject();
                } 
            }
            Logger.getLogger(OidcProvider.class.getName()).log(
                    Level.SEVERE, "invalid or no response from {0}", request.uri());
        } catch(IOException | InterruptedException ex) {
            Logger.getLogger(OidcProvider.class.getName()).log(
                    Level.SEVERE, "error invoking {0}", ex.getMessage());
        }
        
        return null;
    }
    
    private JsonObject parse(String token) {
        final String[] token_parts = token.split("\\.");
        if (token_parts.length == 3) {
            return decode(token_parts[1]);
        }
        return null;
    }

    private JsonObject decode(String base64) {
        final Base64.Decoder decoder = Base64.getDecoder();
        try {
            final byte[] b = decoder.decode(base64);
            return Json.createReader(new ByteArrayInputStream(b)).readObject();
        } catch (Exception ex) {
            return null;
        }
    }

}
