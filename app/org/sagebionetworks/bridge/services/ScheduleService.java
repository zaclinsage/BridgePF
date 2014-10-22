package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

public interface ScheduleService {

    public List<Schedule> getSchedules(Study study, User user);
    
    public List<Schedule> createSchedules(List<Schedule> schedules);
    
    public void deleteSchedules(SchedulePlan plan);
    
    public void deleteSchedules(Study study, User user);

}
