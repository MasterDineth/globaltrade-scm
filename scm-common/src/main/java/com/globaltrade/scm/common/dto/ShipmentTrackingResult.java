package com.globaltrade.scm.common.dto;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import java.io.Serializable;
import java.time.LocalDateTime;
public class ShipmentTrackingResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private String trackingNumber;
    private ShipmentStatus status;
    private String originCountry;
    private String destinationCountry;
    private LocalDateTime estimatedDelivery;
    private LocalDateTime actualDelivery;
    private String carrierName;
    private String vendorName;
    public ShipmentTrackingResult() {
    }
    public ShipmentTrackingResult(String trackingNumber, ShipmentStatus status, String originCountry,
                                   String destinationCountry, LocalDateTime estimatedDelivery,
                                   LocalDateTime actualDelivery, String carrierName, String vendorName) {
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.originCountry = originCountry;
        this.destinationCountry = destinationCountry;
        this.estimatedDelivery = estimatedDelivery;
        this.actualDelivery = actualDelivery;
        this.carrierName = carrierName;
        this.vendorName = vendorName;
    }
    public String getTrackingNumber() {
        return trackingNumber;
    }
    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }
    public ShipmentStatus getStatus() {
        return status;
    }
    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }
    public String getOriginCountry() {
        return originCountry;
    }
    public void setOriginCountry(String originCountry) {
        this.originCountry = originCountry;
    }
    public String getDestinationCountry() {
        return destinationCountry;
    }
    public void setDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
    }
    public LocalDateTime getEstimatedDelivery() {
        return estimatedDelivery;
    }
    public void setEstimatedDelivery(LocalDateTime estimatedDelivery) {
        this.estimatedDelivery = estimatedDelivery;
    }
    public LocalDateTime getActualDelivery() {
        return actualDelivery;
    }
    public void setActualDelivery(LocalDateTime actualDelivery) {
        this.actualDelivery = actualDelivery;
    }
    public String getCarrierName() {
        return carrierName;
    }
    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }
    public String getVendorName() {
        return vendorName;
    }
    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }
}
