package com.thinkbiganalytics.nifi.provenance.model;

/*-
 * #%L
 * thinkbig-nifi-provenance-model
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Track the FeedFlowfile and any of its child flow files as it moves through NiFi along with pointers to help calculate the event timing data and indication as to when a child flow file or this
 * entire feed flow file is complete
 */
public class FeedFlowFile implements Serializable {


    private static final long serialVersionUID = 6464904199959374630L;

    /**
     * The ID of the Flow File
     */
    private String id;


    /**
     * Flag to mark this flowfile as a Stream.
     */
    private boolean isStream;

    /**
     * The feed associated with the flow file.  This includes the category.feedname
     */
    private String feedName;

    /**
     * The process group id for the feed
     */
    private String feedProcessGroupId;

    /**
     * When new flow files are created they get associated back to the feedflow file in this collection
     */
    private Set<String> activeChildFlowFiles;

    /**
     * reference to all child flow file ids for this feed flow file
     * This is used when clearing the cache
     */
    private Set<String> childFlowFiles;


    /**
     * The First Event in this flow file
     */
    private Long firstEventId;

    private Long firstEventStartTime;

    private String firstEventProcessorId;


    private Long lastEventId;

    private String lastEventProcessorId;

    private Long lastEventTime;

    private AtomicInteger failedEvents = new AtomicInteger(0);


    /**
     * Track when an event comes through that is attached to a flow file to mark the start of that flow file.
     */
    private Set<String> flowfilesStarted;


    /**
     * flag to mark if this flow file is complete.  This does not mean the entire feed is complete as other "activeChildFlowFiles" could still be running
     */
    private boolean isCurrentFlowFileComplete;

    /**
     * Map of the flowfile to last event time.
     * This will be used to determine the next flow file start time
     */
    private Map<String, Long> flowFileLastEventTime;


    /**
     * When a flowfile is cloned or forked creating other child flow files it needs to store that event time the flow was cloned to use as the start time for that next event
     */
    private Map<String, Long> childFlowFileStartTimes;

    /**
     * Map the flow to its parent to help in processing the previous event time.
     */
    private Map<String, String> flowFileIdToParentFlowFileId;

    private boolean isBuiltFromMapDb;


