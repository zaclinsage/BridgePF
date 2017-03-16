package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.InstantExportService;

/** Play controller for one-time export requests. */
@Controller
public class InstantExportController extends BaseController {
    private InstantExportService instantExportService;

    /** Service handler for instant exporting request. */
    @Autowired
    public void setInstantExportService(InstantExportService instantExportService) {
        this.instantExportService = instantExportService;
    }

    /**
     * Play handler for requesting instant export, only for current study. User must be one of Developer or Researcher.
     */
    public Result requestInstantExport(String endDateTimeStr) throws JsonProcessingException {
        System.out.println(endDateTimeStr + "\n");
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        StudyIdentifier studyIdentifier = session.getStudyIdentifier();

        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        DateTime endDateTime = DateTime.parse(endDateTimeStr, dtf);

        instantExportService.export(studyIdentifier, endDateTime);
        return acceptedResult("Request submitted.");
    }
}
