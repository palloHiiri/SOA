package com.fuzis.tickets.dto;

public class SchemeResponse {
    private Integer id;
    private String code;
    private String name;
    private String storageKey;
    private Integer version;
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
