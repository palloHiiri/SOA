package com.fuzis.tickets.dto;

public class CarriageResponse {
    private Integer id;
    private String carriageNumber;
    private Integer position;
    private CarriageTypeResponse carriageType;
    private String serialNumber;
    private String inventoryNumber;
    private SchemeResponse scheme;
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCarriageNumber() { return carriageNumber; }
    public void setCarriageNumber(String carriageNumber) { this.carriageNumber = carriageNumber; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public CarriageTypeResponse getCarriageType() { return carriageType; }
    public void setCarriageType(CarriageTypeResponse carriageType) { this.carriageType = carriageType; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getInventoryNumber() { return inventoryNumber; }
    public void setInventoryNumber(String inventoryNumber) { this.inventoryNumber = inventoryNumber; }
    public SchemeResponse getScheme() { return scheme; }
    public void setScheme(SchemeResponse scheme) { this.scheme = scheme; }
}
