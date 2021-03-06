package nl.sikken.bertrik.hab;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Enumeration of possible payload encodings.
 */
public enum EPayloadEncoding {
    
    /** encoding used by the sodaq one in raw format */
    SODAQ_ONE("sodaqone"),
    /** encoding using fields from the "payload_fields" element */
    JSON("json"),
    /** encoding using cayenne */
    CAYENNE("cayenne"),
    /** encoding using custom_format_icss */
    CUSTOM_FORMAT_ICSS("custom_format_icss"),
    ;

    // reverse lookup by name
    private static Map<String, EPayloadEncoding> LOOKUP = new HashMap<>();
    static {
        Stream.of(values()).forEach((v) -> LOOKUP.put(v.name, v)); 
    }
    
    private final String name;
    
    /**
     * Constructor.
     * 
     * @param name the name of the encoding
     */
    EPayloadEncoding(String name) {
        this.name = name;
    }
    
    /**
     * @return the name of this encoding
     */
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
    	return name;
    }
    
    /**
     * Parses an encoding by name.
     * 
     * @param name the name of the encoding
     * @return the corresponding enum value, or null if not found
     */
    public static EPayloadEncoding parse(String name) {
        return LOOKUP.get(name);
    }

}
