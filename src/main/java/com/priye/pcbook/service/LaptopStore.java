package com.priye.pcbook.service;

import com.priye.pcbook.pb.*;
import io.grpc.Context;

public interface LaptopStore {
    void Save(Laptop laptop) throws Exception;
    Laptop Find(String id);
    void Search(Filter filter, LaptopStream stream);
}
