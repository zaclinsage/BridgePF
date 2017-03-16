package org.sagebionetworks.bridge.services;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Implementation of {@link InstantExportService} that connects to the Bridge Exporter Service via SQS.
 */
@Component
public class InstantExportViaSqsService implements InstantExportService{
    private static final Logger logger = LoggerFactory.getLogger(InstantExportViaSqsService.class);

    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    // constants - these are package scoped so unit tests can access them
    static final String CONFIG_KEY_EXPORTER_SQS_QUEUE_URL = "exporter.request.sqs.queue.url";
    static final String REQUEST_KEY_END_DATE_TIME = "endDateTime";
    static final String REQUEST_KEY_START_DATE_TIME = "startDateTime";
    static final String REQUEST_KEY_STUDY_WHITE_LIST = "studyWhitelist";
    static final String REQUEST_KEY_STUDY_ID= "studyId";
    static final String LAST_EXPORT_DATE_TIME = "lastExportDateTime";
    static final String TIME_ZONE_ID = "America/Los_Angeles";

    private BridgeConfig bridgeConfig;
    private AmazonSQSClient sqsClient;
    private Table ddbExportTimeTable;
    private DateTimeZone timeZone = DateTimeZone.forID(TIME_ZONE_ID);

    /** Bridge config, used to get the SQS queue URL. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    /** SQS client. */
    @Autowired
    public final void setSqsClient(AmazonSQSClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    /** DDB Export Time Table. */
    @Resource(name = "ddbExportTimeTable")
    final void setDdbExportTimeTable(Table ddbExportTimeTable) {
        this.ddbExportTimeTable = ddbExportTimeTable;
    }

    /** {@inheritDoc} */
    @Override
    public void export(@Nonnull StudyIdentifier studyIdentifier, @Nonnull DateTime endDateTime)
            throws JsonProcessingException {

        // first check if specified studyid has lastexportdatetime
        DateTime lastExportDateTime;

        Item item = ddbExportTimeTable.getItem(REQUEST_KEY_STUDY_ID, studyIdentifier.getIdentifier());
        if (item != null) {
            lastExportDateTime = new DateTime(item.getLong(LAST_EXPORT_DATE_TIME), timeZone);
        } else {
            // if no such field, just set last export date time to the midnight of today
            DateTime startDateTime = endDateTime
                    .withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
            lastExportDateTime = startDateTime;
        }

        String studyId = studyIdentifier.getIdentifier();
        String startDateStr = lastExportDateTime.toString();
        String endDateStr = endDateTime.toString();

        // wrap msg as nested json node
        ObjectNode requestNode = JSON_OBJECT_MAPPER.createObjectNode();

        ArrayNode studyWhitelist = JSON_OBJECT_MAPPER.createArrayNode();
        studyWhitelist.add(studyId);

        requestNode.set(REQUEST_KEY_STUDY_WHITE_LIST, studyWhitelist);
        requestNode.put(REQUEST_KEY_START_DATE_TIME, startDateStr);
        requestNode.put(REQUEST_KEY_END_DATE_TIME, endDateStr);

        String requestJson = JSON_OBJECT_MAPPER.writeValueAsString(requestNode);

        // send to SQS
        String queueUrl = bridgeConfig.getProperty(CONFIG_KEY_EXPORTER_SQS_QUEUE_URL);
        SendMessageResult sqsResult = sqsClient.sendMessage(queueUrl, requestJson);
        logger.info("Sent exporting request to SQS for hash[username]=" + ", study=" + studyId +
                ", startDate=" + startDateStr + ", endDate=" + endDateStr + "; received message ID=" +
                sqsResult.getMessageId());
    }
}
