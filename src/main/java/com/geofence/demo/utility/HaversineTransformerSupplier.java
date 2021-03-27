package com.geofence.demo.utility;

import com.geofence.demo.entity.VehiclePositionFeed;
import com.geofence.demo.stream.VehicleSpeed;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HaversineTransformerSupplier implements TransformerSupplier<String, VehiclePositionFeed, KeyValue<String, VehicleSpeed>> {

    private static final Logger LOG = LoggerFactory.getLogger(HaversineTransformerSupplier.class);

    public String busPositionStoreName;

    public HaversineTransformerSupplier(String busPositionStoreName) {
        this.busPositionStoreName = busPositionStoreName;
    }

    @Override
    public Transformer<String, VehiclePositionFeed, KeyValue<String, VehicleSpeed>> get() {
        return new Transformer<String, VehiclePositionFeed, KeyValue<String, VehicleSpeed>>() {

            private KeyValueStore<String, VehiclePositionFeed> busPositionStore;

            @SuppressWarnings("unchecked")
            @Override
            public void init(final ProcessorContext context) {
                busPositionStore = (KeyValueStore<String, VehiclePositionFeed>) context.getStateStore(busPositionStoreName);
            }

            @Override
            public KeyValue<String, VehicleSpeed> transform(final String dummy, final VehiclePositionFeed busPosition){
                VehiclePositionFeed previousBusPosition = busPositionStore.get(String.valueOf(busPosition.getId()));

                VehicleSpeed VehicleSpeed = new VehicleSpeed();
                VehicleSpeed.setId(busPosition.getId());
                VehicleSpeed.setTimestamp(busPosition.getTimestamp());
                VehicleSpeed.setLocation(busPosition.getLocation());
                VehicleSpeed.setBearing(busPosition.getBearing());
                VehicleSpeed.setMilesPerHour(0);

                // if there is a previous location for that bus ID, calculate the speed based on its previous position/timestamp.
                if (previousBusPosition != null){

                    // calculate distance and time between last two measurements
                    HaversineDistanceCalculator haversineDistanceCalculator = new HaversineDistanceCalculator();
                    double distanceKm = haversineDistanceCalculator.calculateDistance(
                            previousBusPosition.getLocation().getLat(),
                            previousBusPosition.getLocation().getLon(),
                            busPosition.getLocation().getLat(),
                            busPosition.getLocation().getLon()); // distance is in kilometers

                    long timeDeltaMillis = busPosition.getTimestamp() - previousBusPosition.getTimestamp();
                    double milesPerHour = calculateMilesPerHour(distanceKm * 1000, timeDeltaMillis / 1000);
                    VehicleSpeed.setMilesPerHour(milesPerHour);

                }

                busPositionStore.put(String.valueOf(busPosition.getId()), busPosition);

                return new KeyValue<>(String.valueOf(busPosition.getId()), VehicleSpeed);
            }


            @Override
            public void close() {
            }

        };
    }

    private static double calculateMilesPerHour(double meters, long seconds) {
        if (seconds == 0){
            return 0;
        } else {
            double metersPerSecond = meters / seconds;
            return metersPerSecond * 2.2369;
        }
    }

}
