package com.priye.pcbook.service;

import com.priye.pcbook.pb.*;
import com.priye.pcbook.pb.Laptop;
import io.grpc.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class InMemoryLaptopStore implements LaptopStore {
    private static final Logger logger = Logger.getLogger(InMemoryLaptopStore.class.getName());

    //store the laptops with key is laptop id string and value is laptop itself
    private ConcurrentMap<String, Laptop> data; // thread-safe hashmap

    public InMemoryLaptopStore() {
        data = new ConcurrentHashMap<>(0);
    }

    @Override
    public void Save(Laptop laptop) throws Exception {
        if (data.containsKey(laptop.getId())) {
            throw new AlreadyExistsException("laptop ID already exists");
        }

        // deep copy
        Laptop other = laptop.toBuilder().build();//other is whole json
        data.put(other.getId(), other);
    }

    @Override
    public Laptop Find(String id) {
        if (!data.containsKey(id)) {
            return null;
        }

        // deep copy
        Laptop other = data.get(id).toBuilder().build();
        return other;
    }

    @Override
    public void Search(Filter filter, LaptopStream stream) {
        for (Map.Entry<String, Laptop> entry : data.entrySet()) {
            Laptop laptop = entry.getValue().toBuilder().build();
            if (isQualified(filter, laptop)) {
                stream.Send(laptop);
            }
        }
    }

    private boolean isQualified(Filter filter, Laptop laptop) {
        if (laptop.getPriceUsd() > filter.getMaxPriceUsd()) {
            return false;
        }

        if (laptop.getCpu().getNumberCores() < filter.getMinCpuCores()) {
            return false;
        }

        if (laptop.getCpu().getMinGhz() < filter.getMinCpuGhz()) {
            return false;
        }

        //toBit() for convert memory size into bits
        if (toBit(laptop.getRam()) < toBit(filter.getMinRam())) {
            return false;
        }

        return true;
    }
    private long toBit(Memory memory) {
        long value = memory.getValue();

        switch (memory.getUnit()) {
            case BIT:
                return value;
            case BYTE:
                return value << 3;//1 BYTE=8 BIT = 2^3 BIT
            case KILOBYTE:
                return value << 13;//1 KILOBYTE=1024 BYTE= 2^10 BYTE=2^13 BITS
            case MEGABYTE:
                return value << 23;
            case GIGABYTE:
                return value << 33;
            case TERABYTE:
                return value << 43;
            default:
                return 0;
        }
    }

}
