package nl.sikken.bertrik.hab.habitat.docs;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Payload telemetry document.
 * 
 * SEE http://habitat.habhub.org/jse/#schemas/payload_telemetry.json
 */
public final class PayloadTelemetryDoc {

    private final DateTimeFormatter dateFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final OffsetDateTime dateCreated;
    private final OffsetDateTime dateUploaded;
    private final String callSign;
    private final byte[] rawBytes;

    /**
     * Constructor.
     * 
     * @param instant the creation/upload date/time
     * @param callSign the receiver call sign
     * @param rawBytes the raw telemetry string as bytes
     */
    public PayloadTelemetryDoc(Instant instant, String callSign, byte[] rawBytes) {
        this.dateCreated = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        this.dateUploaded = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        this.callSign = callSign;
        this.rawBytes = rawBytes;
    }

    /**
     * @return the payload telemetry doc as JSON string
     */
    public String format() {
        JsonNodeFactory factory = new JsonNodeFactory(false);
        ObjectNode topNode = factory.objectNode();

        // create data node
        ObjectNode dataNode = factory.objectNode();
        dataNode.set("_raw", factory.binaryNode(rawBytes));

        // create receivers node
        ObjectNode receiverNode = factory.objectNode();
        receiverNode.set("time_created", factory.textNode(dateFormat.format(dateCreated)));
        receiverNode.set("time_uploaded", factory.textNode(dateFormat.format(dateUploaded)));
        ObjectNode receiversNode = factory.objectNode();
        receiversNode.set(callSign, receiverNode);

        // put it together in the top node
        topNode.set("data", dataNode);
        topNode.set("receivers", receiversNode);

        return topNode.toString();
    }
}