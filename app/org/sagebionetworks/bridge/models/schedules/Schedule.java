package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

public class Schedule {
    
    public enum Type {
        DATE,
        CRON
    }

    public enum ActivityType {
        SURVEY,
        TASK
    }
    
    private String studyUserCompoundKey;
    private String guid;
    private String label;
    private ActivityType activityType;
    private String activityRef;
    private Schedule.Type scheduleType;
    private String schedule;
    private Long expires;
    
    public Schedule() {
        
    }
    
    public Schedule(Schedule schedule) {
        setStudyUserCompoundKey(schedule.getStudyUserCompoundKey());
        setGuid(schedule.getGuid());
        setLabel(schedule.getLabel());
        setActivityType(schedule.getActivityType());
        setActivityRef(schedule.getActivityRef());
        setScheduleType(schedule.getScheduleType());
        setSchedule(schedule.getSchedule());
        setExpires(schedule.getExpires());
    }
    
    public String getStudyUserCompoundKey() {
        return studyUserCompoundKey;
    }
    public void setStudyUserCompoundKey(String studyUserCompoundKey) {
        this.studyUserCompoundKey = studyUserCompoundKey;
    }
    public void setStudyAndUser(Study study, User user) {
        setStudyUserCompoundKey(study.getKey()+":"+user.getId());
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public ActivityType getActivityType() {
        return activityType;
    }
    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }
    public String getActivityRef() {
        return activityRef;
    }
    public void setActivityRef(String activityRef) {
        this.activityRef = activityRef;
    }
    public Schedule.Type getScheduleType() {
        return scheduleType;
    }
    public void setScheduleType(Schedule.Type scheduleType) {
        this.scheduleType = scheduleType;
    }
    public String getSchedule() {
        return schedule;
    }
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
    public Long getExpires() {
        return expires;
    }
    public void setExpires(Long expires) {
        this.expires = expires;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((activityRef == null) ? 0 : activityRef.hashCode());
        result = prime * result + ((activityType == null) ? 0 : activityType.hashCode());
        result = prime * result + ((expires == null) ? 0 : expires.hashCode());
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((schedule == null) ? 0 : schedule.hashCode());
        result = prime * result + ((scheduleType == null) ? 0 : scheduleType.hashCode());
        result = prime * result + ((studyUserCompoundKey == null) ? 0 : studyUserCompoundKey.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Schedule other = (Schedule) obj;
        if (activityRef == null) {
            if (other.activityRef != null)
                return false;
        } else if (!activityRef.equals(other.activityRef))
            return false;
        if (activityType != other.activityType)
            return false;
        if (expires == null) {
            if (other.expires != null)
                return false;
        } else if (!expires.equals(other.expires))
            return false;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (schedule == null) {
            if (other.schedule != null)
                return false;
        } else if (!schedule.equals(other.schedule))
            return false;
        if (scheduleType != other.scheduleType)
            return false;
        if (studyUserCompoundKey == null) {
            if (other.studyUserCompoundKey != null)
                return false;
        } else if (!studyUserCompoundKey.equals(other.studyUserCompoundKey))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Schedule [studyUserCompoundKey=" + studyUserCompoundKey + ", guid=" + guid + ", label=" + label
                + ", activityType=" + activityType + ", activityRef=" + activityRef + ", scheduleType=" + scheduleType
                + ", schedule=" + schedule + ", expires=" + expires + "]";
    }
    
}
