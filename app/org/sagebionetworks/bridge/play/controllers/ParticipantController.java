package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.BodyParser;
import play.mvc.Result;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.ParticipantService;

@Controller
public class ParticipantController extends BaseController {
    
    private ParticipantService participantService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    public Result getSelfParticipant() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(study, session.getId(), false);
        
        String ser = StudyParticipant.API_NO_HEALTH_CODE_WRITER.writeValueAsString(participant);
        
        return ok(ser).as(BridgeConstants.JSON_MIME_TYPE);
    }
    
    public Result updateSelfParticipant() throws Exception {
        UserSession session = getAuthenticatedSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        // By copying only values that were included in the JSON onto the existing StudyParticipant,
        // we allow clients to only send back partial JSON to update the user. This has been the 
        // usage pattern in prior APIs and it will make refactoring to use this API easier.
        JsonNode node = requestToJSON(request());
        Set<String> fieldNames = Sets.newHashSet(node.fieldNames());

        StudyParticipant participant = MAPPER.treeToValue(node, StudyParticipant.class);
        StudyParticipant existing = participantService.getParticipant(study, session.getId(), false);
        StudyParticipant updated = new StudyParticipant.Builder()
                .copyOf(existing)
                .copyFieldsOf(participant, fieldNames)
                .withId(session.getId()).build();
        participantService.updateParticipant(study, NO_CALLER_ROLES, updated);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(session.getParticipant().getLanguages())
                .withClientInfo(getClientInfoFromUserAgentHeader())
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withUserDataGroups(updated.getDataGroups())
                .withStudyIdentifier(session.getStudyIdentifier())
                .build();
        
        sessionUpdateService.updateParticipant(session, context, updated);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result getParticipants(String offsetByString, String pageSizeString, String emailFilter,
            String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        
        Study study = studyService.getStudy(session.getStudyIdentifier());
        int offsetBy = getIntOrDefault(offsetByString, 0);
        int pageSize = getIntOrDefault(pageSizeString, API_DEFAULT_PAGE_SIZE);
        DateTime startDate = DateUtils.getDateTimeOrDefault(startDateString, null);
        DateTime endDate = DateUtils.getDateTimeOrDefault(endDateString, null);
        
        PagedResourceList<AccountSummary> page = participantService.getPagedAccountSummaries(study, offsetBy, pageSize,
                emailFilter, startDate, endDate);
        return okResult(page);
    }
    
    public Result createParticipant(String verifyEmailString) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
        boolean verifyEmail = Boolean.parseBoolean(verifyEmailString);
        
        IdentifierHolder holder = participantService.createParticipant(study, session.getParticipant().getRoles(),
                participant, verifyEmail);
        return createdResult(holder);
    }
    
    public Result getParticipant(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        StudyParticipant participant = participantService.getParticipant(study, userId, true);

        ObjectWriter writer = (study.isHealthCodeExportEnabled()) ?
                StudyParticipant.API_WITH_HEALTH_CODE_WRITER :
                StudyParticipant.API_NO_HEALTH_CODE_WRITER;
        String ser = writer.writeValueAsString(participant);

        return ok(ser).as(BridgeConstants.JSON_MIME_TYPE);
    }
    
    public Result getRequestInfo(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // Verify it's in the same study as the researcher.
        RequestInfo requestInfo = cacheProvider.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = new RequestInfo.Builder().build();
        } else if (!study.getStudyIdentifier().equals(requestInfo.getStudyIdentifier())) {
            throw new EntityNotFoundException(StudyParticipant.class);
        }
        return okResult(requestInfo);
    }
    
    public Result updateParticipant(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
 
        participant = new StudyParticipant.Builder()
                .copyOf(participant)
                .withId(userId).build();
        participantService.updateParticipant(study, session.getParticipant().getRoles(), participant);

        return okResult("Participant updated.");
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result signOut(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.signUserOut(study, userId);

        return okResult("User signed out.");
    }

    public Result requestResetPassword(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.requestResetPassword(study, userId);
        
        return okResult("Request to reset password sent to user.");
    }
    
    public Result getActivityHistoryV2(String userId, String activityGuid, String scheduledOnStartString,
            String scheduledOnEndString, String offsetBy, String pageSizeString) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        DateTime scheduledOnStart = getDateTimeOrDefault(scheduledOnStartString, null);
        DateTime scheduledOnEnd = getDateTimeOrDefault(scheduledOnEndString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = participantService.getActivityHistory(
                study, userId, activityGuid, scheduledOnStart, scheduledOnEnd, offsetBy, pageSize);
        
        return ok(ScheduledActivity.RESEARCHER_SCHEDULED_ACTIVITY_WRITER.writeValueAsString(page));
    }
    
    public Result deleteActivities(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.deleteActivities(study, userId);
        
        return okResult("Scheduled activities deleted.");
    }
    
    public Result resendEmailVerification(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.resendEmailVerification(study, userId);
        
        return okResult("Email verification request has been resent to user.");
    }
    
    public Result resendConsentAgreement(String userId, String subpopulationGuid) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(subpopulationGuid);
        participantService.resendConsentAgreement(study, subpopGuid, userId);
        
        return okResult("Consent agreement resent to user.");
    }
    
    public Result withdrawFromAllConsents(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawAllConsents(study, userId, withdrawal, withdrewOn);
        
        return okResult("User has been withdrawn from the study.");
    }
    
    public Result withdrawConsent(String userId, String subpopulationGuid) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        long withdrewOn = DateTime.now().getMillis();
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(subpopulationGuid);
        
        participantService.withdrawConsent(study, userId, subpopGuid, withdrawal, withdrewOn);
        
        return okResult("User has been withdrawn from subpopulation '"+subpopulationGuid+"'.");
    }
    
    public Result getUploads(String userId, String startTimeString, String endTimeString) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        DateTime startTime = DateUtils.getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = DateUtils.getDateTimeOrDefault(endTimeString, null);

        PagedResourceList<? extends UploadView> uploads = participantService.getUploads(
                study, userId, startTime, endTime);

        return okResult(uploads);
    }
    
    public Result getNotificationRegistrations(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        List<NotificationRegistration> registrations = participantService.listRegistrations(study, userId);
        
        return okResult(registrations);
    }
    
    public Result sendNotification(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        NotificationMessage message = parseJson(request(), NotificationMessage.class);
        
        participantService.sendNotification(study, userId, message);
        
        return acceptedResult("Message has been sent to external notification service.");
    }
}
