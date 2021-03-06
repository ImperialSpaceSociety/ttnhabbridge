package nl.sikken.bertrik.hab;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decoding according to "custom_format_icss" format, as used in (for example):
 */
public final class ICSSPayload {

    private final int loadVoltage;
    private final int noloadVoltage;
    private final byte boardTemp;
    private final float latitude;
    private final float longitude;
    private final int altitude;
    private final int numSats;
    private final int pressure;
    private final int data_received_flag;
    private final int reset_cnt;
    private final List<past_postion_time> past_position_times;
    private final int days_of_playback;

    private static final Logger LOG = LoggerFactory.getLogger(ICSSPayload.class);
    
    private static final int current_data_size = 11;  // Number of bytes to store the current position and temperature/sensor info
    private static final int past_data_size = 8;  // Number of bytes to store one set of past long,lat,alt, timestamp

    
    /**
     * Constructor.
     * 
     * @param loadVoltage the solar voltage under GPS load (volts)
     * @param noloadVoltage the solar voltage NOT under GPS load (volts)
     * @param boardTemp the board temperature (degrees celcius)
     * @param pressure the board pressure (millibars)
     * @param latitude the latitude (units of 1E-7)
     * @param longitude the longitude (units of 1E-7)
     * @param altitude the altitude (unit?)
     * @param numSats number of satellites used in fix
	 * @param past_position_times
	 * @param days_of_playback Number of days of playback stored on the tracker
	 */
    public ICSSPayload( int loadVoltage, int noloadVoltage, byte boardTemp, float latitude,
    		float longitude, int altitude, int numSats, int pressure, int data_received_flag, int reset_cnt,
    		List<past_postion_time> past_position_times, int days_of_playback) {
    	this.loadVoltage = loadVoltage;
    	this.noloadVoltage = noloadVoltage;
    	this.boardTemp = boardTemp;
    	this.latitude = latitude;
    	this.longitude = longitude;
    	this.altitude = altitude;
    	this.numSats = numSats;
    	this.pressure = pressure;
    	this.data_received_flag = data_received_flag;
    	this.reset_cnt = reset_cnt;
    	this.past_position_times = past_position_times;
    	this.days_of_playback = days_of_playback;
    }

	/**
     * Parses a raw buffer into a ICSS custom payload object.
     * 
     * @param raw the raw buffer
     * @return a parsed object
     * @throws BufferUnderflowException in case of a buffer underflow
     */
    public static ICSSPayload parse(byte[] raw, int current_time) throws BufferUnderflowException {
    	int payload_size = raw.length;
    	int n_past_positions = (payload_size - current_data_size)/past_data_size;
    	
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        byte byte0  = bb.get();
        byte byte1  = bb.get();
        byte byte2  = bb.get();
        byte byte3  = bb.get();

        int noloadVoltage = ((byte0 >> 3) & 0b00011111) + 18;
        int loadVoltage = (((byte0 << 2) & 0b00011100) | ((byte1 >> 6) & 0b00000011)) + 18;
        int days_of_playback = (byte1 & 0b00111111);
        int pressure = ((byte2 >> 1) & 0b01111111) * 10;
        int data_received_flag = byte2 & 0b00000001;
        
        int numSats = (byte3>>3) & 0b00011111;
        int reset_cnt = byte3 & 0b00000111;
        
        byte boardTemp = bb.get();

        
        float latitude = (float) ((bb.getShort() * 0xFFFF) / 1e7);
        float longitude = (float) ((bb.getShort() * 0xFFFF) / 1e7);
        int altitude = ((bb.getShort() & 0xFFFF) * 0xFF) / 1000;

        
        
                
        List<past_postion_time> past_position_times = new ArrayList<past_postion_time>();
        StringBuilder past_pos_str = new StringBuilder();
        
        for(int i=0;i<n_past_positions;i++){  
        	
            float latitude_temp = (float) ((bb.getShort() *  0xFFFF) / 1e7);
            float longitude_temp = (float) ((bb.getShort() *  0xFFFF) / 1e7);
            int altitude_temp = ((bb.getShort() & 0xFFFF) * 0xFF) / 1000;
            long ts_temp = current_time -  ((bb.get() & 0xFF) | ((bb.get() & 0xFF) << 8)) * 60;
        	
            past_postion_time ppt = new past_postion_time(longitude_temp, latitude_temp, altitude_temp, ts_temp);
            past_position_times.add(ppt);
            past_pos_str.append(ppt.toString());
            past_pos_str.append(System.lineSeparator());


            
        }
        
        LOG.info(System.lineSeparator()+past_pos_str);
        

        

        
        return new ICSSPayload(loadVoltage, noloadVoltage, boardTemp, latitude, longitude, altitude,  numSats,
        		pressure, data_received_flag, reset_cnt, past_position_times,days_of_playback);
    }


    public int getloadVoltage() {
        return loadVoltage;
    }
    
    public int getnoloadVoltage() {
        return noloadVoltage;
    }

    public byte getBoardTemp() {
        return boardTemp;
    }

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    public int getAltitude() {
        return altitude;
    }

    public int getNumSats() {
        return numSats;
    }

    public int getPressure() {
        return pressure;
    }
    
    public int getData_received_flag() {
        return data_received_flag;
    }
    
    public int getReset_cnt() {
        return reset_cnt;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "playback_days=%d, load_voltage=%d, no_load_voltage=%d, temp=%d, lat=%f, lon=%f, alt=%d, request_error=%d, reset_cnt=%d, pressure=%d, numsats=%d",
        		days_of_playback,loadVoltage, noloadVoltage, boardTemp, latitude, longitude, 
        		altitude, data_received_flag, reset_cnt, pressure, numSats);
    }

	public List<past_postion_time> getPast_position_times() {
		return past_position_times;
	}

	public int getDays_of_playback() {
		return days_of_playback;
	}

}

