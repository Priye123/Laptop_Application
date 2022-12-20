package com.priye.pcbook.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

//this class listen to grpc request & call laptopservice to serve the request
public class LaptopServer {
    private static final Logger logger = Logger.getLogger(LaptopServer.class.getName());

    private int port=0;
    private final Server server;//grpc server

    //starting grpc server at particular port
    public LaptopServer(int port, LaptopStore laptopStore,ImageStore imageStore,RatingStore ratingStore) {
        this(ServerBuilder.forPort(port), port, laptopStore,imageStore,ratingStore);
    }

    //we will use this constructor when we write testcases
    public LaptopServer(ServerBuilder serverBuilder, int port, LaptopStore laptopStore,ImageStore imageStore,RatingStore ratingStore) {
        this.port = port;
        LaptopService laptopService = new LaptopService(laptopStore,imageStore,ratingStore);
        server = serverBuilder.addService(laptopService) //add laptop service to serverBuilder
                .build();//call build() to create a grpc server
    }

    public void start() throws IOException {
        server.start();
        logger.info("server started on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("shut down gRPC server because JVM shuts down");
                try {
                    LaptopServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("server shut down");
            }
        });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    //block main thread until the server shuts down bcoz the grpc server uses daemon thread
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

//    public static SslContext loadTLSCredentials() throws SSLException {
//        File serverCertFile = new File("cert/server-cert.pem");
//        File serverKeyFile = new File("cert/server-key.pem");
//        File clientCACertFile = new File("cert/ca-cert.pem");
//
//        SslContextBuilder ctxBuilder = SslContextBuilder.forServer(serverCertFile, serverKeyFile)
//                .clientAuth(ClientAuth.REQUIRE)
//                .trustManager(clientCACertFile);
//
//        return GrpcSslContexts.configure(ctxBuilder).build();
//    }

    public static void main(String[] args) throws InterruptedException, IOException {
        InMemoryLaptopStore laptopStore = new InMemoryLaptopStore();
       DiskImageStore imageStore = new DiskImageStore("img");
        InMemoryRatingStore ratingStore = new InMemoryRatingStore();

        //SslContext sslContext = LaptopServer.loadTLSCredentials();
        LaptopServer server = new LaptopServer(9090, laptopStore,imageStore,ratingStore);
        server.start();
        server.blockUntilShutdown();
    }
}
