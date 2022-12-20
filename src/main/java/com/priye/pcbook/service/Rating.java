package com.priye.pcbook.service;

//this class will contain the rating metrices of given laptop
public class Rating {
    private int count; //integer count to store number of times a laptop is rated
    private double sum; //to store sum of all rated scores

    public Rating(int count, double sum) {
        this.count = count;
        this.sum = sum;
    }

    public int getCount() {
        return count;
    }

    public double getSum() {
        return sum;
    }

    public static Rating add(Rating r1, Rating r2) {
        return new Rating(r1.count + r2.count, r1.sum + r2.sum);
    }
}
