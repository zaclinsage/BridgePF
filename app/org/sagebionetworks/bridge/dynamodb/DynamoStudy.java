package org.sagebionetworks.bridge.dynamodb;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

@DynamoDBTable(tableName = "Study")
@BridgeTypeName("Study")
@JsonFilter("filter")
public final class DynamoStudy implements Study {
    private String name;
    private String sponsorName;
    private String identifier;
    private String stormpathHref;
    private String supportEmail;
    private Long synapseDataAccessTeamId;
    private String synapseProjectId;
    private String technicalEmail;
    private boolean usesCustomExportSchedule;
    private String consentNotificationEmail;
    private int minAgeOfConsent;
    private Long version;
    private boolean active;
    private Set<String> profileAttributes;
    private Set<String> taskIdentifiers;
    private Set<String> dataGroups;
    private PasswordPolicy passwordPolicy;
    private EmailTemplate verifyEmailTemplate;
    private EmailTemplate resetPasswordTemplate;
    private EmailTemplate emailSignInTemplate;
    private boolean strictUploadValidationEnabled;
    private boolean healthCodeExportEnabled;
    private boolean emailVerificationEnabled;
    private boolean externalIdValidationEnabled;
    private boolean emailSignInEnabled;
    private boolean externalIdRequiredOnSignup;
    private Map<String, Integer> minSupportedAppVersions;
    private Map<String, String> pushNotificationARNs;
    private boolean disableExport;

    public DynamoStudy() {
        profileAttributes = new HashSet<>();
        taskIdentifiers = new HashSet<>();
        dataGroups = new HashSet<>();
        minSupportedAppVersions = new HashMap<>();
        pushNotificationARNs = new HashMap<>();
    }

    /** {@inheritDoc} */
    @Override
    public String getSponsorName() {
        return sponsorName;
    }

