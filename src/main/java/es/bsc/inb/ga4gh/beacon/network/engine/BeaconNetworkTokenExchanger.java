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

package es.bsc.inb.ga4gh.beacon.network.engine;

import es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties;
import es.bsc.inb.ga4gh.beacon.network.config.NetworkConfiguration;
import es.bsc.inb.ga4gh.beacon.network.model.AccessTokenResponse;
import es.bsc.inb.ga4gh.beacon.network.model.OauthProtectedResource;
import es.bsc.inb.ga4gh.beacon.network.model.OidcConfigurationProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.bind.Jsonb;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

@ApplicationScoped
public class BeaconNetworkTokenExchanger {
    
    @Inject
    private NetworkConfiguration network_configuration;
    
    @Inject
    private Jsonb jsonb;
    
    private HttpClient http_client;
    private Map<String, OidcConfigurationProvider> configuration_providers;

    private OidcConfigurationProvider idp;
    
    @PostConstruct
    public void init() {
        http_client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
        
        configuration_providers = new ConcurrentHashMap();
        
        idp = limitScopes2Client(getBeaconNetworkIdentityProvider());
    }
    
    private OidcConfigurationProvider getBeaconNetworkIdentityProvider() {
        if (ConfigurationProperties.BN_OIDC_ENDPOINT == null ||
            ConfigurationProperties.BN_CLIENT_ID == null ||
            ConfigurationProperties.BN_CLIENT_SECRET == null) {
            Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                    Level.INFO, "no Beacon Network server Identity Provider defined...");

            return null;
        }
        
