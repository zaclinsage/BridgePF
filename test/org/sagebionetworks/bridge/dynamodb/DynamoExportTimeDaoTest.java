package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ExportTime;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoExportTimeDaoTest {
    private static final String STUDY_ID = "test-study-id";

    private DynamoDBMapper mockMapper;

    private DynamoExportTimeDao dynamoExportTimeDao;

    @Before
    public void setup() {
        ExportTime mockExportTime = ExportTime.create();
        mockExportTime.setStudyId(STUDY_ID);

        mockMapper = mock(DynamoDBMapper.class);
        when(mockMapper.load(any())).thenReturn(mockExportTime);

        dynamoExportTimeDao = new DynamoExportTimeDao();
        dynamoExportTimeDao.setDdbMapper(mockMapper);
    }

    @Test
    public void testGetExportTime() {
        // execute
        dynamoExportTimeDao.getExportTime(STUDY_ID);
        ArgumentCaptor<DynamoExportTime> captor = ArgumentCaptor.forClass(DynamoExportTime.class);
        verify(mockMapper).load(captor.capture());

        DynamoExportTime retDynamoExportTime = captor.getValue();
        assertEquals(STUDY_ID, retDynamoExportTime.getStudyId());
    }

    @Test(expected = EntityNotFoundException.class)
    public void testGetExportTimeWithNullStudyId() {
        when(mockMapper.load(any())).thenReturn(null);

        // execute
        dynamoExportTimeDao.getExportTime(STUDY_ID);
        verify(mockMapper).load(eq(STUDY_ID));
    }

    @Test
    public void testDeleteExportTime() {
        // execute
        dynamoExportTimeDao.deleteStudyInfo(STUDY_ID);

        ArgumentCaptor<DynamoExportTime> captor = ArgumentCaptor.forClass(DynamoExportTime.class);
        verify(mockMapper).load(captor.capture());
        verify(mockMapper).delete(any());

        DynamoExportTime retDynamoExportTime = captor.getValue();
        assertEquals(STUDY_ID, retDynamoExportTime.getStudyId());
    }

    @Test
    public void testDeleteExportTimeWithNullStudyId() {
        when(mockMapper.load(any())).thenReturn(null);

        // execute
        dynamoExportTimeDao.deleteStudyInfo(STUDY_ID);
        ArgumentCaptor<DynamoExportTime> captor = ArgumentCaptor.forClass(DynamoExportTime.class);
        verify(mockMapper).load(captor.capture());
        verify(mockMapper, times(0)).delete(any());

        DynamoExportTime retDynamoExportTime = captor.getValue();
        assertEquals(STUDY_ID, retDynamoExportTime.getStudyId());
    }
}
