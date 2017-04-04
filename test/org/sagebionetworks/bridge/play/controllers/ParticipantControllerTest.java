package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantControllerTest {

    private static final String SUBPOP_GUID = "subpopGuid";

    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> FORWARD_CURSOR_PAGED_ACTIVITIES_REF = new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {
    };
    
    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>(){};
    
    private static final TypeReference<PagedResourceList<? extends Upload>> UPLOADS_REF = new TypeReference<PagedResourceList<? extends Upload>>(){};
    
    private static final Set<Roles> CALLER_ROLES = Sets.newHashSet(Roles.RESEARCHER);
    
    private static final String ID = "ASDF";

    private static final String EMAIL = "email@email.com";
    
    private static final String ACTIVITY_GUID = "activityGuid";

    private static final DateTime ENDS_ON = DateTime.now();
    
    private static final DateTime STARTS_ON = ENDS_ON.minusWeeks(1);
    
    private static final AccountSummary SUMMARY = new AccountSummary("firstName", "lastName", "email", "id",
            DateTime.now(), AccountStatus.ENABLED, TestConstants.TEST_STUDY);
    
    @Spy
    private ParticipantController controller;
    
    @Mock
    private ConsentService mockConsentService;
    
    @Mock
    private ParticipantService mockParticipantService;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private AuthenticationService authService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Captor
    private ArgumentCaptor<Map<ParticipantOption,String>> optionMapCaptor;
    
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> startTimeCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> endTimeCaptor;
    
    @Captor
    private ArgumentCaptor<NotificationMessage> messageCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> startsOnCaptor;
    
    @Captor
    private ArgumentCaptor<DateTime> endsOnCaptor;
    
    private UserSession session;
    
    private Study study;
    
    @Before
    public void before() throws Exception {
        study = new DynamoStudy();
        study.setUserProfileAttributes(Sets.newHashSet("foo","baz"));
        study.setIdentifier("test-study");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withRoles(CALLER_ROLES)
                .withId(ID).build();
        
        session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);

        doReturn(session).when(controller).getSessionIfItExists();
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        
        List<AccountSummary> summaries = Lists.newArrayListWithCapacity(3);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(summaries, 10, 20, 30).withFilter("emailFilter", "foo");
        
        when(authService.getSession(eq(study), any())).thenReturn(session);
        
        when(mockParticipantService.getPagedAccountSummaries(eq(study), anyInt(), anyInt(), any(), any(), any())).thenReturn(page);
        
        controller.setParticipantService(mockParticipantService);
        controller.setStudyService(mockStudyService);
        controller.setAuthenticationService(authService);
        controller.setCacheProvider(cacheProvider);
        
        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(cacheProvider);
        sessionUpdateService.setConsentService(mockConsentService);
        
        controller.setSessionUpdateService(sessionUpdateService);
        
        mockPlayContext();
    }
    
    @Test
    public void getParticipants() throws Exception {
        DateTime start = DateTime.now();
        DateTime end = DateTime.now();
        Result result = controller.getParticipants("10", "20", "foo", start.toString(), end.toString());
        PagedResourceList<AccountSummary> page = resultToPage(result);
        
        // verify the result contains items
        assertEquals(3, page.getItems().size());
        assertEquals(30, page.getTotal());
        assertEquals(SUMMARY, page.getItems().get(0));
        
        //verify paging/filtering
        assertEquals(new Integer(10), page.getOffsetBy());
        assertEquals(20, page.getPageSize());
        assertEquals("foo", page.getFilters().get("emailFilter"));
        
        // DateTime instances don't seem to be equal unless you use the library's equality methods, which
        // verification does not do. So capture and compare that way.
        verify(mockParticipantService).getPagedAccountSummaries(eq(study), eq(10), eq(20), eq("foo"),
                startTimeCaptor.capture(), endTimeCaptor.capture());
        assertEquals(start.toString(), startTimeCaptor.getValue().toString());
        assertEquals(end.toString(), endTimeCaptor.getValue().toString());
    }
    
    @Test(expected = BadRequestException.class)
    public void oddParametersUseDefaults() throws Exception {
        controller.getParticipants("asdf", "qwer", null, null, null);
        
        // paging with defaults
        verify(mockParticipantService).getPagedAccountSummaries(study, 0, API_DEFAULT_PAGE_SIZE, null, null, null);
    }

    @Test
    public void getParticipant() throws Exception {
        study.setHealthCodeExportEnabled(true);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test")
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE).build();
        
        when(mockParticipantService.getParticipant(study, ID, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID);
        assertEquals(result.contentType(), "application/json");
        
        // StudyParticipant will encrypt the healthCode when you ask for it, so validate the
        // JSON itself.
        JsonNode node = TestUtils.getJson(result);
        assertTrue(node.has("firstName"));
        assertTrue(node.has("healthCode"));
        assertFalse(node.has("encryptedHealthCode"));
    }
    
    @Test
    public void getParticipantWithNoHealthCode() throws Exception {
        study.setHealthCodeExportEnabled(false);
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withFirstName("Test").withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(study, ID, true)).thenReturn(studyParticipant);
        
        Result result = controller.getParticipant(ID);
        String json = Helpers.contentAsString(result);
        StudyParticipant retrievedParticipant = MAPPER.readValue(json, StudyParticipant.class);
        
        assertEquals("Test", retrievedParticipant.getFirstName());
        assertNull(retrievedParticipant.getHealthCode());
    }
    
    @Test
    public void signUserOut() throws Exception {
        controller.signOut(ID);
        
        verify(mockParticipantService).signUserOut(study, ID);
    }

    @Test
    public void updateParticipant() throws Exception {
        study.getUserProfileAttributes().add("phone");
        mockPlayContextWithJson(createJson("{'firstName':'firstName',"+
                "'lastName':'lastName',"+
                "'email':'email@email.com',"+
                "'externalId':'externalId',"+
                "'password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,"+
                "'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},"+
                "'languages':['en','fr']}"));
        
        Result result = controller.updateParticipant(ID);
        assertResult(result, 200, "Participant updated.");
        
        verify(mockParticipantService).updateParticipant(eq(study), eq(CALLER_ROLES), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(ID, participant.getId());
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }
    
    @Test
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null, null, null, null);

        // paging with defaults
        verify(mockParticipantService).getPagedAccountSummaries(study, 0, API_DEFAULT_PAGE_SIZE, null, null, null);
    }
    
    @Test
    public void createParticipant() throws Exception {
        IdentifierHolder holder = setUpCreateParticipant();
        doReturn(holder).when(mockParticipantService).createParticipant(eq(study), any(), any(StudyParticipant.class), eq(true));
        
        Result result = controller.createParticipant("true");

        assertEquals(201, result.status());
        String id = MAPPER.readTree(Helpers.contentAsString(result)).get("identifier").asText();
        assertEquals(holder.getIdentifier(), id);
        
        verify(mockParticipantService).createParticipant(eq(study), eq(CALLER_ROLES), participantCaptor.capture(), eq(true));
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertEquals(EMAIL, participant.getEmail());
        assertEquals("newUserPassword", participant.getPassword());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.SPONSORS_AND_PARTNERS, participant.getSharingScope());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group2","group1"), participant.getDataGroups());
        assertEquals("123456789", participant.getAttributes().get("phone"));
        assertEquals(Sets.newHashSet("en","fr"), participant.getLanguages());
    }
    
    @Test
    public void createParticipantWithoutEmailVerification() throws Exception {
        IdentifierHolder holder = setUpCreateParticipant();
        doReturn(holder).when(mockParticipantService).createParticipant(eq(study), any(), any(StudyParticipant.class), eq(false));
        
        Result result = controller.createParticipant("false");
        
        String id = MAPPER.readTree(Helpers.contentAsString(result)).get("identifier").asText();
        assertEquals(holder.getIdentifier(), id);
        
        verify(mockParticipantService).createParticipant(eq(study), eq(CALLER_ROLES), participantCaptor.capture(), eq(false));
    }

    @Test
    public void getParticipantRequestInfo() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withStudyIdentifier(new StudyIdentifierImpl("test-study")).build();
        
        doReturn(requestInfo).when(cacheProvider).getRequestInfo("userId");
        Result result = controller.getRequestInfo("userId");
        
        // serialization was tested separately... just validate the object is there
        RequestInfo info = MAPPER.readValue(Helpers.contentAsString(result), RequestInfo.class);
        assertEquals(requestInfo, info);
    }
    
    @Test
    public void getParticipantRequestInfoIsNullsafe() throws Exception {
        // There is no request info.
        Result result = controller.getRequestInfo("userId");
        
        assertEquals(200, result.status());
        RequestInfo info = MAPPER.readValue(Helpers.contentAsString(result), RequestInfo.class);
        assertNotNull(info); // values are all null, but object is returned
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getParticipantRequestInfoOnlyReturnsCurrentStudyInfo() throws Exception {
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withUserAgent("app/20")
                .withTimeZone(DateTimeZone.forOffsetHours(-7))
                .withStudyIdentifier(new StudyIdentifierImpl("some-other-study")).build();
        
        doReturn(requestInfo).when(cacheProvider).getRequestInfo("userId");
        controller.getRequestInfo("userId");
    }
    
    private IdentifierHolder setUpCreateParticipant() throws Exception {
        IdentifierHolder holder = new IdentifierHolder("ABCD");
        
        study.getUserProfileAttributes().add("phone");
        mockPlayContextWithJson(createJson("{'firstName':'firstName',"+
                "'lastName':'lastName',"+
                "'email':'email@email.com',"+
                "'externalId':'externalId',"+
                "'password':'newUserPassword',"+
                "'sharingScope':'sponsors_and_partners',"+
                "'notifyByEmail':true,"+
                "'dataGroups':['group2','group1'],"+
                "'attributes':{'phone':'123456789'},"+
                "'languages':['en','fr']}"));
        return holder;
    }

    @Test
    public void updateParticipantWithMismatchedIdsUsesURL() throws Exception {
        mockPlayContextWithJson(createJson("{'id':'id2'}"));
        
        controller.updateParticipant("id1");
        
        verify(mockParticipantService).updateParticipant(eq(study), eq(CALLER_ROLES), participantCaptor.capture());
        
        StudyParticipant persisted = participantCaptor.getValue();
        assertEquals("id1", persisted.getId());
    }
    
    @Test
    public void getSelfParticipant() throws Exception {
        StudyParticipant studyParticipant = new StudyParticipant.Builder()
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE)
                .withFirstName("Test").build();
        
        when(mockParticipantService.getParticipant(study, ID, false)).thenReturn(studyParticipant);

        Result result = controller.getSelfParticipant();
        assertEquals("application/json", result.contentType());
        
        verify(mockParticipantService).getParticipant(study, ID, false);
        
        StudyParticipant deserParticipant = MAPPER.readValue(Helpers.contentAsString(result), StudyParticipant.class);

        assertEquals("Test", deserParticipant.getFirstName());
        assertNull(deserParticipant.getHealthCode());
        assertNull(deserParticipant.getEncryptedHealthCode());
    }
    
    @Test
    public void updateSelfParticipant() throws Exception {
        // All values should be copied over here, also add a healthCode to verify it is not unset.
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(TestUtils.getStudyParticipant(ParticipantControllerTest.class))
                .withHealthCode("healthCode").build();
        
        doReturn(participant).when(mockParticipantService).getParticipant(study, ID, false);
        doReturn(new UserSession(participant)).when(authService).getSession(eq(study), any());
        
        String json = MAPPER.writeValueAsString(participant);
        mockPlayContextWithJson(json);

        Result result = controller.updateSelfParticipant();
        
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("UserSessionInfo", node.get("type").asText());
        assertNull(node.get("healthCode"));
        
        // verify the object is passed to service, one field is sufficient
        verify(cacheProvider).setUserSession(any());
        verify(mockParticipantService).updateParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture());

        // Just test the different types and verify they are there.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
        assertEquals("FirstName", captured.getFirstName());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, captured.getSharingScope());
        assertTrue(captured.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1"), captured.getDataGroups());
        assertEquals("123-456-7890", captured.getAttributes().get("phone"));
    }
    
    // Some values will be missing in the JSON and should be preserved from this original participant object.
    // This allows client to provide JSON that's less than the entire participant.
    @Test
    public void partialUpdateSelfParticipant() throws Exception {
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("foo", "bar");
        attrs.put("baz", "bap");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withLastName("lastName")
                .withEmail("email@email.com")
                .withId("id")
                .withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withNotifyByEmail(true)
                .withDataGroups(Sets.newHashSet("group1","group2"))
                .withAttributes(attrs)
                .withLanguages(TestUtils.newLinkedHashSet("en"))
                .withStatus(AccountStatus.DISABLED)
                .withExternalId("POWERS").build();
        doReturn(participant).when(mockParticipantService).getParticipant(study, ID, false);
        
        mockPlayContextWithJson(createJson("{'externalId':'simpleStringChange',"+
                "'sharingScope':'no_sharing',"+
                "'notifyByEmail':false,"+
                "'attributes':{'baz':'belgium'},"+
                "'languages':['fr'],"+
                "'status':'enabled',"+
                "'roles':['admin']}"));
        
        Result result = controller.updateSelfParticipant();
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("UserSessionInfo", node.get("type").asText());

        verify(mockParticipantService).updateParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
        assertEquals("firstName", captured.getFirstName());
        assertEquals("lastName", captured.getLastName());
        assertEquals("email@email.com", captured.getEmail());
        assertEquals("password", captured.getPassword());
        assertEquals(SharingScope.NO_SHARING, captured.getSharingScope());
        assertFalse(captured.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), captured.getDataGroups());
        assertNull(captured.getAttributes().get("foo"));
        assertEquals("belgium", captured.getAttributes().get("baz"));
        assertEquals(AccountStatus.ENABLED, captured.getStatus());
        assertEquals(Sets.newHashSet("fr"), captured.getLanguages());
        assertEquals("simpleStringChange", captured.getExternalId());
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        Result result = controller.requestResetPassword(ID);
        assertResult(result, 200, "Request to reset password sent to user.");
        
        verify(mockParticipantService).requestResetPassword(study, ID);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void cannotResetPasswordIfNotResearcher() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        session.setParticipant(participant);
        
        controller.requestResetPassword(ID);
    }
    
    @Test
    public void updateSelfCallCannotChangeIdToSomeoneElse() throws Exception {
        // All values should be copied over here.
        StudyParticipant participant = TestUtils.getStudyParticipant(ParticipantControllerTest.class);
        participant = new StudyParticipant.Builder().copyOf(participant).withId(ID).build();
        doReturn(participant).when(mockParticipantService).getParticipant(study, ID, false);
        
        // Now change to some other ID
        participant = new StudyParticipant.Builder().copyOf(participant).withId("someOtherId").build();
        String json = MAPPER.writeValueAsString(participant);
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.updateSelfParticipant();
        JsonNode node = TestUtils.getJson(result);
        assertEquals(200, result.status());
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        // verify the object is passed to service, one field is sufficient
        verify(mockParticipantService).updateParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture());

        // The ID was changed back to the session's participant user ID, not the one provided.
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(ID, captured.getId());
    }
    
    @Test
    public void canGetActivityHistoryV2() throws Exception {
        doReturn(createActivityResultsV2()).when(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ACTIVITY_GUID),
                any(), any(), eq("200"), eq(77));
        
        Result result = controller.getActivityHistoryV2(ID, ACTIVITY_GUID, STARTS_ON.toString(), ENDS_ON.toString(),
                "200", "77");
        assertEquals(200, result.status());
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(Helpers.contentAsString(result),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals("schedulePlanGuid", activity.getSchedulePlanGuid());
        assertNull(activity.getHealthCode());
        
        assertEquals(1, page.getItems().size()); // have not mocked out these items, but the list is there.
        assertEquals(1, page.getPageSize());
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("200"), eq(77));
        assertTrue(STARTS_ON.isEqual(startsOnCaptor.getValue()));
        assertTrue(ENDS_ON.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void canGetActivityV2WithNullValues() throws Exception {
        doReturn(createActivityResultsV2()).when(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ACTIVITY_GUID),
                any(), any(), eq(null), eq(API_DEFAULT_PAGE_SIZE));
        
        Result result = controller.getActivityHistoryV2(ID, ACTIVITY_GUID, null, null, null, null);
        assertEquals(200, result.status());
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(Helpers.contentAsString(result),
                FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        
        ScheduledActivity activity = page.getItems().iterator().next();
        assertEquals("schedulePlanGuid", activity.getSchedulePlanGuid());
        assertNull(activity.getHealthCode());
        
        assertEquals(1, page.getItems().size()); // have not mocked out these items, but the list is there.
        assertEquals(1, page.getPageSize());
        
        verify(mockParticipantService).getActivityHistory(eq(study), eq(ID), eq(ACTIVITY_GUID), eq(null), eq(null),
                eq(null), eq(API_DEFAULT_PAGE_SIZE));
    }

    @Test
    public void deleteActivities() throws Exception {
        Result result = controller.deleteActivities(ID);
        assertResult(result, 200, "Scheduled activities deleted.");
        
        verify(mockParticipantService).deleteActivities(study, ID);
    }

    @Test
    public void resendEmailVerification() throws Exception {
        controller.resendEmailVerification(ID);
        
        verify(mockParticipantService).resendEmailVerification(study, ID);
    }
    
    @Test
    public void resendConsentAgreement() throws Exception {
        controller.resendConsentAgreement(ID, SUBPOP_GUID);
        
        verify(mockParticipantService).resendConsentAgreement(study, SubpopulationGuid.create(SUBPOP_GUID), ID);
    }

    @Test
    public void withdrawFromAllConsents() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            TestUtils.mockPlayContextWithJson(json);
            
            controller.withdrawFromAllConsents(ID);
            
            verify(mockParticipantService).withdrawAllConsents(study, ID, new Withdrawal("Because, reasons."), 20000);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void withdrawConsent() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(20000);
        try {
            String json = "{\"reason\":\"Because, reasons.\"}";
            TestUtils.mockPlayContextWithJson(json);
            
            controller.withdrawConsent(ID, SUBPOP_GUID);
            
            verify(mockParticipantService).withdrawConsent(study, ID, SubpopulationGuid.create(SUBPOP_GUID),
                    new Withdrawal("Because, reasons."), 20000);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void getUploads() throws Exception {
        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z").withZone(DateTimeZone.UTC);
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z").withZone(DateTimeZone.UTC);

        List<? extends Upload> list = Lists.newArrayList();

        PagedResourceList<? extends Upload> uploads = new PagedResourceList<>(list, null, API_MAXIMUM_PAGE_SIZE, 0)
                .withFilter("startTime", startTime)
                .withFilter("endTime", endTime);
        doReturn(uploads).when(mockParticipantService).getUploads(study, ID, startTime, endTime);
        
        Result result = controller.getUploads(ID, startTime.toString(), endTime.toString());
        assertEquals(200, result.status());
        
        verify(mockParticipantService).getUploads(study, ID, startTime, endTime);
        
        // in other words, it's the object we mocked out from the service, we were returned the value.
        PagedResourceList<? extends Upload> retrieved = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), UPLOADS_REF);
        assertEquals(startTime.toString(), retrieved.getFilters().get("startTime"));
        assertEquals(endTime.toString(), retrieved.getFilters().get("endTime"));
    }
    
    @Test
    public void getUploadsNullsDateRange() throws Exception {
        List<? extends Upload> list = Lists.newArrayList();

        PagedResourceList<? extends Upload> uploads = new PagedResourceList<>(list,
                null, API_MAXIMUM_PAGE_SIZE, 0);
        doReturn(uploads).when(mockParticipantService).getUploads(study, ID, null, null);
        
        Result result = controller.getUploads(ID, null, null);
        assertEquals(200, result.status());
        
        verify(mockParticipantService).getUploads(study, ID, null, null);
    }
    
    @Test
    public void getNotificationRegistrations() throws Exception {
        List<NotificationRegistration> list = Lists.newArrayList();
        doReturn(list).when(mockParticipantService).listRegistrations(study, ID);
        
        Result result = controller.getNotificationRegistrations(ID);
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals(0, node.get("total").asInt());
        assertEquals("ResourceList", node.get("type").asText());
        
        verify(mockParticipantService).listRegistrations(study, ID);
    }
    
    @Test
    public void sendMessage() throws Exception {
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        TestUtils.mockPlayContextWithJson(message);
        Result result = controller.sendNotification(ID);
        
        TestUtils.assertResult(result, 202, "Message has been sent to external notification service.");
        
        verify(mockParticipantService).sendNotification(eq(study), eq(ID), messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        
        assertEquals("a subject", captured.getSubject());
        assertEquals("a message", captured.getMessage());
    }
    
    private ForwardCursorPagedResourceList<ScheduledActivity> createActivityResultsV2() {
        List<ScheduledActivity> list = Lists.newArrayList();
        
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        activity.setActivity(TestUtils.getActivity1());
        activity.setHealthCode("healthCode");
        activity.setSchedulePlanGuid("schedulePlanGuid");
        list.add(activity);
        
        return new ForwardCursorPagedResourceList<>(list, null, list.size());
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return MAPPER.readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
