package es.bsc.inb.ga4gh.beacon.network.log;

/**
 * @author Dmitry Repchevsky
 */

public enum BeaconLogLevel {
   
    NONE, 
    METADATA, 
    REQUESTS,
    RESPONSES,
    ALL;
    
    public final static String BEACON_NETWORK_LOG_LEVEL = "BEACON_NETWORK_LOG_LEVEL";
    
    public final static BeaconLogLevel LEVEL;
    
    static {
        
        final String level = System.getenv(BEACON_NETWORK_LOG_LEVEL);
        if (level == null) {
            LEVEL = NONE;
        } else {
            BeaconLogLevel value = NONE;
            try {
                value = BeaconLogLevel.valueOf(level);
            } catch (IllegalArgumentException ex) {}
            LEVEL = value;
        }
    }
}
