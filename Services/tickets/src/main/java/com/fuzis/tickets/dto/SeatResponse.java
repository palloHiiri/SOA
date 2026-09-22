package com.fuzis.tickets.dto;

public class SeatResponse {
    private Integer id;
    private String seatNumber;
    private double x;
    private double y;
    private double rotation;
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getSeatNumber() {
        return seatNumber;
    }
    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }
    public double getX() {
        return x;
    }
    public void setX(double x) {
        this.x = x;
    }
    public double getY() {
        return y;
    }
    public void setY(double y) {
        this.y = y;
    }
    public double getRotation() {
        return rotation;
    }
    public void setRotation(double rotation) {
        this.rotation = rotation;
    }
}
