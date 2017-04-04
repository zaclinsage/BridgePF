package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;

import play.mvc.Result;

@Controller("externalIdController")
public class ExternalIdController extends BaseController {
    
    private static final TypeReference<List<String>> EXTERNAL_ID_TYPE_REF = new TypeReference<List<String>>() {};

    private ExternalIdService externalIdService;
    
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    public Result getExternalIds(String offsetKey, String pageSizeString, String idFilter, String assignmentFilterString) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // Play will not convert these to null if they are not included in the query string, so we must do the conversion.
        Integer pageSize = (pageSizeString != null) ? Integer.parseInt(pageSizeString,10) : null;
        Boolean assignmentFilter = (assignmentFilterString != null) ? Boolean.valueOf(assignmentFilterString) : null;
        
        PagedResourceList<ExternalIdentifierInfo> page = externalIdService.getExternalIds(
                study, offsetKey, pageSize, idFilter, assignmentFilter);
        return okResult(page);
    }
    
    public Result addExternalIds() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        List<String> externalIdentifiers = MAPPER.convertValue(requestToJSON(request()), EXTERNAL_ID_TYPE_REF);
        externalIdService.addExternalIds(study, externalIdentifiers);
        
        return createdResult("External identifiers added.");
    }
    
    public Result deleteExternalIds() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        String[] externalIds = request().queryString().get("externalId");
        if (externalIds == null || externalIds.length == 0) {
            throw new BadRequestException("No external IDs provided in query string.");
        }
        List<String> identifiers = Lists.newArrayList(externalIds);
        externalIdService.deleteExternalIds(study, identifiers);
        
        return okResult("External identifiers deleted.");
    }

}
