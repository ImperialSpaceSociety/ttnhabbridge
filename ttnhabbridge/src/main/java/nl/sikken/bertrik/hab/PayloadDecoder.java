package nl.sikken.bertrik.hab;

import java.nio.BufferUnderflowException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.sikken.bertrik.cayenne.CayenneException;
import nl.sikken.bertrik.cayenne.CayenneItem;
import nl.sikken.bertrik.cayenne.CayenneMessage;
import nl.sikken.bertrik.cayenne.ECayenneItem;
import nl.sikken.bertrik.hab.ttn.TtnMessage;

/**
 * Decodes a payload and encodes it into a UKHAS sentence.
 */
public final class PayloadDecoder {
    
    private static final Logger LOG = LoggerFactory.getLogger(PayloadDecoder.class);
    
    private final EPayloadEncoding encoding;
    
    /**
     * Constructor.
     * 
     * @param encodingName the payload encoding name
     */
    public PayloadDecoder(String encodingName) {
        LOG.info("Payload decoder initialised for '{}' format", encodingName);
        this.encoding = Objects.requireNonNull(EPayloadEncoding.parse(encodingName));
    }
    
    /**
     * Decodes a TTN message into a UKHAS sentence.
     * 
     * @param message the message as received from TTN
     * @return the UKHAS sentence
     * @throws DecodeException in case of a problem decoding the message
     */
    public Sentence decode(TtnMessage message) throws DecodeException {
        // common fields
        String callSign = message.getDevId();
        int counter = message.getCounter();

        // specific fields
        Sentence sentence;
        switch (encoding) {
        case SODAQ_ONE:
            sentence = decodeSodaqOne(message, callSign, counter);
            break;
        case JSON:
            sentence = decodeJson(message, callSign, counter);
            break;
        case CAYENNE:
            sentence = decodeCayenne(message, callSign, counter);
            break;
        default:
            throw new IllegalStateException("Unhandled encoding " + encoding);
        }
        
        return sentence;
    }
    
    /**
     * Decodes a sodaqone encoded payload.
     * 
     * @param message the TTN message
     * @param callSign the call sign
     * @param counter the counter
     * @return the UKHAS sentence
     * @throws DecodeException in case of a problem decoding the message
     */
    private Sentence decodeSodaqOne(TtnMessage message, String callSign, int counter) throws DecodeException {
        LOG.info("Decoding 'sodaqone' message...");
        
        try {
            // SODAQ payload
            SodaqOnePayload sodaq = SodaqOnePayload.parse(message.getPayloadRaw());
            
            // construct a sentence
            double latitude = sodaq.getLatitude();
            double longitude = sodaq.getLongitude();
            double altitude = sodaq.getAltitude();
            Instant instant = Instant.ofEpochSecond(sodaq.getTimeStamp());
            Sentence sentence = new Sentence(callSign, counter, instant, latitude, longitude, altitude);
            sentence.addField(String.format(Locale.ROOT, "%.0f", sodaq.getBoardTemp()));
            sentence.addField(String.format(Locale.ROOT, "%.2f", sodaq.getBattVoltage()));
            return sentence;
        } catch (BufferUnderflowException e) {
            throw new DecodeException("Error decoding sodaqone", e);
        }
    }

    /**
     * Decodes a JSON encoded payload.
     * 
     * @param message the TTN message
     * @param callSign the call sign
     * @param counter the counter
     * @return the UKHAS sentence
     * @throws DecodeException in case of a problem decoding the message
     */
    private Sentence decodeJson(TtnMessage message, String callSign, int counter) throws DecodeException {
        LOG.info("Decoding 'json' message...");
    
        try {
            Instant time = message.getMetaData().getTime();
            ObjectNode fields = message.getPayloadFields();
            double latitude = fields.get("lat").doubleValue();
            double longitude = fields.get("lon").doubleValue();
            double altitude = fields.get("gpsalt").doubleValue();
            Sentence sentence = new Sentence(callSign, counter, time, latitude, longitude, altitude);
            JsonNode tempNode = fields.get("temp");
            JsonNode vccNode = fields.get("vcc");
            if ((tempNode != null) && (vccNode != null)) {
                sentence.addField(String.format(Locale.ROOT, "%.1f", tempNode.doubleValue()));
                sentence.addField(String.format(Locale.ROOT, "%.3f", vccNode.doubleValue()));
            }
            return sentence;
        } catch (RuntimeException e) {
            throw new DecodeException("Error decoding json", e);
        }
    }

    /**
     * Decodes a cayenne encoded payload.
     * 
     * @param message the TTN message
     * @param callSign the call sign
     * @param counter the counter
     * @return the UKHAS sentence
     * @throws DecodeException
     */
    private Sentence decodeCayenne(TtnMessage message, String callSign, int counter) throws DecodeException {
        LOG.info("Decoding 'cayenne' message...");
        
        try {
            Instant time = message.getMetaData().getTime();
            CayenneMessage cayenne = CayenneMessage.parse(message.getPayloadRaw());
            
            // decode location
            CayenneItem gpsItem = cayenne.ofType(ECayenneItem.GPS_LOCATION);
            Double[] location = gpsItem.getValues();
            double latitude = location[0];
            double longitude = location[1];
            double altitude = location[2];
            Sentence sentence = new Sentence(callSign, counter, time, latitude, longitude, altitude);

            // temperature and battery
            CayenneItem tempItem = cayenne.ofType(ECayenneItem.TEMPERATURE);
            CayenneItem battItem = cayenne.ofType(ECayenneItem.ANALOG_INPUT);
            if ((tempItem != null) && (battItem != null)) {
                sentence.addField(tempItem.format()[0]);
                sentence.addField(battItem.format()[0]);
            }
            
            return sentence;
        } catch (CayenneException e) {
            throw new DecodeException("Error decoding cayenne", e);
        }
    }
    
}