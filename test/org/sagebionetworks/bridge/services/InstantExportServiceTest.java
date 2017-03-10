package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.services.InstantExportViaSqsService.CONFIG_KEY_EXPORTER_SQS_QUEUE_URL;
import static org.sagebionetworks.bridge.services.InstantExportViaSqsService.LAST_EXPORT_DATE_TIME;
import static org.sagebionetworks.bridge.services.InstantExportViaSqsService.REQUEST_KEY_END_DATE_TIME;
import static org.sagebionetworks.bridge.services.InstantExportViaSqsService.REQUEST_KEY_START_DATE_TIME;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_STUDY_ID;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.config.BridgeConfig;

public class InstantExportServiceTest {
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    private static final String START_DATE_TIME = "2013-11-15T00:00:00.000-08:00";
    private static final String END_DATE_TIME = "2013-11-15T08:00:00.000-08:00";

    private DateTime startDateTime;
    private DateTime endDateTime;
    private BridgeConfig mockConfig;
    private AmazonSQSClient mockSqsClient;
    private ArgumentCaptor<String> sqsMessageCaptor;

    @Before
    public void before() throws Exception {
        // setup start/end date time
        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        startDateTime = DateTime.parse(START_DATE_TIME, dtf);
        endDateTime = DateTime.parse(END_DATE_TIME, dtf);

        // mock config
        mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(CONFIG_KEY_EXPORTER_SQS_QUEUE_URL)).thenReturn("dummy-sqs-url");

        // mock SQS
        mockSqsClient = mock(AmazonSQSClient.class);
        SendMessageResult mockSqsResult = new SendMessageResult().withMessageId("dummy-message-id");
        sqsMessageCaptor = ArgumentCaptor.forClass(String.class);
        when(mockSqsClient.sendMessage(eq("dummy-sqs-url"), sqsMessageCaptor.capture())).thenReturn(mockSqsResult);
    }

    @Test
    public void testWithLastExportDateTime() throws Exception {
        // mock ddb table and item
        Table mockDdbTable = mock(Table.class);
        Item mockItem = new Item().withLong(LAST_EXPORT_DATE_TIME, startDateTime.getMillis());
        when(mockDdbTable.getItem(REQUEST_KEY_STUDY_ID, TEST_STUDY_IDENTIFIER)).thenReturn(mockItem);

        // set up test service
        InstantExportViaSqsService testService = new InstantExportViaSqsService();
        testService.setBridgeConfig(mockConfig);
        testService.setSqsClient(mockSqsClient);
        testService.setDdbExportTimeTable(mockDdbTable);

        // execute
        testService.export(TEST_STUDY, endDateTime);

        // verify ddb table
        verify(mockDdbTable).getItem(eq(REQUEST_KEY_STUDY_ID), eq(TEST_STUDY_IDENTIFIER));

        // Validate SQS args.
        verify(mockSqsClient).sendMessage(eq("dummy-sqs-url"), any());
        String sqsMessageText = sqsMessageCaptor.getValue();

        JsonNode sqsMessageNode = JSON_OBJECT_MAPPER.readTree(sqsMessageText);

        // first assert parent node
        assertEquals(sqsMessageNode.size(), 3);

        // then assert white list
        JsonNode studyWhitelist = sqsMessageNode.path("studyWhitelist");
        if (studyWhitelist.isArray()) {
            JsonNode objNode = studyWhitelist.get(0);
            assertEquals(TEST_STUDY_IDENTIFIER, objNode.asText());
        }

        assertEquals(START_DATE_TIME, sqsMessageNode.get(REQUEST_KEY_START_DATE_TIME).textValue());
        assertEquals(END_DATE_TIME, sqsMessageNode.get(REQUEST_KEY_END_DATE_TIME).textValue());
    }

    @Test
    public void testWithoutLastExportDateTime() throws Exception {
        // mock ddb table without item
        Table mockDdbTable = mock(Table.class);

        // set up test service
        InstantExportViaSqsService testService = new InstantExportViaSqsService();
        testService.setBridgeConfig(mockConfig);
        testService.setSqsClient(mockSqsClient);
        testService.setDdbExportTimeTable(mockDdbTable);

        // execute
        testService.export(TEST_STUDY, endDateTime);

        // verify ddb table
        verify(mockDdbTable).getItem(eq(REQUEST_KEY_STUDY_ID), eq(TEST_STUDY_IDENTIFIER));

        // Validate SQS args.
        verify(mockSqsClient).sendMessage(eq("dummy-sqs-url"), any());
        String sqsMessageText = sqsMessageCaptor.getValue();

        JsonNode sqsMessageNode = JSON_OBJECT_MAPPER.readTree(sqsMessageText);

        // first assert parent node
        assertEquals(sqsMessageNode.size(), 3);

        // then assert white list
        JsonNode studyWhitelist = sqsMessageNode.path("studyWhitelist");
        if (studyWhitelist.isArray()) {
            JsonNode objNode = studyWhitelist.get(0);
            assertEquals(TEST_STUDY_IDENTIFIER, objNode.asText());
        }

        assertEquals(START_DATE_TIME, sqsMessageNode.get(REQUEST_KEY_START_DATE_TIME).textValue());
        assertEquals(END_DATE_TIME, sqsMessageNode.get(REQUEST_KEY_END_DATE_TIME).textValue());
    }
}
