package com.geofence.demo.feed;

import com.geofence.demo.entity.Location;
import com.geofence.demo.entity.VehiclePositionFeed;
import com.google.transit.realtime.GtfsRealtime;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.io.InputStream;

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

    @Scheduled(cron = "*/5 * * * * *")
    private void getBusPositions(){
        try{

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
                                logger.info("DHSD: ", vehiclePosition.getPosition().getLatitude());

                                Location location = new Location();
                                location.setLat(vehiclePosition.getPosition().getLatitude());
                                location.setLon(vehiclePosition.getPosition().getLongitude());

                                VehiclePositionFeed busPosition = new VehiclePositionFeed();

                                busPosition.setId(vehiclePosition.getVehicle().getId());
                                busPosition.setTimestamp(vehiclePosition.getTimestamp() * 1000); // convert seconds to Avro-friendly millis
                                busPosition.setLocation(location);
                                busPosition.setBearing(vehiclePosition.getPosition().getBearing());
                                System.out.println("PING: "+busPosition.getId());

                                Message<VehiclePositionFeed> message = MessageBuilder
                                        .withPayload(busPosition)
                                        .setHeader(KafkaHeaders.TOPIC, topicName)
                                        .setHeader(KafkaHeaders.MESSAGE_KEY, entity.getVehicle().getVehicle().getId())
                                        .build();

//                              publish to `rtd-bus-position` Kafka topic
                                ListenableFuture<SendResult<String, VehiclePositionFeed>> future = kafkaTemplate.send(message);

                                future.addCallback(new ListenableFutureCallback<SendResult<String, VehiclePositionFeed>>() {
                                    @Override
                                    public void onSuccess(SendResult<String, VehiclePositionFeed> stringVehiclePositionFeedSendResult) {
                                        logger.info("Sent message=[ {} ] with offset=[ ]", stringVehiclePositionFeedSendResult);
                                    }


                                    @Override
                                    public void onFailure(Throwable ex) {
                                        logger.info("Unable to send message=[ {} ] due to : ", ex.getMessage());
                                    }

                                });
                                logger.info("Published message to topic: {}.", topicName);
                            }
                        }catch (Exception e){
                        e.printStackTrace();
                        }
                    });
                        }catch (Exception e){
            e.printStackTrace();
        }
    }



}


