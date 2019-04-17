package org.mycompany.services;

import org.jpdna.grpchello.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import io.grpc.stub.StreamObserver;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import org.mycompany.example.car.CarGrpc;
import org.mycompany.example.car.CarStatus;
import org.mycompany.example.car.DrsStatus;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class CarServer {

   private static final Logger logger = Logger.getLogger(CarServer.class.getName());

   /* The port on which the server should run */
   private int port = 50021;
   private Server server;

   private void start() throws Exception {
      server = ServerBuilder.forPort(port)
              .addService(new CarImpl())
              .build()
              .start();
      JmDNSRegistrationHelper helper = new JmDNSRegistrationHelper("Car", "_car_udp.local.", "", port);
      logger.info("Server started, listening on " + port);
      Runtime.getRuntime().addShutdownHook(new Thread() {
         @Override
         public void run() {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            CarServer.this.stop();
            System.err.println("*** server shut down");
         }
      });
   }

   private void stop() {
      if (server != null) {
         server.shutdown();
      }
   }

   /**
    * Await termination on the main thread since the GRPC library uses
    * daemon threads.
    */
   private void blockUntilShutdown() throws InterruptedException {
      if (server != null) {
         server.awaitTermination();
      }
   }
   
    /**
    * Main launches the server from the command line.
    */
   public static void main(String[] args) throws Exception {
      final CarServer server = new CarServer();
      server.start();
      server.blockUntilShutdown();
   }

   private class CarImpl extends CarGrpc.CarImplBase {

      private int winlevel = 0;
      private String locked = "Locked!!!";

      public CarImpl() {
         String name = "Car";
         String serviceType = "_car_udp.local.";
      }
      
      @Override
      public void close(com.google.protobuf.Empty request,
              io.grpc.stub.StreamObserver<org.mycompany.example.car.CarStatus> responseObserver) {
         Timer t = new Timer();
         t.schedule(new RemindTask(responseObserver), 0, 1000);
      }

      @Override
      public void getStatus(com.google.protobuf.Empty request,
              io.grpc.stub.StreamObserver<org.mycompany.example.car.CarStatus> responseObserver) {
         responseObserver.onNext(CarStatus.newBuilder().setPercentage(winlevel).build());
         responseObserver.onCompleted();
      }
      
      public void LockDoors(com.google.protobuf.Empty request, 
              io.grpc.stub.StreamObserver<org.mycompany.example.car.DrsStatus> responseObserver) {
         DrsStatus resp = DrsStatus.newBuilder().setLock(locked).build();
         responseObserver.onNext(resp);
         responseObserver.onCompleted();
      }
      
      class RemindTask extends TimerTask {

         StreamObserver<CarStatus> stmOb;

         public RemindTask(StreamObserver<CarStatus> j) {
            stmOb = j;
         }

         @Override
         public void run() {
            
            if (winlevel < 100) {
               winlevel += 10;
               CarStatus status = CarStatus.newBuilder().setPercentage(winlevel).build();
               stmOb.onNext(status);
            } else {
               stmOb.onCompleted();
               this.cancel();
            }
         }
      }

   }
   
}