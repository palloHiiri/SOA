package com.fuzis.tickets.dto;

public class TrainSetResponse {
    private int id;
    private String code;
    private String name;
    private int buildNumber;
    private String technicalName;
    private String description;
    private long snapshotVersion;
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getBuildNumber() { return buildNumber; }
    public void setBuildNumber(int buildNumber) { this.buildNumber = buildNumber; }
    public String getTechnicalName() { return technicalName; }
    public void setTechnicalName(String technicalName) { this.technicalName = technicalName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(long snapshotVersion) { this.snapshotVersion = snapshotVersion; }
}