    @Override
    public void setSponsorName(String sponsorName) {
        this.sponsorName = sponsorName;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBHashKey
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    @DynamoDBIgnore
    public StudyIdentifier getStudyIdentifier() {
        return (identifier == null) ? null : new StudyIdentifierImpl(identifier);
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /** {@inheritDoc} */
    @Override
    public int getMinAgeOfConsent() {
        return minAgeOfConsent;
    }

    @Override
    public void setMinAgeOfConsent(int minAge) {
        this.minAgeOfConsent = minAge;
    }

    /** {@inheritDoc} */
    @Override
    public String getStormpathHref() {
        return stormpathHref;
    }

    @Override
    public void setStormpathHref(String stormpathHref) {
        this.stormpathHref = stormpathHref;
    }

    /** {@inheritDoc} */
    @Override
    public String getSupportEmail() {
        return supportEmail;
    }

    @Override
    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    /** {@inheritDoc} */
    @Override
    public Long getSynapseDataAccessTeamId() {
        return synapseDataAccessTeamId;
    }

    /** {@inheritDoc} */
    @Override
    public void setSynapseDataAccessTeamId(Long teamId) {
        this.synapseDataAccessTeamId = teamId;
    }

    /** {@inheritDoc} */
    @Override
    public String getSynapseProjectId() {
        return synapseProjectId;
    }

    /** {@inheritDoc} */
    @Override
    public void setSynapseProjectId(String projectId) {
        this.synapseProjectId = projectId;
    }

    /** {@inheritDoc} */
    @Override
    public String getTechnicalEmail() {
        return technicalEmail;
    }

    @Override
    public void setTechnicalEmail(String technicalEmail) {
        this.technicalEmail = technicalEmail;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getUsesCustomExportSchedule() {
        return usesCustomExportSchedule;
    }

    /** {@inheritDoc} */
    @Override
    public void setUsesCustomExportSchedule(boolean usesCustomExportSchedule) {
        this.usesCustomExportSchedule = usesCustomExportSchedule;
    }

    /** {@inheritDoc} */
    @Override
    public String getConsentNotificationEmail() {
        return consentNotificationEmail;
    }

    @Override
    public void setConsentNotificationEmail(String consentNotificationEmail) {
        this.consentNotificationEmail = consentNotificationEmail;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    public Set<String> getUserProfileAttributes() {
        return profileAttributes;
    }

    @Override
    public void setUserProfileAttributes(Set<String> profileAttributes) {
        this.profileAttributes = (profileAttributes == null) ? new HashSet<>() : profileAttributes;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    public Set<String> getTaskIdentifiers() {
        return taskIdentifiers;
    }

    @Override
    public void setTaskIdentifiers(Set<String> taskIdentifiers) {
        this.taskIdentifiers = (taskIdentifiers == null) ? new HashSet<>() : taskIdentifiers;
    }
    
    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    public Set<String> getDataGroups() {
        return dataGroups;
    }

    @Override
    public void setDataGroups(Set<String> dataGroups) {
        this.dataGroups = (dataGroups == null) ? new HashSet<>() : dataGroups;
    }
    
    /** {@inheritDoc} */
    @DynamoDBTypeConvertedJson
    @Override
    public PasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    @Override
    public void setPasswordPolicy(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConvertedJson
    @Override
    public EmailTemplate getVerifyEmailTemplate() {
        return verifyEmailTemplate;
    }

    @Override
    public void setVerifyEmailTemplate(EmailTemplate template) {
        this.verifyEmailTemplate = template;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConvertedJson
    @Override
    public EmailTemplate getResetPasswordTemplate() {
        return resetPasswordTemplate;
    }

    @Override
    public void setResetPasswordTemplate(EmailTemplate template) {
        this.resetPasswordTemplate = template;
    }
    
    /** {@inheritDoc} */
    @DynamoDBTypeConvertedJson
    @Override
    public EmailTemplate getEmailSignInTemplate() {
        return emailSignInTemplate;
    }
    
    @Override
    public void setEmailSignInTemplate(EmailTemplate template) {
        this.emailSignInTemplate = template;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStrictUploadValidationEnabled() {
        return strictUploadValidationEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setStrictUploadValidationEnabled(boolean enabled) {
        this.strictUploadValidationEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override 
    public boolean isEmailSignInEnabled() {
        return emailSignInEnabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setEmailSignInEnabled(boolean enabled){
        this.emailSignInEnabled = enabled;
    }
    
    
    /** {@inheritDoc} */
    @Override
    public boolean isHealthCodeExportEnabled() {
        return healthCodeExportEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setHealthCodeExportEnabled(boolean enabled) {
        this.healthCodeExportEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isEmailVerificationEnabled() {
        return emailVerificationEnabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setEmailVerificationEnabled(boolean enabled) {
        this.emailVerificationEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isExternalIdValidationEnabled() {
        return externalIdValidationEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setExternalIdValidationEnabled(boolean externalIdValidationEnabled) {
        this.externalIdValidationEnabled = externalIdValidationEnabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public Map<String,Integer> getMinSupportedAppVersions() {
        return minSupportedAppVersions;
    }

    @Override
    public void setMinSupportedAppVersions(Map<String,Integer> map) {
        this.minSupportedAppVersions = (map == null) ? new HashMap<>() : map;
    }
    
    /** {@inheritDoc} */
    @Override
    public Map<String,String> getPushNotificationARNs() {
        return pushNotificationARNs;
    }

    @Override
    public void setPushNotificationARNs(Map<String,String> map) {
        this.pushNotificationARNs = (map == null) ? new HashMap<>() : map;
    }

    @Override public boolean getDisableExport() {
        return this.disableExport;
    }

    @Override public void setDisableExport(boolean disable) {
        this.disableExport = disable;
    }

    @Override
    public boolean isExternalIdRequiredOnSignup() {
        return externalIdRequiredOnSignup;
    }

    @Override
    public void setExternalIdRequiredOnSignup(boolean externalIdRequiredOnSignup) {
        this.externalIdRequiredOnSignup = externalIdRequiredOnSignup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, minAgeOfConsent, name, sponsorName, supportEmail, technicalEmail,
                consentNotificationEmail, stormpathHref, version, profileAttributes, taskIdentifiers, dataGroups,
                passwordPolicy, verifyEmailTemplate, resetPasswordTemplate, active, strictUploadValidationEnabled,
                healthCodeExportEnabled, emailVerificationEnabled, externalIdValidationEnabled,
                externalIdRequiredOnSignup, minSupportedAppVersions, synapseDataAccessTeamId, synapseProjectId,
                usesCustomExportSchedule, pushNotificationARNs, disableExport, emailSignInTemplate,
                emailSignInEnabled);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoStudy other = (DynamoStudy) obj;

        return (Objects.equals(identifier, other.identifier) && Objects.equals(supportEmail, other.supportEmail)
                && Objects.equals(minAgeOfConsent, other.minAgeOfConsent) && Objects.equals(name, other.name)
                && Objects.equals(stormpathHref, other.stormpathHref)
                && Objects.equals(passwordPolicy, other.passwordPolicy) && Objects.equals(active, other.active))
                && Objects.equals(verifyEmailTemplate, other.verifyEmailTemplate)
                && Objects.equals(consentNotificationEmail, other.consentNotificationEmail)
                && Objects.equals(resetPasswordTemplate, other.resetPasswordTemplate)
                && Objects.equals(version, other.version)
                && Objects.equals(profileAttributes, other.profileAttributes)
                && Objects.equals(taskIdentifiers, other.taskIdentifiers)
                && Objects.equals(dataGroups, other.dataGroups)
                && Objects.equals(sponsorName, other.sponsorName)
                && Objects.equals(synapseDataAccessTeamId, other.synapseDataAccessTeamId)
                && Objects.equals(synapseProjectId, other.synapseProjectId)
                && Objects.equals(technicalEmail, other.technicalEmail)
                && Objects.equals(usesCustomExportSchedule, other.usesCustomExportSchedule)
                && Objects.equals(strictUploadValidationEnabled, other.strictUploadValidationEnabled)
                && Objects.equals(healthCodeExportEnabled, other.healthCodeExportEnabled)
                && Objects.equals(externalIdValidationEnabled, other.externalIdValidationEnabled)
                && Objects.equals(emailVerificationEnabled, other.emailVerificationEnabled)
                && Objects.equals(externalIdRequiredOnSignup, other.externalIdRequiredOnSignup)
                && Objects.equals(minSupportedAppVersions, other.minSupportedAppVersions)
                && Objects.equals(pushNotificationARNs, other.pushNotificationARNs)
                && Objects.equals(disableExport, other.disableExport)
                && Objects.equals(emailSignInTemplate, other.emailSignInTemplate)
                && Objects.equals(emailSignInEnabled, other.emailSignInEnabled);
    }

    @Override
    public String toString() {
        return String.format(
            "DynamoStudy [name=%s, active=%s, sponsorName=%s, identifier=%s, stormpathHref=%s, minAgeOfConsent=%s, "
                        + "supportEmail=%s, synapseDataAccessTeamId=%s, synapseProjectId=%s, technicalEmail=%s, "
                        + "consentNotificationEmail=%s, version=%s, userProfileAttributes=%s, taskIdentifiers=%s, "
                        + "dataGroups=%s, passwordPolicy=%s, verifyEmailTemplate=%s, resetPasswordTemplate=%s, "
                        + "strictUploadValidationEnabled=%s, healthCodeExportEnabled=%s, emailVerificationEnabled=%s, "
                        + "externalIdValidationEnabled=%s, externalIdRequiredOnSignup=%s, minSupportedAppVersions=%s, "
                        + "usesCustomExportSchedule=%s, pushNotificationARNs=%s], "
                        + "disableExport=%s, emailSignInTemplate=%s, emailSignInEnabled=%s]",
                name, active, sponsorName, identifier, stormpathHref, minAgeOfConsent, supportEmail,
                synapseDataAccessTeamId, synapseProjectId, technicalEmail, consentNotificationEmail, version,
                profileAttributes, taskIdentifiers, dataGroups, passwordPolicy, verifyEmailTemplate,
                resetPasswordTemplate, strictUploadValidationEnabled, healthCodeExportEnabled, emailVerificationEnabled,
                externalIdValidationEnabled, externalIdRequiredOnSignup, minSupportedAppVersions,
                usesCustomExportSchedule, pushNotificationARNs, disableExport, emailSignInTemplate,
                emailSignInEnabled);
    }
}
