package com.priye.pcbook.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryRatingStore implements RatingStore {
    private ConcurrentMap<String, Rating> data;

    public InMemoryRatingStore() {
        data = new ConcurrentHashMap<>();
    }

    @Override
    public Rating Add(String laptopID, double score) {
        //we have to update laptop rating atomically because there might be many request to rate the same laptop at the same time
        //to do that, we use the merge() of the concurrent map
        //Basically this function takes a laptop id key , A rating value to be used
        //if the key is not associated with any value before which should be Rating(1,score) in our case and
        // a remapping function to update the value of existing key
        return data.merge(laptopID, new Rating(1, score), Rating::add);
        //let's write a unit test , where we concurrently call ratingStore.Add from multiple threads
    }
}
