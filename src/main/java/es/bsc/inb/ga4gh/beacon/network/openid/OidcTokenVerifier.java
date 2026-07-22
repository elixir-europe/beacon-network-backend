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

import es.bsc.inb.ga4gh.beacon.network.config.ConfigurationProperties;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.security.enterprise.authentication.mechanism.http.openid.OpenIdConstant;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OAuth 2.0 tokens verifier.
 * 
 * @author Dmitry Repchevsky
 */

public class OidcTokenVerifier {
        
    private final OidcProvider provider;
    private final String uri;
    
    private List<String> audiences;
    
    /**
     * OidcTokenVerifier public constructor.
     * 
     * @param provider - the identity provider object to verify tokens with
     * @param uri - the sting that must be found in the token's 'aud' or null.
     */
    public OidcTokenVerifier(OidcProvider provider, String uri) {
        this.provider = provider;
        this.uri = uri;
        
        if (ConfigurationProperties.BN_TOKEN_AUDIENCE == null) {
            audiences = Collections.EMPTY_LIST;
        } else if ((ConfigurationProperties.BN_TOKEN_AUDIENCE.startsWith("\"") &&
                    ConfigurationProperties.BN_TOKEN_AUDIENCE.endsWith("\"")) ||
                   ((ConfigurationProperties.BN_TOKEN_AUDIENCE.startsWith("[") &&
                    ConfigurationProperties.BN_TOKEN_AUDIENCE.endsWith("]")))) {
            try (JsonReader reader = Json.createReader(
                    new StringReader(ConfigurationProperties.BN_TOKEN_AUDIENCE))) {
                final JsonValue aud = reader.readValue();
                audiences = parseJsonValue(aud);
            } catch (Exception ex) {
                audiences = Collections.EMPTY_LIST;
            }
        } else {
            audiences = List.of(ConfigurationProperties.BN_TOKEN_AUDIENCE);
        }
    }

    /**
     * Validates token and returns it's body if valid.
     * 
     * @param token token to validate.
     * 
     * @return token payload (claims) if valid, null otherwise.
     */ 
    public JsonObject verify(String token) {
        final String[] parts = token.split("\\.");
        if (parts.length == 3) {
            final JsonObject header = decode64(parts[0]);
            final JsonObject payload = decode64(parts[1]);
            if (header != null && payload != null) {

                // RFC 9068 'iss' REQUIRED
                final String issuer = payload.getString(OpenIdConstant.ISSUER_IDENTIFIER, null);
                if (!Objects.equals(issuer, provider.getIssuer())) {
                    return null;
                }

                // RFC 9068 'exp' REQUIRED
                final JsonNumber exp = payload.getJsonNumber(OpenIdConstant.EXPIRATION_IDENTIFIER);
                if (exp == null || Instant.now().getEpochSecond() > exp.longValue()) {
                    return null; // token expired
                }

                final JsonValue aud = payload.get(OpenIdConstant.AUDIENCE);

                // check "aud" contains 'allowed' audiences and api uri (if defined)
                final List<String> l = parseJsonValue(aud);
                if ((uri != null && !l.contains(uri)) || 
                    (!audiences.isEmpty() && l.stream().noneMatch(audiences::contains))) {
                    return null;
                }
                        
                final String kid = header.getString("kid", null);
                if (kid == null) {
                    return null;
                }

                final String alg = JwtSigningAlgorithms.map.get(header.getString("alg", "none"));
                if (alg == null) {
                    return null;
                }
                
                final JsonObject jwk = provider.getKey(kid);
                if (jwk == null) {
                    return null;
                }
                
                try {
                    final byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
                    if (verifySignature(token, alg, jwk, signature)) {
                        return payload;
                    }
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(OidcTokenVerifier.class.getName()).log(
                        Level.INFO, ex.getMessage());
                }
            }
        }
        Logger.getLogger(OidcTokenVerifier.class.getName()).log(
                Level.INFO, "invalid token");

        return null;
    }
        
    private boolean verifySignature(String token, String alg, JsonObject jwk, 
            byte[] signature) {
                
        final String kty = jwk.getString("kty", null);
        if (kty == null) {
            return false; // no key type
        }
        
        switch(kty) {
            case "EC": return verifyEC(token, alg, jwk, signature);
            case "RSA": return verifyRSA(token, alg, jwk, signature);
            case "OKP": return verifyEDDSA(token, alg, jwk, signature);
        }
        return false;
    }
    
