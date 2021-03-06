package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.GE;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.LT;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NOT_NULL;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NULL;
import static com.amazonaws.services.dynamodbv2.model.ConditionalOperator.AND;
import static com.amazonaws.services.dynamodbv2.model.ConditionalOperator.OR;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.joda.time.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ConditionalOperator;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoExternalIdDao implements ExternalIdDao {
    
    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private static final String RESERVATION = "reservation";
    private static final String HEALTH_CODE = "healthCode";
    private static final String IDENTIFIER = "identifier";
    private static final String STUDY_ID = "studyId";
    private static final String ASSIGNMENT_FILTER = "assignmentFilter";
    private static final String ID_FILTER = "idFilter";

    private int addLimit;
    private int lockDuration;
    private DynamoDBMapper mapper;

    /** Gets the add limit and lock duration from Config. */
    @Autowired
    public final void setConfig(Config config) {
        addLimit = config.getInt(CONFIG_KEY_ADD_LIMIT);
        lockDuration = config.getInt(CONFIG_KEY_LOCK_DURATION);
    }
    
    @Resource(name = "externalIdDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PagedResourceList<ExternalIdentifierInfo> getExternalIds(StudyIdentifier studyId, String offsetKey, 
            int pageSize, String idFilter, Boolean assignmentFilter) {
        checkNotNull(studyId);
        
        // Just set a sane upper limit on this.
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        // The offset key is applied after the idFilter. If the offsetKey doesn't match the beginning
        // of the idFilter, the AWS SDK throws a validation exception. So when providing an idFilter and 
        // a paging offset, clear the offset (go back to the first page) if they don't match.
        if (offsetKey != null && idFilter != null && !offsetKey.startsWith(idFilter)) {
            offsetKey = null;
        }
        PaginatedQueryList<DynamoExternalIdentifier> list = mapper.query(DynamoExternalIdentifier.class,
                createGetQuery(studyId, offsetKey, pageSize, idFilter, assignmentFilter));
        
        int total = mapper.count(DynamoExternalIdentifier.class, createCountQuery(studyId, idFilter, assignmentFilter));
        
        List<ExternalIdentifierInfo> identifiers = Lists.newArrayListWithCapacity(pageSize);
        
        Iterator<? extends ExternalIdentifier> iterator = list.iterator();
        while(iterator.hasNext() && identifiers.size() < pageSize) {
            ExternalIdentifier id = iterator.next();
            identifiers.add( createInfo(id, lockDuration) );
        }
        // This is the last key, not the next key of the next page of records. It only exists if there's a record
        // beyond the records we've converted to a page. Then get the last key in the list.
        String nextPageOffsetKey = (iterator.hasNext()) ? last(identifiers).getIdentifier() : null;
        
        PagedResourceList<ExternalIdentifierInfo> resourceList = new PagedResourceList<ExternalIdentifierInfo>(identifiers, null, pageSize, total)
                .withOffsetKey(nextPageOffsetKey)
                .withFilter(ID_FILTER, idFilter);
        if (assignmentFilter != null) {
            resourceList = resourceList.withFilter(ASSIGNMENT_FILTER, assignmentFilter.toString());
        }
        return resourceList;
    }

    @Override
    public void addExternalIds(StudyIdentifier studyId, List<String> externalIds) {
        checkNotNull(studyId);
        checkNotNull(externalIds);

        // We validate a wider range of issues in the service, but check size again because this is 
        // specifically a database capacity issue.
        if (externalIds.size() > addLimit) {
            throw new BadRequestException("List of externalIds is too large; size=" + externalIds.size() + ", limit=" + addLimit);
        }
        List<DynamoExternalIdentifier> idsToSave = externalIds.stream().map(id -> {
            return new DynamoExternalIdentifier(studyId, id);
        }).filter(externalId -> {
            return mapper.load(externalId) == null;
        }).collect(Collectors.toList());
        
        if (!idsToSave.isEmpty()) {
            List<FailedBatch> failures = mapper.batchSave(idsToSave);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    @Override
    public void reserveExternalId(StudyIdentifier studyId, String externalId) throws EntityAlreadyExistsException {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        if (identifier == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        try {
            long newReservation = DateUtils.getCurrentMillisFromEpoch();
            
            identifier.setReservation(newReservation);
            mapper.save(identifier, getReservationExpression(newReservation));
            
        } catch(ConditionalCheckFailedException e) {
            // The timeout is in effect or the healthCode is set, either way, code is "taken"
            throw new EntityAlreadyExistsException(identifier);
        }
    }

    @Override
    public void assignExternalId(StudyIdentifier studyId, String externalId, String healthCode) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        checkArgument(isNotBlank(healthCode));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        if (identifier == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        // If the same code has already been set, do nothing, do not throw an error.
        if (!healthCode.equals(identifier.getHealthCode())) {
            try {
                
                identifier.setReservation(0L);
                identifier.setHealthCode(healthCode);
                mapper.save(identifier, getAssignmentExpression());
                
            } catch(ConditionalCheckFailedException e) {
                // The timeout is in effect or the healthCode is set, either way, code is "taken"
                throw new EntityAlreadyExistsException(identifier);
            }        
        }
    }

    @Override
    public void unassignExternalId(StudyIdentifier studyId, String externalId) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        
        // Don't throw an exception if the identifier doesn't exist, we don't care.
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        if (identifier != null) {
            identifier.setHealthCode(null);
            identifier.setReservation(0L);
            mapper.save(identifier);
        }
    }
    
    /**
     * This is intended for testing. Deleting a large number of identifiers will cause DynamoDB capacity exceptions.
     */
    @Override
    public void deleteExternalIds(StudyIdentifier studyId, List<String> externalIds) {
        checkNotNull(studyId);
        checkNotNull(externalIds);
        
        if (!externalIds.isEmpty()) {
            List<DynamoExternalIdentifier> idsToDelete = externalIds.stream().map(id -> {
                return new DynamoExternalIdentifier(studyId, id);
            }).collect(Collectors.toList());
            
            List<FailedBatch> failures = mapper.batchDelete(idsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    /**
     * Get the count query (applies filters) and then sets an offset key and the limit to a page of records, 
     * plus one, to determine if there are records beyond the current page. 
     */
    private DynamoDBQueryExpression<DynamoExternalIdentifier> createGetQuery(StudyIdentifier studyId,
            String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter) {

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = createCountQuery(studyId, idFilter, assignmentFilter);
        if (offsetKey != null) {
            Map<String,AttributeValue> map = new HashMap<>();
            map.put(STUDY_ID, new AttributeValue().withS(studyId.getIdentifier()));
            map.put(IDENTIFIER, new AttributeValue().withS(offsetKey));
            query.withExclusiveStartKey(map);
        }
        query.withLimit(pageSize+1);
        return query;
    }

    /**
     * Create a query for records applying the filter values if they exist.
     */
    private DynamoDBQueryExpression<DynamoExternalIdentifier> createCountQuery(StudyIdentifier studyId,
            String idFilter, Boolean assignmentFilter) {

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = new DynamoDBQueryExpression<DynamoExternalIdentifier>();
        if (idFilter != null) {
            query.withRangeKeyCondition(IDENTIFIER, new Condition()
                    .withAttributeValueList(new AttributeValue().withS(idFilter))
                    .withComparisonOperator(BEGINS_WITH));
        }
        if (assignmentFilter != null) {
            addAssignmentFilter(query, assignmentFilter.booleanValue());
        }
        query.withHashKeyValues(new DynamoExternalIdentifier(studyId, null)); // no healthCode.
        return query;
    }

    private void addAssignmentFilter(DynamoDBQueryExpression<DynamoExternalIdentifier> query, boolean isAssigned) {
        String reservationStartTime = Long.toString(DateTimeUtils.currentTimeMillis()-lockDuration);
        
        ComparisonOperator healthCodeOp = (isAssigned) ? NOT_NULL : NULL;
        ComparisonOperator reservationOp = (isAssigned) ? GE : LT;
        ConditionalOperator op = (isAssigned) ? OR : AND;
        AttributeValue attrValue = new AttributeValue().withN(reservationStartTime);
        
        Condition healthCodeCondition = new Condition().withComparisonOperator(healthCodeOp);
        Condition reservationCondition = new Condition().withAttributeValueList(attrValue).withComparisonOperator(reservationOp);
        query.withQueryFilterEntry(HEALTH_CODE, healthCodeCondition);
        query.withQueryFilterEntry(RESERVATION, reservationCondition);
        query.withConditionalOperator(op);
    }
    
    /**
     * Save the record with a new timestamp IF the healthCode is empty and the reservation timestamp in the 
     * existing record was not set in the recent past (during the lockDuration).
     */
    private DynamoDBSaveExpression getReservationExpression(long newReservation) {
        AttributeValue reservationStartTime = new AttributeValue().withN(Long.toString(newReservation-lockDuration));
        
        ExpectedAttributeValue beforeReservation = new ExpectedAttributeValue()
                .withValue(reservationStartTime).withComparisonOperator(LT);
        
        ExpectedAttributeValue healthCodeNull = new ExpectedAttributeValue().withExists(false);
        
        Map<String, ExpectedAttributeValue> map = Maps.newHashMap();
        map.put(RESERVATION, beforeReservation);
        map.put(HEALTH_CODE, healthCodeNull);

        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
        saveExpression.withConditionalOperator(AND);
        saveExpression.setExpected(map);
        return saveExpression;
    }
    
    /**
     * Save the record with the user's healthCode IF the healthCode is not yet set. If calling code calls 
     * the reservation method first, this should not happen, but we do not prevent it.  
     */
    private DynamoDBSaveExpression getAssignmentExpression() {
        Map<String, ExpectedAttributeValue> map = Maps.newHashMap();
        map.put(HEALTH_CODE, new ExpectedAttributeValue().withExists(false));

        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
        saveExpression.setExpected(map);
        return saveExpression;
    }
    
    private ExternalIdentifierInfo createInfo(ExternalIdentifier id, long lockDuration) {
        // This calculation is done a couple of times, it does not need to be accurate to the millisecond
        long reservationStartTime = DateTimeUtils.currentTimeMillis() - lockDuration;
        boolean isAssigned = (id.getHealthCode() != null || id.getReservation() >= reservationStartTime);
        return new ExternalIdentifierInfo(id.getIdentifier(), isAssigned);
    }
    
    private <T> T last(List<T> items) {
        if (items != null && !items.isEmpty()) {
            return items.get(items.size()-1);
        }
        return null;
    }
}
