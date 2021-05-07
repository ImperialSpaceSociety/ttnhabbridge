package nl.sikken.bertrik.hab;

import java.nio.BufferUnderflowException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.sikken.bertrik.cayenne.CayenneException;
import nl.sikken.bertrik.cayenne.CayenneItem;
import nl.sikken.bertrik.cayenne.CayenneMessage;
import nl.sikken.bertrik.cayenne.ECayennePayloadFormat;
import nl.sikken.bertrik.hab.ttn.TtnUplinkMessage;

/**
 * Decodes a payload and encodes it into a UKHAS sentence.
 */
public final class PayloadDecoder {
    
    private static final Logger LOG = LoggerFactory.getLogger(PayloadDecoder.class);
    
    private final EPayloadEncoding encoding;
    
    /**
     * Constructor.
     * 
     * @param encoding the payload encoding
     */
    public PayloadDecoder(EPayloadEncoding encoding) {
        LOG.info("Payload decoder initialised for '{}' format", encoding);
        this.encoding = Objects.requireNonNull(encoding);
    }
    
    /**
     * Decodes a TTN message into a UKHAS sentence.
     * 
     * @param message the message as received from TTN
     * @return the UKHAS sentence
     * @throws DecodeException in case of a problem decoding the message
     */
    public Sentence decode(TtnUplinkMessage message) throws DecodeException {
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
        case CUSTOM_FORMAT_ICSS:
            sentence = decodeCUSTOM_FORMAT_ICSS(message, callSign, counter);
            break;
        default:
            throw new IllegalStateException("Unhandled encoding " + encoding);
        }
        
        return sentence;
    }
    /**
     * Decodes a CUSTOM_FORMAT_ICSS encoded payload.
     * 
     * @param message the TTN message
     * @param callSign the call sign
     * @param counter the counter
     * @return the UKHAS sentence
     * @throws DecodeException in case of a problem decoding the message
     */
    private Sentence decodeCUSTOM_FORMAT_ICSS(TtnMessage message, String callSign, int counter) throws DecodeException {
        LOG.info("Decoding 'CUSTOM_FORMAT_ICSS' message...");
        
        try {
            // CUSTOM_FORMAT_ICSS payload
            Instant time = message.getMetaData().getTime();
            int unix_time_of_message = (int)time.getEpochSecond();

            ICSSPayload icsspayload = ICSSPayload.parse(message.getPayloadRaw(),unix_time_of_message);
            LOG.info("ICSS payload:"+icsspayload.toString());

            
            // construct a sentence
            double latitude = icsspayload.getLatitude();
            double longitude = icsspayload.getLongitude();
            int altitude = icsspayload.getAltitude();
            Sentence sentence = new Sentence(callSign, counter, time);
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getPressure()));
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getBoardTemp()));
            sentence.addField(String.format(Locale.ROOT, "%.6f", latitude));
            sentence.addField(String.format(Locale.ROOT, "%.6f", longitude));
            sentence.addField(String.format(Locale.ROOT, "%d", altitude));
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getloadVoltage()));
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getnoloadVoltage()));
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getData_received_flag()));
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getReset_cnt()));
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getNumSats()));
            sentence.addField(String.format(Locale.ROOT, "%d", icsspayload.getDays_of_playback()));

            return sentence;
        } catch (BufferUnderflowException e) {
            throw new DecodeException("Error decoding CUSTOM_FORMAT_ICSS", e);
        }
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
    private Sentence decodeSodaqOne(TtnUplinkMessage message, String callSign, int counter) throws DecodeException {
        LOG.info("Decoding 'sodaqone' message...");
        
        try {
            // SODAQ payload
            SodaqOnePayload sodaq = SodaqOnePayload.parse(message.getPayloadRaw());
            
            // construct a sentence
            double latitude = sodaq.getLatitude();
            double longitude = sodaq.getLongitude();
            double altitude = sodaq.getAltitude();
            Instant instant = Instant.ofEpochSecond(sodaq.getTimeStamp());
            Sentence sentence = new Sentence(callSign, counter, instant);
            sentence.addField(String.format(Locale.ROOT, "%.6f", latitude));
            sentence.addField(String.format(Locale.ROOT, "%.6f", longitude));
            sentence.addField(String.format(Locale.ROOT, "%.1f", altitude));
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
    private Sentence decodeJson(TtnUplinkMessage message, String callSign, int counter) throws DecodeException {
        LOG.info("Decoding 'json' message...");
    
        try {
            Instant time = message.getTime();
            Map<String, Object> fields = message.getPayloadFields();
            double latitude = parseDouble(fields.get("lat"));
            double longitude = parseDouble(fields.get("lon"));
            double altitude = parseDouble(fields.get("gpsalt"));
            Sentence sentence = new Sentence(callSign, counter, time);
            sentence.addField(String.format(Locale.ROOT, "%.6f", latitude));
            sentence.addField(String.format(Locale.ROOT, "%.6f", longitude));
            sentence.addField(String.format(Locale.ROOT, "%.1f", altitude));
            
            if (fields.containsKey("temp") && fields.containsKey("vcc")) {
                Double temp = parseDouble(fields.get("temp"));
                Double vcc = parseDouble(fields.get("vcc"));
                sentence.addField(String.format(Locale.ROOT, "%.1f", temp));
                sentence.addField(String.format(Locale.ROOT, "%.3f", vcc));
            }
            return sentence;
        } catch (RuntimeException e) {
            throw new DecodeException("Error decoding json", e);
        }
    }

    private Double parseDouble(Object object) throws DecodeException {
        if (object instanceof Number) {
            Number number = (Number) object;
            return number.doubleValue();
        }
        if (object instanceof String) {
            String string = (String) object;
            return Double.parseDouble(string);
        }
        throw new DecodeException("Cannot decode " + object);
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
    private Sentence decodeCayenne(TtnUplinkMessage message, String callSign, int counter) throws DecodeException {
        LOG.info("Decoding 'cayenne' message...");
        
        try {
            Instant time = message.getTime();
            Sentence sentence = new Sentence(callSign, counter, time);
            ECayennePayloadFormat cayenneFormat = ECayennePayloadFormat.fromPort(message.getPort());
            CayenneMessage cayenne = new CayenneMessage(cayenneFormat);
            cayenne.parse(message.getPayloadRaw());

            // add all items, in the order they appear in the cayenne message
            for (CayenneItem item : cayenne.getItems()) {
        		for (String s : item.format()) {
        			sentence.addField(s);
        		}
            }
			
            return sentence;
        } catch (CayenneException e) {
            throw new DecodeException("Error decoding cayenne", e);
        }
    }
    
}