    private boolean verifyRSA(String token, String alg, JsonObject jwk, byte[] signature) {

        final String n = jwk.getString("n", null);
        final String e = jwk.getString("e", null);
        if (n == null || e == null) {
            return false;
        }
        
        final byte[] mod = Base64.getUrlDecoder().decode(n);
        final byte[] exp = Base64.getUrlDecoder().decode(e);
        
        final RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new BigInteger(1, mod), new BigInteger(1, exp));
        
        try {
            final PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(spec);
            final Signature verifier = Signature.getInstance(alg);    
            verifier.initVerify(pub);
            // token = header + '.' + payload + '.' + signature
            // validate only header + '.' + payload part
            final byte[] data = token.getBytes(StandardCharsets.UTF_8);
            verifier.update(data, 0, token.lastIndexOf('.'));
            return verifier.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | 
                 InvalidKeyException | SignatureException ex) {
        }
        
        return false;
    }

    private boolean verifyEC(String token, String alg, JsonObject jwk, byte[] signature) {
        
        final String crv;
        switch (jwk.getString("crv", "")) {
            case "P-256": crv = "secp256r1"; break;
            case "P-384": crv = "secp384r1"; break;
            case "P-521": crv = "secp521r1"; break;
            default: return false;
        }
        
        final String x = jwk.getString("x", null);
        final String y = jwk.getString("y", null);
        
        if (x == null || y == null) {
            return false;
        }
        
        final byte[] _x = Base64.getUrlDecoder().decode(x);
        final byte[] _y = Base64.getUrlDecoder().decode(y);
        
        try {
                    
            final ECPoint ecPoint = new ECPoint(new BigInteger(1, _x), new BigInteger(1, _y));
            
            final AlgorithmParameters algorithm = AlgorithmParameters.getInstance("EC");
            algorithm.init(new ECGenParameterSpec(crv));            
            final ECParameterSpec p = algorithm.getParameterSpec(ECParameterSpec.class);
            
            final ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, p);

            final PublicKey pub = KeyFactory.getInstance("EC").generatePublic(keySpec);
            final Signature verifier = Signature.getInstance(alg);    
            verifier.initVerify(pub);
            final byte[] data = token.getBytes(StandardCharsets.UTF_8);
            verifier.update(data, 0, token.lastIndexOf('.'));
            return verifier.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | 
                 InvalidKeyException | SignatureException | InvalidParameterSpecException ex) {
            Logger.getLogger(OidcTokenVerifier.class.getName()).log(
                    Level.SEVERE, ex.getMessage());
        }
        
        return false;

    }

    private boolean verifyEDDSA(String token, String alg, JsonObject jwk, byte[] signature) {

        final String x = jwk.getString("x", null);
        if (x == null) {
            return false;
        }

        final byte[] key = Base64.getUrlDecoder().decode(x);
        for (int i = 0, n = key.length; i < n; key[i] ^= key[--n], key[n] ^= key[i], key[i++] ^= key[n]) {}
        
        final boolean xOdd = key[0] < 0;
        key[0] &= 0x7F;

        EdECPublicKeySpec spec = new EdECPublicKeySpec(NamedParameterSpec.ED25519, 
                new EdECPoint(xOdd, new BigInteger(key)));
        try {
            final PublicKey pub = KeyFactory.getInstance("EdDSA").generatePublic(spec);
            final Signature verifier = Signature.getInstance(alg);    
            verifier.initVerify(pub);
            final byte[] data = token.getBytes(StandardCharsets.UTF_8);
            verifier.update(data, 0, token.lastIndexOf('.'));
            return verifier.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | 
                 InvalidKeyException | SignatureException ex) {
        }
        
        return false;
    }
    
    private List<String> parseJsonValue(JsonValue value) {
        if (value instanceof JsonString s) {
            return List.of(s.getString());
        } else if (value instanceof JsonArray arr) {
            return arr.stream().filter(JsonString.class::isInstance)
                    .map(JsonString.class::cast)
                    .map(JsonString::getString).toList();
        }
        return Collections.EMPTY_LIST;
    }
    
    private JsonObject decode64(String encoded) {
        try {
            final byte[] json = Base64.getUrlDecoder().decode(encoded);

            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(json))) {
                return reader.readObject();
            } catch (Exception ex) {
                Logger.getLogger(OidcTokenVerifier.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            }
        } catch(IllegalArgumentException ex) {
            Logger.getLogger(OidcTokenVerifier.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }
}
