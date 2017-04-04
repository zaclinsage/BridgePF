package org.sagebionetworks.bridge.stormpath;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;
import com.stormpath.sdk.application.ApplicationAccountStoreMapping;
import com.stormpath.sdk.application.ApplicationAccountStoreMappingCriteria;
import com.stormpath.sdk.application.ApplicationAccountStoreMappings;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.AccountCreationPolicy;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.directory.PasswordPolicy;
import com.stormpath.sdk.directory.PasswordStrength;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupCriteria;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.group.Groups;
import com.stormpath.sdk.mail.EmailStatus;
import com.stormpath.sdk.mail.ModeledEmailTemplate;
import com.stormpath.sdk.mail.ModeledEmailTemplateList;
import com.stormpath.sdk.resource.ResourceException;

@Component
public class StormpathDirectoryDao implements DirectoryDao {

    private static final Logger logger = LoggerFactory.getLogger(StormpathDirectoryDao.class);

    // Package-scoped to facilitate unit tests.
    static final ApplicationAccountStoreMappingCriteria ASM_CRITERIA = ApplicationAccountStoreMappings.criteria()
            .limitTo(100);

    private BridgeConfig config;
    private Client client;

    @Autowired
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }
    @Autowired
    public void setStormpathClient(Client client) {
        this.client = client;
    }

    @Override
    public String createDirectoryForStudy(Study study) {
        checkNotNull(study);
        checkArgument(isNotBlank(study.getIdentifier()), Validate.CANNOT_BE_BLANK, "identifier");
        Application app = getApplication();
        checkNotNull(app);
        String dirName = createDirectoryName(study.getIdentifier());

        Directory directory = getDirectoryForStudy(study);
        if (directory == null) {
            directory = client.instantiate(Directory.class);
            directory.setName(dirName);
            directory = client.createDirectory(directory);
        }
        
        adjustPasswordPolicies(study, directory);
        adjustVerifyEmailPolicies(study, directory);
        
        ApplicationAccountStoreMapping mapping = getApplicationMapping(directory.getHref(), app);
        if (mapping == null) {
            mapping = client.instantiate(ApplicationAccountStoreMapping.class);
            mapping.setAccountStore(directory);
            mapping.setApplication(app);
            mapping.setDefaultAccountStore(Boolean.FALSE);
            mapping.setDefaultGroupStore(Boolean.FALSE);
            mapping.setListIndex(10); // this is a priority number
            app.createAccountStoreMapping(mapping);
        }
        for (Roles role : Roles.values()) {
            Group group = getGroup(directory, role);
            if (group == null) {
                group = client.instantiate(Group.class);
                group.setName(role.name().toLowerCase());
                directory.createGroup(group);
            }
        }
        return directory.getHref();
    }
    
    /**
     * Once a study is created, the researcher can only change the password policies, and the email templates
     * for the email verification and password reset workflows.
     */
    @Override
    public void updateDirectoryForStudy(Study study) {
        checkNotNull(study);
        
        Directory directory = getDirectoryForStudy(study);
        adjustPasswordPolicies(study, directory);
        adjustVerifyEmailPolicies(study, directory);
    }

    @Override
    public Directory getDirectoryForStudy(Study study) {
        if (study != null && StringUtils.isNotBlank(study.getStormpathHref())) {
            try {
                return client.getResource(study.getStormpathHref(), Directory.class);    
            } catch(ResourceException e) {
                // Not unusual, as we check for the existence of a directory before creating a study.
                // Only rethrow if this is not a 404, otherwise ignore.
                if (e.getCode() != 404) {
                    throw e;
                }
            }
        }
        return null;
    }

    @Override
    public void deleteDirectoryForStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        Application app = getApplication();
        checkNotNull(app);

        // delete the directory
        Directory directory = getDirectoryForStudy(study);
        if (directory != null) {
            directory.delete();
        } else {
            logger.warn("Directory not found: " + study.getStormpathHref());
        }
    }

    private static Group getGroup(Directory dir, Roles role) {
        GroupCriteria criteria = Groups.where(Groups.name().eqIgnoreCase(role.name().toLowerCase()));
        GroupList list = dir.getGroups(criteria);
        return (list.iterator().hasNext()) ? list.iterator().next() : null;
    }
    
    private String createDirectoryName(String identifier) {
        return String.format("%s (%s)", identifier, config.getEnvironment().name().toLowerCase());
    }
    
    private Application getApplication() {
        return client.getResource(config.getStormpathApplicationHref(), Application.class);
    }
    
    private static ApplicationAccountStoreMapping getApplicationMapping(String href, Application app) {
        // This is tedious but I see no way to search for or make a reference to this 
        // mapping without iterating through the application's mappings.
        for (ApplicationAccountStoreMapping mapping : app.getAccountStoreMappings(ASM_CRITERIA)) {
            if (mapping.getAccountStore().getHref().equals(href)) {
                return mapping;
            }
        }
        return null;
    }

    private void adjustPasswordPolicies(Study study, Directory directory) {
        PasswordPolicy passwordPolicy = directory.getPasswordPolicy();
        
        // 7/18/2016: Stormpath has inserted extra templates into our directories, causing the 
        // single() method call to fail (it fails when there are multiple templates). Stick to 
        // the first template and see if this is a workaround (there's no API to delete these 
        // unwanted templates). 
        // ModeledEmailTemplate template = policy.getAccountVerificationEmailTemplates().single();
        ModeledEmailTemplate template = findBridgeTemplate(passwordPolicy.getResetEmailTemplates());
        updateTemplate(study, template, study.getResetPasswordTemplate(), "resetPassword");
        
        PasswordStrength strength = passwordPolicy.getStrength();
        strength.setMaxLength(org.sagebionetworks.bridge.models.studies.PasswordPolicy.FIXED_MAX_LENGTH);
        strength.setMinDiacritic(0);
        strength.setMinLength(study.getPasswordPolicy().getMinLength());
        strength.setMinNumeric(study.getPasswordPolicy().isNumericRequired() ? 1 : 0);
        strength.setMinSymbol(study.getPasswordPolicy().isSymbolRequired() ? 1 : 0);
        strength.setMinLowerCase(study.getPasswordPolicy().isLowerCaseRequired() ? 1 : 0);
        strength.setMinUpperCase(study.getPasswordPolicy().isUpperCaseRequired() ? 1 : 0);
        strength.save();
        
        passwordPolicy.setResetEmailStatus(EmailStatus.ENABLED);
        passwordPolicy.setResetSuccessEmailStatus(EmailStatus.DISABLED);
        passwordPolicy.save();
    }
    
    private void adjustVerifyEmailPolicies(Study study, Directory directory) {
        AccountCreationPolicy policy = directory.getAccountCreationPolicy();
        
        // 7/18/2016: Stormpath has inserted extra templates into our directories, causing the 
        // single() method call to fail (it fails when there are multiple templates). Stick to 
        // the first template and see if this is a workaround (there's no API to delete these 
        // unwanted templates). 
        // ModeledEmailTemplate template = policy.getAccountVerificationEmailTemplates().single();
        ModeledEmailTemplate template = findBridgeTemplate(policy.getAccountVerificationEmailTemplates());
        updateTemplate(study, template, study.getVerifyEmailTemplate(), "verifyEmail");

        EmailStatus verifyEmailStatus = study.isEmailVerificationEnabled() ? EmailStatus.ENABLED : EmailStatus.DISABLED;
        policy.setVerificationEmailStatus(verifyEmailStatus);
        policy.setVerificationSuccessEmailStatus(EmailStatus.DISABLED);
        policy.setWelcomeEmailStatus(EmailStatus.DISABLED);
        policy.save();
    }
    
    private void updateTemplate(Study study, ModeledEmailTemplate stormpathTemplate, EmailTemplate template, String pageName) {
        if (study.getSponsorName() != null) {
            stormpathTemplate.setFromName(study.getSponsorName());    
        } else {
            stormpathTemplate.setFromName(study.getName());
        }
        stormpathTemplate.setFromEmailAddress(study.getSupportEmail());

        String subject = partiallyResolveTemplate(template.getSubject(), study);
        stormpathTemplate.setSubject(subject);
        
        com.stormpath.sdk.mail.MimeType stormpathMimeType = getStormpathMimeType(template);
        stormpathTemplate.setMimeType(stormpathMimeType);
        
        String body = partiallyResolveTemplate(template.getBody(), study);
        stormpathTemplate.setTextBody(body);
        stormpathTemplate.setHtmlBody(body);

        String link = String.format("%s/mobile/%s.html?study=%s", config.getWebservicesURL(), pageName, study.getIdentifier());
        stormpathTemplate.setLinkBaseUrl(link);
        stormpathTemplate.save();
    }
    
    private ModeledEmailTemplate findBridgeTemplate(ModeledEmailTemplateList list) {
        for (ModeledEmailTemplate template : list) {
            String body = template.getTextBody();
            if (body == null) {
                body = template.getHtmlBody();
            }
            if (body != null && !body.contains("Stormpath")) {
                return template;
            }
        }
        // If it doesn't exist... we need the one that's the default when a directory is first created.
        return list.iterator().next();
    }

    public static com.stormpath.sdk.mail.MimeType getStormpathMimeType(EmailTemplate template) {
        return (template.getMimeType() == MimeType.TEXT) ? 
            com.stormpath.sdk.mail.MimeType.PLAIN_TEXT : com.stormpath.sdk.mail.MimeType.HTML;
    }
    
    private static String partiallyResolveTemplate(String template, Study study) {
        Map<String,String> map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("supportEmail", study.getSupportEmail());
        map.put("technicalEmail", study.getTechnicalEmail());
        map.put("sponsorName", study.getSponsorName());
        return BridgeUtils.resolveTemplate(template, map);
    }
}