    public FeedFlowFile(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Long getFirstEventId() {
        return firstEventId;
    }

    public Long getFirstEventStartTime() {
        return firstEventStartTime;
    }

    public String getFirstEventProcessorId() {
        return firstEventProcessorId;
    }

    public boolean isStream() {
        return isStream;
    }

    public void setStream(boolean stream) {
        isStream = stream;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(String feedName) {
        this.feedName = feedName;
    }

    public String getFeedProcessGroupId() {
        return feedProcessGroupId;
    }

    public void setFeedProcessGroupId(String feedProcessGroupId) {
        this.feedProcessGroupId = feedProcessGroupId;
    }

    public Set<String> getActiveChildFlowFiles() {
        return activeChildFlowFiles;
    }

    public Set<String> getChildFlowFiles() {
        return childFlowFiles;
    }

    public Long getLastEventId() {
        return lastEventId;
    }

    public String getLastEventProcessorId() {
        return lastEventProcessorId;
    }

    public Long getLastEventTime() {
        return lastEventTime;
    }


    /**
     * flag to determine if this was build from the persistent cache
     */
    public boolean isBuiltFromMapDb() {
        return isBuiltFromMapDb;
    }

    public void setBuiltFromMapDb(boolean builtFromMapDb) {
        isBuiltFromMapDb = builtFromMapDb;
    }

    /**
     * When an event fails in the flow increment the counter so we can notify Operations Manager that it has failed
     */
    public void incrementFailedEvents() {
        failedEvents.incrementAndGet();
    }

    /**
     * Determine if the Flow File has failed or not
     *
     * @return true if the flow file has failed along the way, false if its successful
     */
    public boolean hasFailedEvents() {
        return failedEvents.get() > 0;
    }


    /**
     * Mark the first event attributes
     */
    public void setFirstEvent(ProvenanceEventRecordDTO event) {
        firstEventId = event.getEventId();
        firstEventStartTime = event.getStartTime().getMillis();
        firstEventProcessorId = event.getComponentId();
    }


    public void addEvent(ProvenanceEventRecordDTO event) {
        Long previousEventTime = getPreviousEventTime(event.getFlowFileUuid());
        if (previousEventTime != null) {
            event.setStartTime(new DateTime(previousEventTime));
        } else {
            event.setStartTime(event.getEventTime());
        }
        lastEventId = event.getEventId();
        lastEventTime = event.getEventTime().getMillis();
        lastEventProcessorId = event.getComponentId();
        event.setEventDuration(event.getEventTime().getMillis() - event.getStartTime().getMillis());
        registerLastEventTime(event);

    }

    /**
     * Is this feed and all the child flow files complete
     */
    public boolean isFeedComplete() {
        return isCurrentFlowFileComplete && (activeChildFlowFiles == null || (activeChildFlowFiles != null && activeChildFlowFiles.isEmpty()));
    }

    /**
     * Determine if the FlowFile has the Kylo Feed information assigned to it
     */
    public boolean hasFeedInformationAssigned() {
        return getFeedName() != null && getFeedProcessGroupId() != null;
    }


    /**
     * If the event is a "DROP" event that mark the correct flow file as complete.
     */
    public void checkAndMarkComplete(ProvenanceEventRecordDTO event) {
        if ("DROP".equalsIgnoreCase(event.getEventType())) {
            if (event.getFlowFileUuid().equals(this.getId())) {
                isCurrentFlowFileComplete = true;
            } else {
                activeChildFlowFiles.remove(event.getFlowFileUuid());
            }
        }
    }

    public void addChildFlowFile(String childFlowFileId) {
        if (activeChildFlowFiles == null) {
            activeChildFlowFiles = new HashSet<>();
        }
        if (childFlowFiles == null) {
            childFlowFiles = new HashSet<>();
        }
        activeChildFlowFiles.add(childFlowFileId);
        childFlowFiles.add(childFlowFileId);
    }

    /**
     * Check to see if the event is the start of the FeedFlowFile.  This indicates the start of the job
     */
    public boolean isFirstEvent(ProvenanceEventRecordDTO eventRecordDTO) {
        return eventRecordDTO.getEventId().equals(getFirstEventId());
    }


    public boolean checkIfEventStartsTheFlowFile(ProvenanceEventRecordDTO eventRecordDTO) {
        if (flowfilesStarted == null || (flowfilesStarted != null && flowfilesStarted.contains(eventRecordDTO.getFlowFileUuid()))) {
            if (flowfilesStarted == null) {
                flowfilesStarted = new HashSet<>();
            }
            flowfilesStarted.add(eventRecordDTO.getFlowFileUuid());
            eventRecordDTO.setStartOfFlowFile(true);
        }
        return eventRecordDTO.isStartOfFlowFile();
    }

    public Long getPreviousEventTime(String flowfileId) {

        if (flowFileLastEventTime != null && flowFileLastEventTime.containsKey(flowfileId)) {
            return flowFileLastEventTime.get(flowfileId);
        } else if (childFlowFileStartTimes != null && childFlowFileStartTimes.containsKey(flowfileId)) {
            return childFlowFileStartTimes.get(flowfileId);
        } else if (flowFileIdToParentFlowFileId != null && flowFileIdToParentFlowFileId.containsKey(flowfileId) && !flowfileId.equals(flowFileIdToParentFlowFileId.get(flowfileId))) {
            return getPreviousEventTime(flowFileIdToParentFlowFileId.get(flowfileId));
        } else {
            return null;
        }
    }

    public void registerLastEventTime(ProvenanceEventRecordDTO eventRecordDTO) {
        if (flowFileLastEventTime == null) {
            flowFileLastEventTime = new HashMap<>();
        }
        flowFileLastEventTime.put(eventRecordDTO.getFlowFileUuid(), eventRecordDTO.getEventTime().getMillis());

    }

    public void assignFlowFileToParent(String childFlowFileId, String parentFlowFileId) {
        if (flowFileIdToParentFlowFileId == null) {
            flowFileIdToParentFlowFileId = new HashMap<>();
        }
        flowFileIdToParentFlowFileId.put(childFlowFileId, parentFlowFileId);
    }

    public void assignChildFlowFileStartTime(String flowFileId, Long eventTime) {
        if (childFlowFileStartTimes == null) {
            childFlowFileStartTimes = new HashMap<>();
        }
        childFlowFileStartTimes.put(flowFileId, eventTime);
    }

    public Long calculateJobDuration(ProvenanceEventRecordDTO event) {
        Long jobTime = null;
        Long firstEventTime = getFirstEventStartTime();
        if (firstEventTime != null) {
            jobTime = event.getEventTime().getMillis() - firstEventTime;
        }
        return jobTime;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FeedFlowFile{");
        sb.append("id='").append(id).append('\'');
        sb.append(", isStream=").append(isStream);
        sb.append(", feedName='").append(feedName).append('\'');
        sb.append(", activeFlowFiles ='").append(activeChildFlowFiles != null ? activeChildFlowFiles.size() : "null").append('\'');
        sb.append('}');
        return sb.toString();
    }
}
