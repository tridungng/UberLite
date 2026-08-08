package com.uberlite.discountspromotions.domain;

public class DiscountInfo {
    public String riderId;
    public double discountPercentage;
    public String promoCode;

    public DiscountInfo(String riderId, double discountPercentage, String promoCode) {
        this.riderId = riderId;
        this.discountPercentage = discountPercentage;
        this.promoCode = promoCode;
    }
}
