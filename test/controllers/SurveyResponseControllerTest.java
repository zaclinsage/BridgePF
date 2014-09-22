package controllers;

import static play.test.Helpers.running;
import static play.test.Helpers.testServer;
import static org.junit.Assert.*;
import static org.sagebionetworks.bridge.TestConstants.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponseDao;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyResponseControllerTest {
    
    @Resource
    TestUserAdminHelper helper;
    
    @Resource
    DynamoSurveyResponseDao responseDao;
    
    @Resource
    DynamoSurveyDao surveyDao;
    
    private ObjectMapper mapper = new ObjectMapper();
    private TestSurvey survey;
    private String responseGuid;
    
    @Before
    public void before() {
        helper.createOneUser(Lists.newArrayList(BridgeConstants.ADMIN_GROUP));
        survey = new TestSurvey(true);
        surveyDao.createSurvey(survey);
    }
    
    @After
    public void after() {
        if (responseGuid != null) {
            responseDao.deleteSurveyResponse(responseDao.getSurveyResponse(responseGuid));
        }
        if (survey != null) {
            surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());    
        }
        helper.deleteOneUser();
    }
    
    @Test
    public void submitAnswersColdForASurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            @Override
            public void testCode() throws Exception {
                // 
                SurveyQuestion question1 = survey.getBooleanQuestion();
                SurveyQuestion question2 = survey.getIntegerQuestion();
                
                List<SurveyAnswer> list = Lists.newArrayList();
                SurveyAnswer answer = new SurveyAnswer();
                answer.setQuestionGuid(question1.getGuid());
                answer.setAnswer(Boolean.TRUE);
                answer.setClient("test");
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                list.add(answer);
                
                answer = new SurveyAnswer();
                answer.setQuestionGuid(question2.getGuid());
                answer.setAnswer(null);
                answer.setAnswer(Boolean.TRUE);
                answer.setDeclined(true);
                answer.setClient("test");
                answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
                list.add(answer);
                
                String body = mapper.writeValueAsString(list);
                
                String url = String.format(NEW_SURVEY_RESPONSE, survey.getGuid(), survey.getVersionedOn());
                Response response = TestUtils.getURL(helper.getUserSessionToken(), url).post(body).get(TIMEOUT);
                assertEquals("Create new record returns 200", 200, response.getStatus());
                
                responseGuid = getGuid(response.getBody());
                
                SurveyResponse surveyResponse = responseDao.getSurveyResponse(responseGuid);
                assertEquals("There should be two answers", 2, surveyResponse.getAnswers().size());
            }
        });
    }
    
    private String getGuid(String body) throws Exception {
        JsonNode object = mapper.readTree(body);
        return object.get("guid").asText(); 
    }

}