        final Builder builder = createWellKnownProviderRequest(ConfigurationProperties.BN_OIDC_ENDPOINT);
        try {
            final HttpRequest request = builder.build();
            final HttpResponse<String> response = http_client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response != null && response.statusCode() < 300) {
                final String body = response.body();
                if (body != null) {
                    return jsonb.fromJson(body, OidcConfigurationProvider.class);
                }
            }
            Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                    Level.WARNING, "error reading idp configuration from {0}", request.uri());
        } catch (Exception ex) {
            Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                    Level.INFO, ex.getMessage());
        }
        return null;
    }
    
    /**
     * Instead of using the Identity Provider's supported scope list,
     * try to user Beacon Network client's configured scopes.
     * 
     * @param idp Identity Provider configuration
     * 
     * @return the same, possibly modified, idp object
     */
    private OidcConfigurationProvider limitScopes2Client(OidcConfigurationProvider idp) {
        if (idp != null) {
            final String token = getIdentityProviderClientToken(idp.getTokenEndpoint());

            if (token != null) {
                final JsonObject payload = parse(token);
                if (payload != null) {
                    final String scope = payload.getString("scope", null);
                    if (scope != null) {
                        idp.setSupportedScopes(Arrays.asList(scope.split("\\s+")));
                    }
                }
            }
        }
        return idp;
    }
    
    public List<String> exchange(String beaconId, List<String> headers) {
        return headers.stream().map(h -> exchangeHeader(beaconId, h)).toList();
    }
    
    private String exchangeHeader(String beaconId, String header) {
        if (header != null && header.startsWith("Bearer ")) {
            final String token = exchangeToken(beaconId, header.substring(7));
            if (token != null) {
                return "Bearer " + token;
            }
        }
        return header;
    }
    
    private String exchangeToken(String beaconId, String token) {
        final JsonObject payload = parse(token);
        if (payload == null) {
            return null;
        }
        
        final String endpoint = network_configuration.getEndpoints().get(beaconId);
        final String issuer = payload.getString("iss", null);
        
        if (idp != null) {
            // restrict audience to the beacon's endpoint
            final String tkn = doTokenExchange(idp, ConfigurationProperties.BN_CLIENT_ID,
                    ConfigurationProperties.BN_CLIENT_SECRET, issuer, endpoint, payload, token);
            if (tkn != null) {
                token = tkn;
            }
        }
        
        final OauthProtectedResource resource = network_configuration.getProtectedResources().get(endpoint);
        if (resource != null) {
            final String client_id = resource.getClientId();
            final List<String> authorization_servers = resource.getAuthorizationServers();
            if (client_id != null && authorization_servers != null) {
                final List<String> audiences;
                final String audience = payload.getString("aud", null);
                if (audience != null) {
                    audiences = List.of(audience);
                } else {
                    final JsonArray aud = payload.getJsonArray("aud");
                    audiences = aud != null 
                            ? aud.getValuesAs(JsonString::getString) 
                            : null;
                }
                
                if (!authorization_servers.contains(issuer) || 
                    (audiences != null && !audiences.contains(client_id))) {
                    // need exchange
                    final List<OidcConfigurationProvider> providers = getWellKnownProviders(authorization_servers);
                    if (providers != null) {
                        for (OidcConfigurationProvider provider : providers) {
                            return doTokenExchange(provider, client_id, null, issuer, null, payload, token);
                        }
                    }
                }
            }
        }
        
        return token;
    }

    private List<OidcConfigurationProvider> getWellKnownProviders(List<String> authorization_servers) {
        
        final List<OidcConfigurationProvider> providers = new ArrayList();
        
        final List<CompletableFuture<HttpResponse<String>>> invocations = new ArrayList();
        for (String authorization_server : authorization_servers) {
            final OidcConfigurationProvider provider = configuration_providers.get(authorization_server);
            if (provider != null) {
                providers.add(provider);
            } else {
                final Builder builder = createWellKnownProviderRequest(authorization_server);
                final CompletableFuture<HttpResponse<String>> future =
                    http_client.sendAsync(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
                invocations.add(future);
            }
        }
        for (CompletableFuture<HttpResponse<String>> invocation : invocations) {
            try {
                final HttpResponse<String> response = invocation.get(30, TimeUnit.SECONDS);
                if (response != null && response.statusCode() < 300) {
                    final String body = response.body();
                    if (body != null) {
                        final OidcConfigurationProvider provider = 
                                jsonb.fromJson(body, OidcConfigurationProvider.class);
                        providers.add(provider);
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                        Level.INFO, ex.getMessage());
            }
        }
        return providers;
    }
    
    private Builder createWellKnownProviderRequest(String authorization_server) {
        return HttpRequest.newBuilder(UriBuilder.fromUri(authorization_server)
                .path(".well-known/openid-configuration").build())
                .header(HttpHeaders.USER_AGENT, "BN/2.0.0")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
    }

    private String getIdentityProviderClientToken(String endpoint) {
        final StringBuilder data = new StringBuilder();
        
        data.append("client_id").append('=').append(ConfigurationProperties.BN_CLIENT_ID);
        if (ConfigurationProperties.BN_CLIENT_SECRET != null) {
            data.append("&client_secret").append('=').append(ConfigurationProperties.BN_CLIENT_SECRET);
        }
        
        data.append("&grant_type=client_credentials");
        
        return getAccessToken(endpoint, data.toString());
    }
    
    private String doTokenExchange(OidcConfigurationProvider provider, String client_id, 
            String client_secret, String subject_issuer, String endpoint, JsonObject payload, String token) {

        final StringBuilder data = new StringBuilder();

        data.append("client_id").append('=').append(client_id)
            .append("&subject_token").append('=').append(token)
            .append("&grant_type").append('=')
                .append(URLEncoder.encode("urn:ietf:params:oauth:grant-type:token-exchange", StandardCharsets.UTF_8))
            .append("&subject_token_type").append('=')
                .append(URLEncoder.encode(
                        Objects.equals(subject_issuer, provider.getIssuer())
                        ? "urn:ietf:params:oauth:token-type:access_token"
                        : "urn:ietf:params:oauth:token-type:jwt", StandardCharsets.UTF_8))
            .append("&requested_token_type").append('=')
                .append(URLEncoder.encode("urn:ietf:params:oauth:token-type:access_token", StandardCharsets.UTF_8));

        // keep only scopes supported by the target client
        final String scope = payload.getString("scope", "");
        final List<String> scopes = new ArrayList(Arrays.asList(scope.split("\\s+")));
        scopes.retainAll(idp.getSupportedScopes());
        if (!scopes.isEmpty()) {
            data.append("&scope=");
            data.append(String.join(" ", scopes));
        }
        
        if (client_secret != null) {
            data.append("&client_secret").append('=').append(client_secret);
        }

        if (endpoint != null) {
            data.append("&resource").append('=').append(endpoint);
        }
        
        return getAccessToken(provider.getTokenEndpoint(), data.toString());
    }
    
    /**
     * Call the request and get back the access token.
     * 
     * @param endpoint the request endpoint
     * @param data the request body
     * 
     * @return access token or null
     */
    private String getAccessToken(String endpoint, String data) {
        
        final Builder builder = HttpRequest.newBuilder(UriBuilder.fromUri(endpoint)
                .build())
                .header(HttpHeaders.USER_AGENT, "BN/2.0.0")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .POST(BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        
        try {
            final HttpResponse<String> response = http_client.send(builder.build(), 
                    BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response != null && response.statusCode() < 300) {
                final String body = response.body();
                if (body != null) {
                    final AccessTokenResponse atResponse = 
                            jsonb.fromJson(body, AccessTokenResponse.class);

                    final String accessToken = atResponse.getAccessToken();
                    if (accessToken != null) {
                        return accessToken;
                    } else {
                        Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                                Level.WARNING, "no access token found in the client token response");

                    }
                } else {
                        Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                                Level.WARNING, "empty client token response");                    
                }                                           
            } else {
                Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                        Level.WARNING, "error getting client access token from {0}", endpoint);
                Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                        Level.WARNING, response == null ? "no answer" : response.body());
            }
        } catch (Exception ex) {
            Logger.getLogger(BeaconNetworkTokenExchanger.class.getName()).log(
                    Level.INFO, ex.getMessage());
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
