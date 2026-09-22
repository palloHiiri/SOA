package com.fuzis.tickets.dto;

public class VenueReference {
    private long id;
    private String name;
    private int trainSetId;

    public VenueReference() {
    }
    public VenueReference(long id, String name, int trainSetId) {
        this.id = id;
        this.name = name;
        this.trainSetId = trainSetId;
    }
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getTrainSetId() {
        return trainSetId;
    }
    public void setTrainSetId(int trainSetId) {
        this.trainSetId = trainSetId;
    }
}
