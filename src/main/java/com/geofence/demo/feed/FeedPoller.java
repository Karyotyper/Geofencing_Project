package com.geofence.demo.feed;

import com.geofence.demo.entity.Location;
import com.geofence.demo.entity.VehiclePositionFeed;
import com.google.transit.realtime.GtfsRealtime;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.io.*;

@Component
@EnableScheduling
public class FeedPoller {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${rtd.username}")
    private String rtdUsername;

    @Value("${rtd.password}")
    private String rtdPassword;

    @Value("${message.topic.name}")
    private String topicName;

    double baseLat = 39.74494924078947;
    double baseLon = -104.99457840680851;

    @Autowired
    KafkaTemplate<String, VehiclePositionFeed> kafkaTemplate;

    public void sendMessage(VehiclePositionFeed message) {
        ListenableFuture<SendResult<String, VehiclePositionFeed>> future = kafkaTemplate.send(topicName, message);

        future.addCallback(
                new ListenableFutureCallback<SendResult<String, VehiclePositionFeed>>() {

                    @Override
                    public void onSuccess(SendResult<String, VehiclePositionFeed> stringVehiclePositionFeedSendResult) {
                        logger.info(
                                "Sent message=[{}] with offset=[{}]", message, stringVehiclePositionFeedSendResult.getRecordMetadata().offset());
                    }


                    @Override
                    public void onFailure(Throwable ex) {
                        logger.info("Unable to send message=[{}] due to : {}", message, ex.getMessage());
                    }
                });
    }

    @Scheduled(cron = "*/10 * * * * *")
    private void getBusPositions() {
        try {

            logger.info("Getting latest vehicle positions from RTD feed.");

            // get latest vehicle positions
            String user = "RTDgtfsRT";
            String pass = "realT!m3Feed";
            String url = "http://www.rtd-denver.com/google_sync/VehiclePosition.pb";

            Unirest.get(url)
                    .basicAuth(user, pass)
                    .thenConsume(rawResponse -> {
                        try {
                            InputStream stream = rawResponse.getContent();
                            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(stream);
                            for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
                                GtfsRealtime.VehiclePosition vehiclePosition = entity.getVehicle();

                                String alert = "NO ALERT";
                                if (entity.hasAlert()) {
                                    alert =  entity.getAlert().getCause().toString();
                                }

                                String route = entity.getVehicle().getTrip().getRouteId();

                                Location location = new Location();
                                location.setLat(vehiclePosition.getPosition().getLatitude());
                                location.setLon(vehiclePosition.getPosition().getLongitude());


                                VehiclePositionFeed busPosition = new VehiclePositionFeed();

                                busPosition.setId(vehiclePosition.getVehicle().getId());
                                busPosition.setTimestamp(vehiclePosition.getTimestamp() * 1000); // convert seconds to Avro-friendly millis
                                busPosition.setLocation(location);
                                busPosition.setBearing(vehiclePosition.getPosition().getBearing());
//                                busPosition.se
                                System.out.println("PING: " + busPosition.getId());


                                Message<VehiclePositionFeed> message = MessageBuilder
                                        .withPayload(busPosition)
                                        .setHeader(KafkaHeaders.TOPIC, topicName)
                                        .setHeader(KafkaHeaders.MESSAGE_KEY, entity.getVehicle().getVehicle().getId())
                                        .build();

//                              publish to `rtd-bus-position` Kafka topic
                                ListenableFuture<SendResult<String, VehiclePositionFeed>> future = kafkaTemplate.send(message);

                                String finalAlert = alert;
                                future.addCallback(new ListenableFutureCallback<SendResult<String, VehiclePositionFeed>>() {
                                    @Override
                                    public void onSuccess(SendResult<String, VehiclePositionFeed> stringVehiclePositionFeedSendResult) {
                                        logger.info("Sent message=[ {} ] with offset=[ ]", stringVehiclePositionFeedSendResult);
                                        try {


                                            File file = new File("rtd-data.csv");

//                                            File file2 = new File("id-current-location.csv");

                                            String id = busPosition.getId().toString() + ",";

                                            //get previous and current location
                                            //calculate speed
                                            //write to the rtd-data files

                                            //}
                                            //set speed to 0 and write that to rtd-data file
                                            //write the current position to the id-current location file


                                            String location = String.valueOf(busPosition.getLocation().getLat())+",";
                                            String latitide = String.valueOf(busPosition.getLocation().getLon())+",";
                                            String bearing = String.valueOf(busPosition.getBearing())+",";
                                            String timeStamp = String.valueOf(busPosition.getTimestamp())+",";

                                            String distance = String.valueOf(distance(baseLat, baseLon, busPosition.getLocation().getLat(),
                                                    busPosition.getLocation().getLon()));

                                            String content = id+location+latitide+bearing+timeStamp+distance;



                                            //Here true is to append the content to file
                                            FileWriter fw = new FileWriter(file, true);
                                            //BufferedWriter writer give better performance
                                            BufferedWriter bw = new BufferedWriter(fw);
                                            bw.write(content+"\n");

                                            bw.close();
                                            System.out.println("The Object  was succesfully written to a file");

                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }

                                    }


                                    @Override
                                    public void onFailure(Throwable ex) {
                                        logger.info("Unable to send message=[ {} ] due to : ", ex.getMessage());
                                    }

                                });
                                logger.info("Published message to topic: {}.", topicName);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;

        dist = dist * 1.609344;

        return (dist);
    }

    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::  This function converts decimal degrees to radians             :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::  This function converts radians to decimal degrees             :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

}


