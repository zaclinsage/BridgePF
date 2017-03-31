package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;

import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class ConsentServiceMockTest {

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("GUID");
    private static final long SIGNED_ON = 1446044925219L;
    private static final long CONSENT_CREATED_ON = 1446044814108L;
    
    private ConsentService consentService;

    @Mock
    private AccountDao accountDao;
    @Mock
    private ParticipantOptionsService optionsService;
    @Mock
    private SendMailService sendMailService;
    @Mock
    private StudyConsentService studyConsentService;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private SubpopulationService subpopService;
    @Mock
    private Subpopulation subpopulation;

    private Study study;
    private StudyParticipant participant;
    private ConsentSignature consentSignature;
    private Account account;
    
    @Before
    public void before() {
        consentService = new ConsentService();
        consentService.setAccountDao(accountDao);
        consentService.setOptionsService(optionsService);
        consentService.setSendMailService(sendMailService);
        consentService.setActivityEventService(activityEventService);
        consentService.setStudyConsentService(studyConsentService);
        consentService.setSubpopulationService(subpopService);
        
        study = TestUtils.getValidStudy(ConsentServiceMockTest.class);
        
        participant = new StudyParticipant.Builder()
                .withHealthCode("BBB")
                .withId("user-id")
                .withEmail("bbb@bbb.com").build();
        
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("1990-01-01")
                .withSignedOn(SIGNED_ON).build();
        
        account = spy(new SimpleAccount()); // mock(Account.class);
        when(accountDao.getAccount(any(Study.class), any(String.class))).thenReturn(account);
        
        StudyConsentView studyConsentView = mock(StudyConsentView.class);
        when(studyConsentView.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(studyConsentView);
        when(subpopService.getSubpopulation(study.getStudyIdentifier(), SUBPOP_GUID)).thenReturn(subpopulation);
    }
    
    @Test
    public void userCannotGetConsentForSubpopulationToWhichTheyAreNotMapped() {
        SubpopulationGuid badGuid = SubpopulationGuid.create("not-correct");
        
        when(subpopService.getSubpopulation(study, badGuid)).thenThrow(new EntityNotFoundException(Subpopulation.class));
        try {
            consentService.getConsentSignature(study, SubpopulationGuid.create("not-correct"), participant.getId());
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
            assertEquals("Subpopulation not found.", e.getMessage());
        }
    }
    
    @Test
    public void userCannotConsentToSubpopulationToWhichTheyAreNotMapped() {
        SubpopulationGuid badGuid = SubpopulationGuid.create("not-correct");
        
        doThrow(new EntityNotFoundException(Subpopulation.class)).when(subpopService).getSubpopulation(study.getStudyIdentifier(), badGuid);
        try {
            consentService.consentToResearch(study, badGuid, participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
            assertEquals("Subpopulation not found.", e.getMessage());
        }
    }
    
    @Test
    public void activityEventFiredOnConsent() {
        StudyConsent consent = mock(StudyConsent.class);
        doReturn(CONSENT_CREATED_ON).when(consent).getCreatedOn();
        
        StudyConsentView view = mock(StudyConsentView.class);
        when(view.getStudyConsent()).thenReturn(consent);
        when(view.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(view);
        
        ConsentSignature sigWithStudyCreatedOn = new ConsentSignature.Builder()
                .withConsentSignature(consentSignature)
                .withConsentCreatedOn(consent.getCreatedOn()).build();
        
        consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, false);
        
        verify(activityEventService).publishEnrollmentEvent(participant.getHealthCode(), sigWithStudyCreatedOn);
    }

    @Test
    public void noActivityEventIfTooYoung() {
        consentSignature = new ConsentSignature.Builder().withName("Test User").withBirthdate("2014-01-01")
                .withSignedOn(SIGNED_ON).build();
        study.setMinAgeOfConsent(30); // Test is good until 2044. So there.
        
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfAlreadyConsented() {
        account.getConsentSignatureHistory(SUBPOP_GUID)
                .add(new ConsentSignature.Builder().withConsentCreatedOn(CONSENT_CREATED_ON)
                        .withSignedOn(DateTime.now().getMillis()).withName("A Name").withBirthdate("1960-10-10")
                        .build());
        try {
            consentService.consentToResearch(study, SUBPOP_GUID, participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(EntityAlreadyExistsException e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void noActivityEventIfDaoFails() {
        try {
            consentService.consentToResearch(study, SubpopulationGuid.create("badGuid"), participant, consentSignature, SharingScope.NO_SHARING, false);
            fail("Exception expected.");
        } catch(Throwable e) {
            verifyNoMoreInteractions(activityEventService);
        }
    }
    
    @Test
    public void withdrawConsentWithParticipant() throws Exception {
        account.setEmail("bbb@bbb.com");
        
        Map<String,String> optionsMap = Maps.newHashMap();
        optionsMap.put(EXTERNAL_IDENTIFIER.name(), participant.getExternalId());
        
        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(study, participant.getId());
        doReturn(new ParticipantOptionsLookup(optionsMap)).when(optionsService).getOptions(participant.getHealthCode());
        
        List<ConsentSignature> history = account.getConsentSignatureHistory(SUBPOP_GUID);
        history.add(consentSignature);
        consentService.withdrawConsent(study, SUBPOP_GUID, participant, new Withdrawal("For reasons."), SIGNED_ON);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        ArgumentCaptor<MimeTypeEmailProvider> emailCaptor = ArgumentCaptor.forClass(MimeTypeEmailProvider.class);
        
        verify(accountDao).getAccount(study, participant.getId());
        verify(accountDao).updateAccount(captor.capture());
        // It happens twice because we do it the first time to set up the test properly
        //verify(account, times(2)).getConsentSignatures(setterCaptor.capture());
        verify(sendMailService).sendEmail(emailCaptor.capture());
        verifyNoMoreInteractions(accountDao);
        
        Account account = captor.getValue();
        // Signature is there but has been marked withdrawn
        assertNull(account.getActiveConsentSignature(SUBPOP_GUID));
        assertNotNull(account.getConsentSignatureHistory(SUBPOP_GUID).get(0).getWithdrewOn());
        assertEquals(1, account.getConsentSignatureHistory(SUBPOP_GUID).size());
        
        MimeTypeEmailProvider provider = emailCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        assertEquals("\"Test Study [ConsentServiceMockTest]\" <bridge-testing+support@sagebase.org>", email.getSenderAddress());
        assertEquals("bridge-testing+consent@sagebase.org", email.getRecipientAddresses().get(0));
        assertEquals("Notification of consent withdrawal for Test Study [ConsentServiceMockTest]", email.getSubject());
        assertEquals("<p>User   &lt;bbb@bbb.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>For reasons.</p>", 
                    email.getMessageParts().get(0).getContent());
    }
    
    @Test
    public void withdrawConsentWithAccount() throws Exception {
        Map<String,String> optionsMap = Maps.newHashMap();
        optionsMap.put(ParticipantOption.EXTERNAL_IDENTIFIER.name(), participant.getExternalId());
        
        doReturn(participant.getHealthCode()).when(account).getHealthCode();
        doReturn(account).when(accountDao).getAccount(study, participant.getId());
        doReturn(new ParticipantOptionsLookup(optionsMap)).when(optionsService).getOptions(participant.getHealthCode());
        
        List<ConsentSignature> history = account.getConsentSignatureHistory(SUBPOP_GUID);
        history.add(consentSignature);
        consentService.withdrawConsent(study, SUBPOP_GUID, account, new Withdrawal("For reasons."), SIGNED_ON);
        
        verify(accountDao, never()).getAccount(study, participant.getId());
        verify(accountDao).updateAccount(account);
        verify(sendMailService).sendEmail(any(MimeTypeEmailProvider.class));
        verifyNoMoreInteractions(accountDao);
        
        // Contents of call are tested in prior test where participant is used
    }
    
    @Test
    public void stormpathFailureConsistent() {
        when(accountDao.getAccount(any(), any())).thenThrow(new BridgeServiceException("Something bad happend", 500));
        
        try {
            consentService.withdrawConsent(study, SUBPOP_GUID, participant, new Withdrawal("For reasons."), DateTime.now().getMillis());
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
        }
        verifyNoMoreInteractions(sendMailService);
    }

}