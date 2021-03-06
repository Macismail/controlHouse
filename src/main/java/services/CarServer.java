package services;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import java.util.logging.Logger;
import org.mycompany.example.car.AlarmStatus;
import org.mycompany.example.car.CarGrpc;
import org.mycompany.example.car.CarStatus;
import org.mycompany.example.car.Check;
import org.mycompany.example.car.WindowsStatus;
import org.mycompany.example.car.DrsStatus;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class CarServer {

   private static final Logger logger = Logger.getLogger(CarServer.class.getName());

   /* The port on which the server should run */
   private int port = 50051;
   private Server server;

   private void start() throws Exception {
      server = ServerBuilder.forPort(port)
              .addService(new CarImpl())
              .build()
              .start();
      JmDNSRegistrationHelper helper = new JmDNSRegistrationHelper("Car", "_car._udp.local.", "", port);
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
    * Await termination on the main thread since the grpc library uses
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

      private int percent = 0;
      private List<Check> checks;

      public CarImpl() {
         String name = "Car";
         String serviceType = "_car._udp.local.";
         checks = new ArrayList<Check>();
            Check petrol = Check.newBuilder().setLevel(30).build();
            Check oil = Check.newBuilder().setLevel(65).build();
            Check water = Check.newBuilder().setLevel(83).build();
            checks.add(petrol);
            checks.add(oil);
            checks.add(water);
      }

      @Override
      public void closeWindows(com.google.protobuf.Empty request,
              io.grpc.stub.StreamObserver<org.mycompany.example.car.WindowsStatus> responseObserver) {
         Timer t = new Timer();
         t.schedule(new RemindTask(responseObserver), 0, 1000);
      }

      class RemindTask extends TimerTask {

         StreamObserver<WindowsStatus> o;

         public RemindTask(StreamObserver<WindowsStatus> j) {
            o = j;
         }

         @Override
         public void run() {
            if (percent < 100) {
               percent += 20;
               WindowsStatus status = WindowsStatus.newBuilder().setPercentage(percent).build();
               o.onNext(status);
            } else {
               o.onCompleted();
               this.cancel();
            }
         }
      }

      @Override
      public void lockDoors(com.google.protobuf.Empty request,
              io.grpc.stub.StreamObserver<org.mycompany.example.car.DrsStatus> responseObserver) {
         responseObserver.onNext(DrsStatus.newBuilder().setLock("Locked !!!").build());
         responseObserver.onCompleted();
      }

      @Override
      public void switchAlarm(com.google.protobuf.Empty request,
              io.grpc.stub.StreamObserver<org.mycompany.example.car.AlarmStatus> responseObserver) {
         responseObserver.onNext(AlarmStatus.newBuilder().setAlarm("Switched On !!!").build());
         responseObserver.onCompleted();
      }
      
      @Override
      public void carCheck(com.google.protobuf.Empty request,
              io.grpc.stub.StreamObserver<org.mycompany.example.car.CarStatus> responseObserver) {
         responseObserver.onNext(CarStatus.newBuilder().addAllChecks(checks).build());
         responseObserver.onCompleted();
      }

   }
}
