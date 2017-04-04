package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.ApplicationAccountStoreMapping;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.AccountCreationPolicy;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.directory.PasswordStrength;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.mail.EmailStatus;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormpathDirectoryDaoTest {

    @Resource
    StormpathDirectoryDao directoryDao;
    
    @Resource
    Client client;
    
    private DynamoStudy study;
    
    @After
    public void after() {
        if (study != null) {
            directoryDao.deleteDirectoryForStudy(study);
        }
    }
    
    @Test
    public void crudDirectory() throws Exception {
        study = TestUtils.getValidStudy(StormpathDirectoryDaoTest.class);
        
        String stormpathHref = directoryDao.createDirectoryForStudy(study);
        study.setStormpathHref(stormpathHref);
        BridgeConfig config = BridgeConfigFactory.getConfig();
        
        // Verify the directory and mapping were created
        Directory directory = getDirectory(stormpathHref);

        assertEquals("Name is the right one", study.getIdentifier() + " ("+config.getEnvironment().name().toLowerCase()+")", directory.getName());
        assertTrue("Mapping exists for new directory in the right application", containsMapping(stormpathHref));
        assertTrue("The researcher group was created", groupExists(directory, RESEARCHER));
        assertTrue("The developer group was created", groupExists(directory, DEVELOPER));
        assertTrue("The admin group was created", groupExists(directory, ADMIN));
        assertTrue("The test_users group was created", groupExists(directory, TEST_USERS));
        
        Directory newDirectory = directoryDao.getDirectoryForStudy(study);
        assertDirectoriesAreEqual(study, "subject", "subject", directory, newDirectory);
        // This is enabled, by default.
        assertEquals(EmailStatus.ENABLED, newDirectory.getAccountCreationPolicy().getVerificationEmailStatus());
        
        // Verify that we can update the directory.
        study.setPasswordPolicy(new PasswordPolicy(3, true, true, true, true));
        study.setResetPasswordTemplate(new EmailTemplate("new rp subject", "new rp body ${url}", MimeType.TEXT));
        study.setVerifyEmailTemplate(new EmailTemplate("new ve subject", "<p>new ve body ${url}</p>", MimeType.HTML));
        
        directoryDao.updateDirectoryForStudy(study);
        
        newDirectory = directoryDao.getDirectoryForStudy(study);
        assertDirectoriesAreEqual(study, "new rp subject", "new ve subject", directory, newDirectory);

        // disable email verification
        study.setEmailVerificationEnabled(false);
        directoryDao.updateDirectoryForStudy(study);
        
        newDirectory = directoryDao.getDirectoryForStudy(study);
        // This has been disabled.
        assertEquals(EmailStatus.DISABLED, newDirectory.getAccountCreationPolicy().getVerificationEmailStatus());
        
        directoryDao.deleteDirectoryForStudy(study);
        newDirectory = directoryDao.getDirectoryForStudy(study);
        assertNull("Directory has been deleted", newDirectory);
        assertFalse("Mapping no longer exists", containsMapping(stormpathHref));
        
        study = null;
    }

    @SuppressWarnings("UnusedParameters")
    private void assertDirectoriesAreEqual(DynamoStudy study, String rpSubject, String veSubject, Directory directory, Directory newDirectory) throws Exception {
        assertEquals(directory.getHref(), newDirectory.getHref());
        
        com.stormpath.sdk.directory.PasswordPolicy passwordPolicy = newDirectory.getPasswordPolicy();
        assertEquals(EmailStatus.ENABLED, passwordPolicy.getResetEmailStatus());
        assertEquals(EmailStatus.DISABLED, passwordPolicy.getResetSuccessEmailStatus());

        // There's a known bug that sometimes Stormpath email templates won't update.
        // See https://support.stormpath.com/hc/en-us/requests/11976
        // We already test that we update email template content in StormpathDirectoryDaoMockTest. In the meantime,
        // check only that the email templates exist, but don't verify their content.
        assertEquals(1, passwordPolicy.getResetEmailTemplates().getSize());

        PasswordStrength strength = passwordPolicy.getStrength();
        assertEquals(PasswordPolicy.FIXED_MAX_LENGTH, strength.getMaxLength());
        assertEquals(study.getPasswordPolicy().isNumericRequired() ? 1 : 0, strength.getMinNumeric());
        assertEquals(study.getPasswordPolicy().isSymbolRequired() ? 1 : 0, strength.getMinSymbol());
        assertEquals(study.getPasswordPolicy().isLowerCaseRequired() ? 1 : 0, strength.getMinLowerCase());
        assertEquals(study.getPasswordPolicy().isUpperCaseRequired() ? 1 : 0, strength.getMinUpperCase());
        assertEquals(0, strength.getMinDiacritic());

        // Similarly, check that the reset password template exists, but don't verify the content.
        assertEquals(study.getPasswordPolicy().getMinLength(), strength.getMinLength());
        
        AccountCreationPolicy policy = newDirectory.getAccountCreationPolicy();
        assertEquals(EmailStatus.ENABLED, policy.getVerificationEmailStatus());
        assertEquals(EmailStatus.DISABLED, policy.getWelcomeEmailStatus());
        assertEquals(1, policy.getAccountVerificationEmailTemplates().getSize());
    }
    
    private boolean groupExists(Directory directory, Roles role) {
        for (Group group : directory.getGroups()) {
            if (group.getName().equals(role.name().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private Directory getDirectory(String href) {
        return client.getResource(href, Directory.class);
    }
    
    private Application getApplication() {
        return client.getResource(BridgeConfigFactory.getConfig().getStormpathApplicationHref(), Application.class);
    }
    
    private boolean containsMapping(String href) {
        Application app = getApplication();
        for (ApplicationAccountStoreMapping mapping : app.getAccountStoreMappings()) {
            if (mapping.getAccountStore().getHref().equals(href)) {
                return true;    
            }
        }
        return false;
    }
    
}
