package com.geofence.demo.feed;

import com.geofence.demo.entity.VehiclePositionFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FeedConsumer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
//
    @KafkaListener(topics = "rtd-bus-position", groupId = "geof-group")
    public void consume(@Payload VehiclePositionFeed f) {
        logger.info("Received: "+f.toString());

    }
}
