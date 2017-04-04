package org.sagebionetworks.bridge;

import java.util.Set;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Sets;

public class TestConstants {
    
    public static final String ENCRYPTED_HEALTH_CODE = "TFMkaVFKPD48WissX0bgcD3esBMEshxb3MVgKxHnkXLSEPN4FQMKc01tDbBAVcXx94kMX6ckXVYUZ8wx4iICl08uE+oQr9gorE1hlgAyLAM=";
    public static final String UNENCRYPTED_HEALTH_CODE = "5a2192ee-f55d-4d01-a385-2d19f15a0880";
    
    public static final String DUMMY_IMAGE_DATA = "VGhpcyBpc24ndCBhIHJlYWwgaW1hZ2Uu";

    public static final String TEST_STUDY_IDENTIFIER = "api";
    public static final StudyIdentifier TEST_STUDY = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);
    public static final CriteriaContext TEST_CONTEXT = new CriteriaContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY).build();

    public static final int TIMEOUT = 10000;
    public static final String TEST_BASE_URL = "http://localhost:3333";
    public static final String API_URL = "/v3";
    public static final String SIGN_OUT_URL = API_URL + "/auth/signOut";
    public static final String SIGN_IN_URL = API_URL + "/auth/signIn";
    public static final String SCHEDULES_API = API_URL + "/schedules";
    public static final String SCHEDULED_ACTIVITIES_API = API_URL + "/activities";
    public static final String STUDIES_URL = API_URL + "/studies/";

    public static final String APPLICATION_JSON = "application/json";
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";
    public static final String SESSION_TOKEN = "sessionToken";

    public static final String ATTACHMENT_BUCKET = BridgeConfigFactory.getConfig().getProperty("attachment.bucket");
    public static final String UPLOAD_BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");
    
    public static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    /**
     * During tests, must sometimes pause because the underlying query uses a DynamoDB global 
     * secondary index, and this does not currently support consistent reads.
     */
    public static final int GSI_WAIT_DURATION = 2000;

    public static final ConsentStatus REQUIRED_SIGNED_CURRENT = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo1")).withRequired(true).withConsented(true)
            .withSignedMostRecentConsent(true).build();
    public static final ConsentStatus REQUIRED_SIGNED_OBSOLETE = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo2")).withRequired(true).withConsented(true)
            .withSignedMostRecentConsent(false).build();
    public static final ConsentStatus OPTIONAL_SIGNED_CURRENT = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo3")).withRequired(false).withConsented(true)
            .withSignedMostRecentConsent(true).build();
    public static final ConsentStatus OPTIONAL_SIGNED_OBSOLETE = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo4")).withRequired(false).withConsented(true)
            .withSignedMostRecentConsent(false).build();
    public static final ConsentStatus REQUIRED_UNSIGNED = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo5")).withRequired(true).withConsented(false)
            .withSignedMostRecentConsent(false).build();
    public static final ConsentStatus OPTIONAL_UNSIGNED = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo6")).withRequired(false).withConsented(false)
            .withSignedMostRecentConsent(false).build();
    
    public static final Set<String> USER_DATA_GROUPS = Sets.newHashSet("group1","group2");
}
