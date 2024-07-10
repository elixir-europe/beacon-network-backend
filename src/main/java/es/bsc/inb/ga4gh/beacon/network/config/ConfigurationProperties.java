package es.bsc.inb.ga4gh.beacon.network.config;

/**
 * @author Dmitry Repchevsky
 */

public final class ConfigurationProperties {
    
    public final static String BN_CONFIG_DIR_PROPERTY_NAME = "BEACON_NETWORK_CONFIG_DIR";
    public final static String BN_CACHE_TIMEOUT_PROPERTY_NAME = "BEACON_NETWORK_CACHE_TIMEOUT";
    public final static String BN_REQUEST_TIMEOUT_PROPERTY_NAME = "BEACON_NETWORK_REQUEST_TIMEOUT";
    
    public final static String BN_CONFIG_DIR_PROPERTY;
    public final static long BN_CACHE_TIMEOUT_PROPERTY;
    public final static long BN_REQUEST_TIMEOUT_PROPERTY;
    
    static {
        BN_CONFIG_DIR_PROPERTY = System.getenv(BN_CONFIG_DIR_PROPERTY_NAME);
        
        final String cache_timeout = System.getenv(BN_CACHE_TIMEOUT_PROPERTY_NAME);
        if (cache_timeout == null) {
            BN_CACHE_TIMEOUT_PROPERTY = 5;
        } else {
            long bn_cache_timeout;
            try {
                bn_cache_timeout = Long.parseLong(cache_timeout);
            } catch (NumberFormatException ex) {
                bn_cache_timeout = 5;
            }
            BN_CACHE_TIMEOUT_PROPERTY = bn_cache_timeout;
        }
        
        final String request_timeout = System.getenv(BN_REQUEST_TIMEOUT_PROPERTY_NAME);
        if (request_timeout == null) {
            BN_REQUEST_TIMEOUT_PROPERTY = 600;
        } else {
            long bn_request_timeout;
            try {
                bn_request_timeout = Long.parseLong(request_timeout);
            } catch (NumberFormatException ex) {
                bn_request_timeout = 600;
            }
            BN_REQUEST_TIMEOUT_PROPERTY = bn_request_timeout;
        }
    }
}
