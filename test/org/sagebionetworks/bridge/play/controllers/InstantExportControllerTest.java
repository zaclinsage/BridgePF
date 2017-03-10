package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.InstantExportService;

@RunWith(MockitoJUnitRunner.class)
public class InstantExportControllerTest {
    private StudyIdentifier studyIdentifier;
    private InstantExportController controller;

    @Mock
    private UserSession mockSession;

    @Mock
    private InstantExportService mockService;

    @Captor
    private ArgumentCaptor<DateTime> dateTimeArgumentCaptor;

    @Before
    public void before() throws Exception {
        controller = spy(new InstantExportController());
        controller.setInstantExportService(mockService);
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
        studyIdentifier = new StudyIdentifierImpl("test-study");
        when(mockSession.getStudyIdentifier()).thenReturn(studyIdentifier);
        when(mockSession.isAuthenticated()).thenReturn(true);
    }

    @Test
    public void test() throws Exception {
        String endDateTimeStr = "2013-11-15T08:00:00.000-08:00";

        // execute and validate
        Result result = controller.requestInstantExport(endDateTimeStr);
        assertEquals(202, result.status());

        verify(mockService).export(eq(studyIdentifier), dateTimeArgumentCaptor.capture());

        // validate args sent to mock service
        DateTime endDateTime = dateTimeArgumentCaptor.getValue();
        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        assertEquals(DateTime.parse(endDateTimeStr, dtf).getMillis(), endDateTime.getMillis());
    }
}
