package com.priye.pcbook.service;

public interface RatingStore {
    Rating Add(String laptopID, double score); //returns the updated rating of the laptop
}