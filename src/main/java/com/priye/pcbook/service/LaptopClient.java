package com.priye.pcbook.service;

import com.priye.pcbook.pb.*;
import com.priye.pcbook.pb.LaptopServiceGrpc.LaptopServiceBlockingStub;
import com.priye.pcbook.sample.Generator;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
public class LaptopClient {

    private static final Logger logger = Logger.getLogger(LaptopClient.class.getName());

    private final ManagedChannel channel;//we need channel for connection b/w client & server
    private final LaptopServiceBlockingStub blockingStub;//we also need blocking stub in order to call unary rpc
    private final LaptopServiceGrpc.LaptopServiceStub asyncStub;//asynchronous call
    public LaptopClient(String host, int port) {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()//use plain text for now( we are not using secured connection yet)
                .build();

        blockingStub = LaptopServiceGrpc.newBlockingStub(channel);
        asyncStub = LaptopServiceGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void createLaptop(Laptop laptop) {
        CreateLaptopRequest request = CreateLaptopRequest.newBuilder().setLaptop(laptop).build();
        CreateLaptopResponse response;

        try {
            response=blockingStub.createLaptop(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
                // not a big deal
                logger.info("laptop ID already exists");
                return;
            }
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        }

        logger.info("laptop created with ID: " + response.getId());

    }

    public void searchLaptop(Filter filter) {
        logger.info("search started");

        SearchLaptopRequest request = SearchLaptopRequest.newBuilder().setFilter(filter).build();

        try {
            Iterator<SearchLaptopResponse> iterator = blockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .searchLaptop(request);

            while (iterator.hasNext()) {
                SearchLaptopResponse response = iterator.next();
                Laptop laptop = response.getLaptop();
                logger.info("- found: " + laptop.getId());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "request failed: " + e.getMessage());
            return;
        }

        logger.info("search completed");
    }

    public static void testSearchLaptop(LaptopClient client, Generator generator) {
        for (int i = 0; i < 10; i++) {
            Laptop laptop = generator.NewLaptop();
            client.createLaptop(laptop);
        }

        Memory minRam = Memory.newBuilder()
                .setValue(8)
                .setUnit(Memory.Unit.GIGABYTE)
                .build();
        Filter filter = Filter.newBuilder()
                .setMaxPriceUsd(3000)
                .setMinCpuCores(4)
                .setMinCpuGhz(2.5)
                .setMinRam(minRam)
                .build();
        client.searchLaptop(filter);
    }

    public void uploadImage(String laptopID, String imagePath) throws InterruptedException {
        //stub is asynchronous which means that send request part & receive response part run asynchronously bcoz of this we
        //need to use CountDownLatch() to wait until the whole process is completed.
        //Here, we juts use a count of 1 bcoz we only need to wait for the response thread
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<UploadImageRequest> requestObserver = asyncStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .uploadImage(new StreamObserver<UploadImageResponse>() {
                    @Override
                    public void onNext(UploadImageResponse response) {
                        logger.info("receive response from the server:\n" + response);
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.log(Level.SEVERE, "upload failed: " + t);
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("image uploaded");
                        finishLatch.countDown();
                    }
                });

        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(imagePath);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "cannot read image file: " + e.getMessage());
            return;
        }

        String imageType = imagePath.substring(imagePath.lastIndexOf("."));
        ImageInfo info = ImageInfo.newBuilder().setLaptopId(laptopID).setImageType(imageType).build();
        UploadImageRequest request = UploadImageRequest.newBuilder().setInfo(info).build();

        try {
            requestObserver.onNext(request);
            logger.info("sent image info:\n" + info);

            byte[] buffer = new byte[1024];
            while (true) {
                int n = fileInputStream.read(buffer);
                if (n <= 0) {
                    break;
                }

                if (finishLatch.getCount() == 0) {
                    return;
                }

                request = UploadImageRequest.newBuilder()
                        .setChunkData(ByteString.copyFrom(buffer, 0, n))
                        .build();
                requestObserver.onNext(request);
                logger.info("sent image chunk with size: " + n);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "unexpected error: " + e.getMessage());
            requestObserver.onError(e);
            return;
        }

        requestObserver.onCompleted();

        //we call finishLatch.await() to wait for the response thread to finish
        //here, we wait only for atmost 1 minute which is more than enough bcoz above we set the deadline of the call to be 5 seconds
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            logger.warning("request cannot finish within 1 minute");
        }
    }

    public static void main(String[] args) throws InterruptedException{
        LaptopClient client = new LaptopClient("0.0.0.0", 9090);
        Generator generator = new Generator();
        Laptop laptop=generator.NewLaptop();
        //Laptop laptop=generator.NewLaptop().toBuilder().setId("Monali").build();//invalid laptop id
        //Laptop laptop=generator.NewLaptop().toBuilder().setId("").build();

        try {
            client.createLaptop(laptop);
            //LaptopClient.testSearchLaptop(client,generator);
            //Laptop laptop=generator.NewLaptop();
            //client.createLaptop(laptop);
            //client.uploadImage(laptop.getId(),"C:/Users/ranjan_p/pcbook/tmp/img.png");//C:\Users\ranjan_p\pcbook\tmp
        } finally {
            client.shutdown();
        }
    }
}
