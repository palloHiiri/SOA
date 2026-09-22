package com.fuzis.tickets.dto;

public class VenueResponse {
    private long id;
    private String name;
    private int trainSetId;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTrainSetId() { return trainSetId; }
    public void setTrainSetId(int trainSetId) { this.trainSetId = trainSetId; }
}
