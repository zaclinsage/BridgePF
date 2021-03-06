package org.sagebionetworks.bridge.play.controllers;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.CmsPublicKey;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.SynapseAccount;
import org.sagebionetworks.bridge.models.SynapseUserIdHolder;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.studies.SynapseProjectIdTeamIdHolder;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UploadService;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Result;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.Roles.*;

@Controller
public class StudyController extends BaseController {

    private final Comparator<Study> STUDY_COMPARATOR = new Comparator<Study>() {
        public int compare(Study study1, Study study2) {
            return study1.getName().compareToIgnoreCase(study2.getName());
        }
    };

    private final Set<String> studyWhitelist = Collections
            .unmodifiableSet(new HashSet<>(BridgeConfigFactory.getConfig().getPropertyAsList("study.whitelist")));

    private UploadCertificateService uploadCertificateService;
    
    private EmailVerificationService emailVerificationService;
    
    private UploadService uploadService;

    @Autowired
    final void setUploadCertificateService(UploadCertificateService uploadCertificateService) {
        this.uploadCertificateService = uploadCertificateService;
    }
    
    @Autowired
    final void setEmailVerificationService(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }
    
    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Deprecated
    public Result getStudyList() throws Exception {
        List<Study> studies = studyService.getStudies();

        return ok(Study.STUDY_LIST_WRITER.writeValueAsString(new ResourceList<Study>(studies)));
    }

    public Result getCurrentStudy() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        return ok(Study.STUDY_WRITER.writeValueAsString(study));
    }

    public Result updateStudyForDeveloper() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        Study studyUpdate = parseJson(request(), Study.class);
        studyUpdate.setIdentifier(studyId.getIdentifier());
        studyUpdate = studyService.updateStudy(studyUpdate, false);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result updateStudy(String identifier) throws Exception {
        getAuthenticatedSession(ADMIN);

        Study studyUpdate = parseJson(request(), Study.class);
        studyUpdate.setIdentifier(identifier);
        studyUpdate = studyService.updateStudy(studyUpdate, true);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result getStudy(String identifier) throws Exception {
        getAuthenticatedSession(ADMIN);

        Study study = studyService.getStudy(identifier);
        return ok(Study.STUDY_WRITER.writeValueAsString(study));
    }

    // You can get a truncated view of studies with either format=summary or summary=true;
    // the latter allows us to make this a boolean flag in the Java client libraries.
    public Result getAllStudies(String format, String summary) throws Exception {
        List<Study> studies = studyService.getStudies();
        if ("summary".equals(format) || "true".equals(summary)) {
            // then only return active study as summary
            List<Study> activeStudiesSummary = studies.stream()
                    .filter(s -> s.isActive()).collect(Collectors.toList());
            Collections.sort(activeStudiesSummary, STUDY_COMPARATOR);
            return ok(Study.STUDY_LIST_WRITER.writeValueAsString(new ResourceList<Study>(activeStudiesSummary)));
        }
        getAuthenticatedSession(ADMIN);

        // otherwise, return all studies including deactivated ones
        return ok(Study.STUDY_WRITER.writeValueAsString(new ResourceList<Study>(studies)));
    }

    public Result createStudy() throws Exception {
        getAuthenticatedSession(ADMIN);

        Study study = parseJson(request(), Study.class);
        study = studyService.createStudy(study);
        return okResult(new VersionHolder(study.getVersion()));
    }

    public Result createSynapseAccount() throws Exception{
        getAuthenticatedSession(DEVELOPER);

        SynapseAccount newAccount = parseJson(request(), SynapseAccount.class);
        String userId = studyService.createSynapseAccount(newAccount);

        return createdResult(new SynapseUserIdHolder(userId));
    }

    public Result createSynapse(String synapseUserId) throws Exception {
        // first get current study
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        // then create project and team and grant admin permission to current user and exporter
        studyService.createSynapseProjectTeam(synapseUserId, study);

        return createdResult(new SynapseProjectIdTeamIdHolder(study.getSynapseProjectId(), study.getSynapseDataAccessTeamId()));
    }

    // since only admin can delete study, no need to check if return results should contain deactivated ones
    public Result deleteStudy(String identifier, String physical) throws Exception {
        getAuthenticatedSession(ADMIN);
        if (studyWhitelist.contains(identifier)) {
            return forbidden(Json.toJson(identifier + " is protected by whitelist."));
        }

        studyService.deleteStudy(identifier, Boolean.valueOf(physical));

        return okResult("Study deleted.");
    }

    public Result getStudyPublicKeyAsPem() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String pem = uploadCertificateService.getPublicKeyAsPem(session.getStudyIdentifier());

        return okResult(new CmsPublicKey(pem));
    }
    
    public Result getEmailStatus() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        EmailVerificationStatus status = emailVerificationService.getEmailStatus(study.getSupportEmail());
        return okResult(new EmailVerificationStatusHolder(status));
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result verifyEmail() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        EmailVerificationStatus status = emailVerificationService.verifyEmailAddress(study.getSupportEmail());
        return okResult(new EmailVerificationStatusHolder(status));
    }
    
    public Result getUploads(String startTimeString, String endTimeString) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        DateTime startTime = DateUtils.getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = DateUtils.getDateTimeOrDefault(endTimeString, null);
        
        DateTimeRangeResourceList<? extends UploadView> uploads = uploadService.getStudyUploads(
                session.getStudyIdentifier(), startTime, endTime);

        return okResult(uploads);
    }

    /**
     * another version of getUploads for workers to specify any studyid to get uploads
     * @param startTimeString
     * @param endTimeString
     * @return
     */
    public Result getUploadsForStudy(String studyIdString, String startTimeString, String endTimeString) throws EntityNotFoundException {
        getAuthenticatedSession(WORKER);

        DateTime startTime = DateUtils.getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = DateUtils.getDateTimeOrDefault(endTimeString, null);

        StudyIdentifier studyId = new StudyIdentifierImpl(studyIdString);

        DateTimeRangeResourceList<? extends UploadView> uploads = uploadService.getStudyUploads(
                studyId, startTime, endTime);

        return okResult(uploads);
    }

}
