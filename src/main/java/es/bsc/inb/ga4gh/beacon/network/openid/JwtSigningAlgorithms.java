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

import java.util.Map;

/**
 * Keeps a mapping between JWT "alg" and Java JCA Signature algorithms names.
 * 
 * @author Dmitry Repchevsky
 */

public class JwtSigningAlgorithms {
 
    public final static Map<String, String> map;
    
    static {
        map = Map.ofEntries(
            Map.entry("HS256", "HmacSHA256"),
            Map.entry("HS384", "HmacSHA384"),
            Map.entry("HS512", "HmacSHA512"),
            Map.entry("RS256", "SHA256withRSA"),
            Map.entry("RS384", "SHA384withRSA"),
            Map.entry("RS512", "SHA512withRSA"),
            Map.entry("PS256", "RSASSA-PSS"),
            Map.entry("PS384", "RSASSA-PSS"),
            Map.entry("PS512", "RSASSA-PSS"),
            Map.entry("ES256", "SHA256withECDSAinP1363Format"), // plain (not DER format)
            Map.entry("ES384", "SHA384withECDSAinP1363Format"), // plain (not DER format)
            Map.entry("ES512", "SHA512withECDSAinP1363Format"), // plain (not DER format)
            Map.entry("EdDSA", "Ed25519")
        );
    }
}
