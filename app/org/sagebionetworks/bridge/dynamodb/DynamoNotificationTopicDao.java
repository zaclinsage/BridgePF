package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.google.common.collect.ImmutableList;

@Component
public class DynamoNotificationTopicDao implements NotificationTopicDao {
    private static Logger LOG = LoggerFactory.getLogger(DynamoNotificationTopicDao.class);
    
    private DynamoDBMapper mapper;
    
    private AmazonSNSClient snsClient;
    
    private BridgeConfig config;
    
    @Resource(name = "notificationTopicMapper")
    final void setNotificationTopicMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }

    @Resource(name = "bridgeConfig")
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }
    
    @Override
    public List<NotificationTopic> listTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);

        // Consistent reads is set to true, because this is the table's primary key, and having reliable tests is more
        // important than saving a small amount of DDB capacity.
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());
        DynamoDBQueryExpression<DynamoNotificationTopic> query = new DynamoDBQueryExpression<DynamoNotificationTopic>()
                .withConsistentRead(true).withHashKeyValues(hashKey);

        QueryResultPage<DynamoNotificationTopic> resultPage = mapper.queryPage(DynamoNotificationTopic.class, query);
        return ImmutableList.copyOf(resultPage.getResults());
    }

    @Override
    public NotificationTopic getTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        return getTopicInternal(studyId.getIdentifier(), guid);
    }

    private NotificationTopic getTopicInternal(String studyId, String guid) {
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId);
        hashKey.setGuid(guid);

        DynamoNotificationTopic topic = mapper.load(hashKey);
        if (topic == null) {
            throw new EntityNotFoundException(NotificationTopic.class);
        }
        return topic;
    }
    
    @Override
    public NotificationTopic createTopic(NotificationTopic topic) {
        checkNotNull(topic);
        
        // Create SNS topic first. If SNS fails, an exception is thrown. If DDB call fails, the SNS topic is orphaned
        // but that will not break the data integrity of Bridge data.
        
        topic.setGuid(BridgeUtils.generateGuid());
        
        String snsTopicName = createSnsTopicName(topic);
        CreateTopicRequest request = new CreateTopicRequest().withName(snsTopicName);
        CreateTopicResult result = snsClient.createTopic(request);
        topic.setTopicARN(result.getTopicArn());
        long timestamp = DateUtils.getCurrentMillisFromEpoch();
        topic.setCreatedOn(timestamp);
        topic.setModifiedOn(timestamp);
        
        mapper.save(topic);
        return topic;
    }

    @Override
    public NotificationTopic updateTopic(NotificationTopic topic) {
        checkNotNull(topic);
        checkNotNull(topic.getGuid());

        NotificationTopic existing = getTopicInternal(topic.getStudyId(), topic.getGuid());
        existing.setName(topic.getName());
        existing.setDescription(topic.getDescription());
        existing.setModifiedOn( DateUtils.getCurrentMillisFromEpoch() );
        
        mapper.save(existing);
        
        return existing;
    }

    @Override
    public void deleteTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        NotificationTopic existing = getTopicInternal(studyId.getIdentifier(), guid);
        
        // Delete the DDB record first. If it fails an exception is thrown. If SNS fails, the SNS topic
        // is not deleted, but the DDB record has successfully deleted, so suppress the exception (just 
        // log it) because the topic has been deleted from Bridge without a referential integrity problem.
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());
        hashKey.setGuid(guid);
        
        mapper.delete(hashKey);
        
        try {
            DeleteTopicRequest request = new DeleteTopicRequest().withTopicArn(existing.getTopicARN());
            snsClient.deleteTopic(request);
        } catch(AmazonServiceException e) {
            LOG.warn("Bridge topic '" + existing.getName() + "' in study '" + existing.getStudyId()
                    + "' deleted, but SNS topic deletion threw exception", e);
        }
    }
    
    @Override
    public void deleteAllTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        List<NotificationTopic> topics = listTopics(studyId);
        // Delete them individually. 
        for (NotificationTopic topic : topics) {
            deleteTopic(studyId, topic.getGuid());
        }
    }

    /**
     * So we can find these in the AWS console, we give these a specifically formatted name.
     */
    private String createSnsTopicName(NotificationTopic topic) {
        return topic.getStudyId() + "-" + config.getEnvironment().name().toLowerCase() + "-" + topic.getGuid();
    }
}
