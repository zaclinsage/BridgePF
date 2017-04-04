package org.sagebionetworks.bridge.validators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.upload.UploadUtil;

/** Validator for {@link org.sagebionetworks.bridge.models.upload.UploadSchema} */
public class UploadSchemaValidator implements Validator {
    private static final char MULTI_CHOICE_FIELD_SEPARATOR = '.';
    private static final String OTHER_CHOICE_FIELD_SUFFIX = ".other";
    private static final String TIME_ZONE_FIELD_SUFFIX = ".timezone";

    /** Singleton instance of this validator. */
    public static final UploadSchemaValidator INSTANCE = new UploadSchemaValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return UploadSchema.class.isAssignableFrom(clazz);
    }

    /**
     * <p>
     * Validates the given object as a valid UploadSchema instance. This will flag errors in the following
     * conditions:
     *   <ul>
     *     <li>value is null or not an UploadSchema</li>
     *     <li>fieldDefinitions is null or empty</li>
     *     <li>fieldDefinitions contains null or invalid entries</li>
     *     <li>minAppVersion is greater than maxAppVersion</li>
     *     <li>name is blank</li>
     *     <li>revision is zero or negative</li>
     *     <li>schemaId is blank</li>
     *     <li>schemaType is null</li>
     *     <li>studyId is blank</li>
     *   </ul>
     * </p>
     *
     * @see org.springframework.validation.Validator#validate
     */
    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("uploadSchema", "cannot be null");
        } else if (!(target instanceof UploadSchema)) {
            errors.rejectValue("uploadSchema", "is the wrong type");
        } else {
            UploadSchema uploadSchema = (UploadSchema) target;

            // min/maxAppVersion
            for (String osName : uploadSchema.getAppVersionOperatingSystems()) {
                Integer minAppVersion = uploadSchema.getMinAppVersion(osName);
                Integer maxAppVersion = uploadSchema.getMaxAppVersion(osName);
                if (minAppVersion != null && maxAppVersion != null && minAppVersion > maxAppVersion) {
                    errors.rejectValue("minAppVersions{" + osName + "}", "can't be greater than maxAppVersion");
                }
            }

            // name
            if (StringUtils.isBlank(uploadSchema.getName())) {
                errors.rejectValue("name", "is required");
            }

            // revision must be specified and positive
            if (uploadSchema.getRevision() <= 0) {
                errors.rejectValue("revision", "must be positive");
            }

            // schema ID
            if (StringUtils.isBlank(uploadSchema.getSchemaId())) {
                errors.rejectValue("schemaId", "is required");
            }

            // schema type
            if (uploadSchema.getSchemaType() == null) {
                errors.rejectValue("schemaType", "is required");
            }

            // study ID
            if (StringUtils.isBlank(uploadSchema.getStudyId())) {
                errors.rejectValue("studyId", "is required");
            }

            // fieldDefinitions
            List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
            if (fieldDefList == null || fieldDefList.isEmpty()) {
                errors.rejectValue("fieldDefinitions", "requires at least one definition");
            } else {
                // Keep track of field names seen. This list may include duplicates, which we validate in a later step.
                List<String> fieldNameList = new ArrayList<>();

                for (int i=0; i < fieldDefList.size(); i++) {
                    UploadFieldDefinition fieldDef = fieldDefList.get(i);
                    String fieldDefinitionKey = "fieldDefinitions["+i+"]";
                    if (fieldDef == null) {
                        errors.rejectValue(fieldDefinitionKey, "is required");
                    } else {
                        errors.pushNestedPath(fieldDefinitionKey);

                        String fieldName = fieldDef.getName();
                        if (StringUtils.isBlank(fieldName)) {
                            errors.rejectValue("name", "is required");
                        } else {
                            fieldNameList.add(fieldName);

                            // Validate field name.
                            if (!UploadUtil.isValidSchemaFieldName(fieldName)) {
                                errors.rejectValue("name", String.format(UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE,
                                        fieldName));
                            }
                        }

                        UploadFieldType fieldType = fieldDef.getType();
                        //noinspection ConstantConditions
                        if (fieldType == null) {
                            errors.rejectValue("type", "is required");
                        }

                        if (fieldType == UploadFieldType.MULTI_CHOICE) {
                            List<String> multiChoiceAnswerList = fieldDef.getMultiChoiceAnswerList();
                            if (multiChoiceAnswerList == null || multiChoiceAnswerList.isEmpty()) {
                                errors.rejectValue("multiChoiceAnswerList", "must be specified for MULTI_CHOICE field "
                                        + fieldName);
                            } else {
                                // Multi-Choice fields create extra "sub-field" columns, and we need to check for
                                // potential name collisions.

                                int numAnswers = multiChoiceAnswerList.size();
                                for (int j = 0; j < numAnswers; j++) {
                                    String oneAnswer = multiChoiceAnswerList.get(j);
                                    fieldNameList.add(fieldName + MULTI_CHOICE_FIELD_SEPARATOR + oneAnswer);

                                    // Validate choice answer name.
                                    if (!UploadUtil.isValidAnswerChoice(oneAnswer)) {
                                        errors.rejectValue("multiChoice[" + j + "]", String.format(
                                                UploadUtil.INVALID_ANSWER_CHOICE_ERROR_MESSAGE, oneAnswer));
                                    }
                                }
                            }

                            if (Boolean.TRUE.equals(fieldDef.getAllowOtherChoices())) {
                                // Similarly, there's an "other" field.
                                fieldNameList.add(fieldName + OTHER_CHOICE_FIELD_SUFFIX);
                            }
                        } else if (fieldType == UploadFieldType.TIMESTAMP) {
                            // Timestamp fields also generate a second subfield for timezone. Need to check for name
                            // collisions here too.
                            fieldNameList.add(fieldName + TIME_ZONE_FIELD_SUFFIX);
                        }

                        //noinspection ConstantConditions
                        if (fieldDef.isUnboundedText() != null && fieldDef.isUnboundedText() &&
                                fieldDef.getMaxLength() != null) {
                            errors.rejectValue("unboundedText", "cannot specify unboundedText=true with a maxLength");
                        }

                        errors.popNestedPath();
                    }
                }

                // Check for duplicate field names. Dupe set is a tree set so our error messages are in a predictable
                // alphabetical order.
                Set<String> seenFieldNameSet = new HashSet<>();
                Set<String> dupeFieldNameSet = new TreeSet<>();
                for (String oneFieldName : fieldNameList) {
                    if (seenFieldNameSet.contains(oneFieldName)) {
                        dupeFieldNameSet.add(oneFieldName);
                    } else {
                        seenFieldNameSet.add(oneFieldName);
                    }
                }

                if (!dupeFieldNameSet.isEmpty()) {
                    errors.rejectValue("fieldDefinitions", "conflict in field names or sub-field names: " +
                            BridgeUtils.COMMA_SPACE_JOINER.join(dupeFieldNameSet));
                }
            }
        }
    }
}
