package com.geofence.demo.entity;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

public final class CustomSerDes {

    static public final class VehiclePositionFeedSerde
            extends Serdes.WrapperSerde<VehiclePositionFeed> {
        public VehiclePositionFeedSerde() {
            super(new AvroSerializer<>(),
                    new AvroDeserializer<>(VehiclePositionFeed.class));
        }
    }
    public static Serde<VehiclePositionFeed> VehicleSpeed() {
        return new CustomSerDes.VehiclePositionFeedSerde();
    }

}
