package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.sagebionetworks.bridge.Roles.CAN_BE_EDITED_BY;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;
import static org.sagebionetworks.bridge.dao.ParticipantOption.TIME_ZONE;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class ParticipantService {
    private static Logger LOG = LoggerFactory.getLogger(ParticipantService.class);

    private static final String PAGE_SIZE_ERROR = "pageSize must be from " + API_MINIMUM_PAGE_SIZE + "-"
            + API_MAXIMUM_PAGE_SIZE + " records";
    private static final String DATE_RANGE_ERROR = "startDate should be before endDate";

    private AccountDao accountDao;

    private ParticipantOptionsService optionsService;

    private SubpopulationService subpopService;

    private ConsentService consentService;

    private ExternalIdService externalIdService;

    private CacheProvider cacheProvider;

    private ScheduledActivityDao activityDao;

    private UploadService uploadService;

    private NotificationsService notificationsService;

    private ScheduledActivityService scheduledActivityService;

    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    @Autowired
    final void setUserConsent(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setScheduledActivityDao(ScheduledActivityDao activityDao) {
        this.activityDao = activityDao;
    }

    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @Autowired
    final void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }

    public StudyParticipant getParticipant(Study study, String id, boolean includeHistory) {
        Account account = getAccountThrowingException(study, id);
        return getParticipant(study, account, includeHistory);
    }

    public StudyParticipant getParticipant(Study study, Account account, boolean includeHistory) {
        if (account == null) {
            // This should never happen. However, it occasionally does happen, generally only during integration tests.
            // If a call is taking a long time for whatever reason, the call will timeout and the tests will delete the
            // account. If this happens in the middle of a call (such as give consent or update self participant),
            // we'll suddenly have no account here.
            //
            // We'll still want to log an error for this so we'll be aware when it happens. At the very least, we'll
            // have this comment and a marginally useful error message instead of a mysterious null pointer exception.
            //
            // See https://sagebionetworks.jira.com/browse/BRIDGE-1463 for more info.
            LOG.error("getParticipant() called with no account. Was the account deleted in the middle of the call?");
            throw new EntityNotFoundException(Account.class);
        }

        StudyParticipant.Builder builder = new StudyParticipant.Builder();

        ParticipantOptionsLookup lookup = optionsService.getOptions(account.getHealthCode());
        builder.withSharingScope(lookup.getEnum(SHARING_SCOPE, SharingScope.class));
        builder.withNotifyByEmail(lookup.getBoolean(EMAIL_NOTIFICATIONS));
        builder.withExternalId(lookup.getString(EXTERNAL_IDENTIFIER));
        builder.withDataGroups(lookup.getStringSet(DATA_GROUPS));
        builder.withLanguages(lookup.getOrderedStringSet(LANGUAGES));
        builder.withTimeZone(lookup.getTimeZone(TIME_ZONE));
        builder.withFirstName(account.getFirstName());
        builder.withLastName(account.getLastName());
        builder.withEmail(account.getEmail());
        builder.withStatus(account.getStatus());
        builder.withCreatedOn(account.getCreatedOn());
        builder.withRoles(account.getRoles());
        builder.withId(account.getId());
        builder.withHealthCode(account.getHealthCode());

        Map<String, String> attributes = Maps.newHashMap();
        for (String attribute : study.getUserProfileAttributes()) {
            String value = account.getAttribute(attribute);
            attributes.put(attribute, value);
        }
        builder.withAttributes(attributes);

        if (includeHistory) {
            Map<String,List<UserConsentHistory>> consentHistories = Maps.newHashMap();
            List<Subpopulation> subpopulations = subpopService.getSubpopulations(study.getStudyIdentifier());
            for (Subpopulation subpop : subpopulations) {
                // always returns a list, even if empty
                List<UserConsentHistory> history = getUserConsentHistory(account, subpop.getGuid());
                consentHistories.put(subpop.getGuidString(), history);
            }
            builder.withConsentHistories(consentHistories);
        }
        return builder.build();
    }

    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize,
            String emailFilter, DateTime startDate, DateTime endDate) {
        checkNotNull(study);
        if (offsetBy < 0) {
            throw new BadRequestException("offsetBy cannot be less than 0");
        }
        // Just set a sane upper limit on this.
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        if (startDate != null && endDate != null && startDate.getMillis() >= endDate.getMillis()) {
            throw new BadRequestException(DATE_RANGE_ERROR);
        }
        return accountDao.getPagedAccountSummaries(study, offsetBy, pageSize, emailFilter, startDate, endDate);
    }

    public void signUserOut(Study study, String email) {
        checkNotNull(study);
        checkArgument(isNotBlank(email));

        Account account = getAccountThrowingException(study, email);
        cacheProvider.removeSessionByUserId(account.getId());
    }

    /**
     * Create a study participant. A password must be provided, even if it is added on behalf of a user before
     * triggering a reset password request.
     */
    public IdentifierHolder createParticipant(Study study, Set<Roles> callerRoles, StudyParticipant participant,
            boolean sendVerifyEmail) {
        checkNotNull(study);
        checkNotNull(callerRoles);
        checkNotNull(participant);

        Validate.entityThrowingException(new StudyParticipantValidator(study, true), participant);
        
        Account account = accountDao.constructAccount(study, participant.getEmail(), participant.getPassword());
        Map<ParticipantOption, String> options = Maps.newHashMap();
        
        externalIdService.reserveExternalId(study, participant.getExternalId(), account.getHealthCode());

        updateAccountOptionsAndRoles(study, callerRoles, options, account, participant);
        
        accountDao.createAccount(study, account, sendVerifyEmail && study.isEmailVerificationEnabled());
        externalIdService.assignExternalId(study, participant.getExternalId(), account.getHealthCode());
        optionsService.setAllOptions(study.getStudyIdentifier(), account.getHealthCode(), options);
        return new IdentifierHolder(account.getId());
    }

    public void updateParticipant(Study study, Set<Roles> callerRoles, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(callerRoles);
        checkNotNull(participant);
        
        Validate.entityThrowingException(new StudyParticipantValidator(study, false), participant);
        
        Account account = getAccountThrowingException(study, participant.getId());
        Map<ParticipantOption, String> options = Maps.newHashMap();

        // Do this first because if the ID has been taken or is invalid, we do not want to update anything else.
        externalIdService.assignExternalId(study, participant.getExternalId(), account.getHealthCode());

        updateAccountOptionsAndRoles(study, callerRoles, options, account, participant);
        
        // Only admin roles can change status, after participant is created
        if (callerIsAdmin(callerRoles) && participant.getStatus() != null) {
            account.setStatus(participant.getStatus());
        }
        accountDao.updateAccount(account);
        optionsService.setAllOptions(study.getStudyIdentifier(), account.getHealthCode(), options);
    }

    private void updateAccountOptionsAndRoles(Study study, Set<Roles> callerRoles,
            Map<ParticipantOption, String> options, Account account, StudyParticipant participant) {
        for (ParticipantOption option : ParticipantOption.values()) {
            options.put(option, option.fromParticipant(participant));
        }
        // External identifier is handled by the ExternalIdService
        options.remove(EXTERNAL_IDENTIFIER);
        options.remove(TIME_ZONE);

        account.setFirstName(participant.getFirstName());
        account.setLastName(participant.getLastName());
        for (String attribute : study.getUserProfileAttributes()) {
            String value = participant.getAttributes().get(attribute);
            account.setAttribute(attribute, value);
        }
        
        if (callerIsAdmin(callerRoles)) {
            updateRoles(callerRoles, participant, account);
        }
    }

    public void requestResetPassword(Study study, String userId) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));

        Account account = getAccountThrowingException(study, userId);

        Email email = new Email(study.getIdentifier(), account.getEmail());
        accountDao.requestResetPassword(study, email);
    }

    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistory(Study study, String userId,
            String activityGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetBy, int pageSize) {
        checkNotNull(study);
        checkArgument(isNotBlank(activityGuid));
        checkArgument(isNotBlank(userId));

        Account account = getAccountThrowingException(study, userId);

        return scheduledActivityService.getActivityHistory(account.getHealthCode(), activityGuid, scheduledOnStart,
                scheduledOnEnd, offsetBy, pageSize);
    }

    public void deleteActivities(Study study, String userId) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));

        Account account = getAccountThrowingException(study, userId);

        activityDao.deleteActivitiesForUser(account.getHealthCode());
    }

    public void resendEmailVerification(Study study, String userId) {
        checkNotNull(study);
        checkArgument(isNotBlank(userId));

        StudyParticipant participant = getParticipant(study, userId, false);
        Email email = new Email(study.getIdentifier(), participant.getEmail());
        accountDao.resendEmailVerificationToken(study.getStudyIdentifier(), email);
    }

    public void withdrawAllConsents(Study study, String userId, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        Account account = getAccountThrowingException(study, userId);

        consentService.withdrawAllConsents(study, account, withdrawal, withdrewOn);
    }

    public void withdrawConsent(Study study, String userId, SubpopulationGuid subpopGuid, Withdrawal withdrawal,
            long withdrewOn) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(subpopGuid);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        Account account = getAccountThrowingException(study, userId);

        consentService.withdrawConsent(study, subpopGuid, account, withdrawal, withdrewOn);
    }
    
    public void resendConsentAgreement(Study study, SubpopulationGuid subpopGuid, String userId) {
        checkNotNull(study);
        checkNotNull(subpopGuid);
        checkArgument(isNotBlank(userId));

        StudyParticipant participant = getParticipant(study, userId, false);
        consentService.emailConsentAgreement(study, subpopGuid, participant);
    }

    /**
     * Get a history of all consent records for a given subpopulation, whether user is withdrawn or not.
     * 
     * @param account
     * @param subpopGuid
     */
    public List<UserConsentHistory> getUserConsentHistory(Account account, SubpopulationGuid subpopGuid) {
        return account.getConsentSignatureHistory(subpopGuid).stream().map(signature -> {
            Subpopulation subpop = subpopService.getSubpopulation(account.getStudyIdentifier(), subpopGuid);
            boolean hasSignedActiveConsent = (signature.getConsentCreatedOn() == subpop.getPublishedConsentCreatedOn());

            return new UserConsentHistory.Builder()
                .withName(signature.getName())
                .withSubpopulationGuid(subpopGuid)
                .withBirthdate(signature.getBirthdate())
                .withImageData(signature.getImageData())
                .withImageMimeType(signature.getImageMimeType())
                .withSignedOn(signature.getSignedOn())
                .withHealthCode(account.getHealthCode())
                .withWithdrewOn(signature.getWithdrewOn())
                .withConsentCreatedOn(signature.getConsentCreatedOn())
                .withHasSignedActiveConsent(hasSignedActiveConsent).build();
        }).collect(BridgeCollectors.toImmutableList());
    }

    public PagedResourceList<? extends UploadView> getUploads(Study study, String userId, DateTime startTime,
            DateTime endTime) {
        checkNotNull(study);
        checkNotNull(userId);

        Account account = getAccountThrowingException(study, userId);

        return uploadService.getUploads(account.getHealthCode(), startTime, endTime);
    }

    public List<NotificationRegistration> listRegistrations(Study study, String userId) {
        checkNotNull(study);
        checkNotNull(userId);

        Account account = getAccountThrowingException(study, userId);

        return notificationsService.listRegistrations(account.getHealthCode());
    }

    public void sendNotification(Study study, String userId, NotificationMessage message) {
        checkNotNull(study);
        checkNotNull(userId);
        checkNotNull(message);

        Account account = getAccountThrowingException(study, userId);

        notificationsService.sendNotificationToUser(study.getStudyIdentifier(), account.getHealthCode(), message);
    }

    private boolean callerIsAdmin(Set<Roles> callerRoles) {
        return !Collections.disjoint(callerRoles, ADMINISTRATIVE_ROLES);
    }

    private boolean callerCanEditRole(Set<Roles> callerRoles, Roles targetRole) {
        return !Collections.disjoint(callerRoles, CAN_BE_EDITED_BY.get(targetRole));
    }

    /**
     * For each role added, the caller must have the right to add the role. Then for every role currently assigned, we
     * check and if the caller doesn't have the right to remove that role, we'll add it back. Then we save those
     * results.
     */
    private void updateRoles(Set<Roles> callerRoles, StudyParticipant participant, Account account) {
        Set<Roles> newRoleSet = Sets.newHashSet();
        // Caller can only add roles they have the rights to edit
        for (Roles role : participant.getRoles()) {
            if (callerCanEditRole(callerRoles, role)) {
                newRoleSet.add(role);
            }
        }
        // Callers also can't remove roles they don't have the rights to edit
        for (Roles role : account.getRoles()) {
            if (!callerCanEditRole(callerRoles, role)) {
                newRoleSet.add(role);
            }
        }
        account.setRoles(newRoleSet);
    }

    private Account getAccountThrowingException(Study study, String id) {
        Account account = accountDao.getAccount(study, id);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }

}
