package org.sagebionetworks.bridge.validators;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;

import com.google.common.collect.Lists;

public class SurveyAnswerValidatorTest {

    private SurveyAnswerValidator validator;
    
    private DynamoSurveyQuestion createQuestion(Constraints constraints) {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setGuid("AAA");
        question.setIdentifier("Test Question");
        question.setPrompt("This is a test question?");
        question.setUiHint(constraints.getSupportedHints().iterator().next());
        question.setConstraints(constraints);
        return question;
    }
    
    private SurveyAnswer createAnswer(String value) {
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnswer(value);
        answer.setClient("mobile");
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setQuestionGuid("AAA");
        return answer;
    }
    
    private SurveyAnswer createAnswers(List<String> values) {
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnswers(values);
        answer.setClient("mobile");
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setQuestionGuid("AAA");
        return answer;
    }
    
    private List<SurveyQuestionOption> getOptions() {
        return Lists.<SurveyQuestionOption>newArrayList(
            new SurveyQuestionOption("label 1", "1"),
            new SurveyQuestionOption("label 2", "2"),
            new SurveyQuestionOption("label 3", "3")
        );        
    }
    
    @Test(expected = InvalidEntityException.class)
    public void validateDataType() {
        BooleanConstraints constraints = new BooleanConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("This is not a boolean");
        validator.validate(answer);
    }
    
    @Test
    public void validateBoolean() {
        BooleanConstraints constraints = new BooleanConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("true");
        validator.validate(answer);
    }
    @Test
    public void validateDateAllowFuture() {
        DateConstraints constraints = new DateConstraints();
        constraints.setAllowFuture(true);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        validator.validate(createAnswer(DateUtils.convertToISODateTime(future)));
    }
    
    @Test(expected = InvalidEntityException.class)
    public void validateDateDoNotAllowFutrue() {
        DateConstraints constraints = new DateConstraints();
        constraints.setAllowFuture(false);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        validator.validate(createAnswer(DateUtils.convertToISODateTime(future)));
    }
    @Test
    public void validateDateTimeAllowFuture() {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setAllowFuture(true);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        validator.validate(createAnswer(DateUtils.convertToISODateTime(future)));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDateTimeDoNotAllowFuture() {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setAllowFuture(false);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        validator.validate(createAnswer(DateUtils.convertToISODateTime(future)));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDecimalMinValue() {
        DecimalConstraints constraints = new DecimalConstraints();
        constraints.setMinValue(10d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        validator.validate(createAnswer("5.001"));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDecimalMaxValue() {
        DecimalConstraints constraints = new DecimalConstraints();
        constraints.setMaxValue(10d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        validator.validate(createAnswer("15.0"));
    }
    @Test(expected = InvalidEntityException.class)
    @Ignore
    public void validateDecimalStep() {
        DecimalConstraints constraints = new DecimalConstraints();
        constraints.setStep(5d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        validator.validate(createAnswer("12.00"));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDuration() {
        DurationConstraints constraints = new DurationConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("14000");
        validator.validate(answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateIntegerMinValue() {
        IntegerConstraints constraints = new IntegerConstraints();
        constraints.setMinValue(15L);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("10");
        validator.validate(answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateIntegerMaxValue() {
        IntegerConstraints constraints = new IntegerConstraints();
        constraints.setMaxValue(10L);
        validator = new SurveyAnswerValidator(createQuestion(constraints));

        SurveyAnswer answer = createAnswer("12");
        validator.validate(answer);
    }
    @Test
    public void validateIntegerNoConstraints() {
        IntegerConstraints constraints = new IntegerConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("12");
        validator.validate(answer);
    }
    @Test(expected=InvalidEntityException.class)
    public void validateMultiValueWithNoOther() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        constraints.setDataType(DataType.INTEGER);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("6");
        validator.validate(answer);
    }
    @Test
    public void validateMultiValueWithOther() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        constraints.setDataType(DataType.INTEGER);
        constraints.setAllowOther(true);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        // The only validation that can happen is type, so any value is okay here
        SurveyAnswer answer = createAnswer("6");
        validator.validate(answer);
    }
    @Test
    public void validateMultiValueWithMultipleValues() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        constraints.setDataType(DataType.INTEGER);
        constraints.setAllowMultiple(true);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswers(Lists.<String>newArrayList("1", "2"));
        validator.validate(answer);
    }
    @Test
    public void validateMultiValueOtherAllowMultiple() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        
        constraints.setDataType(DataType.INTEGER);
        constraints.setAllowMultiple(true);
        constraints.setAllowOther(true);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        // The only validation that can happen is type, so any value is okay here
        SurveyAnswer answer = createAnswers(Lists.<String>newArrayList("1", "2", "10"));
        validator.validate(answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateStringTooShort() {
        StringConstraints constraints = new StringConstraints();
        constraints.setMinLength(5);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("axe");
        validator.validate(answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateStringTooLong() {
        StringConstraints constraints = new StringConstraints();
        constraints.setMaxLength(5);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("belgium");
        validator.validate(answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateStringFailsPatternMatch() {
        StringConstraints constraints = new StringConstraints();
        constraints.setPattern("\\d{3}-\\d{3}-\\d{4}");
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("123-a67-9870");
        validator.validate(answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateInvalidTime() {
        TimeConstraints constraints = new TimeConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("asdf");
        validator.validate(answer);
    }
    @Test
    public void validateTime() {
        TimeConstraints constraints = new TimeConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("13:00"); // no seconds, that's okay
        validator.validate(answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateTimeWithTimeZone() {
        TimeConstraints constraints = new TimeConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("13:47:30Z"); // time zone, verboten
        validator.validate(answer);
    }
}
