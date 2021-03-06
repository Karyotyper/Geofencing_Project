package com.geofence.demo.feed;

import com.geofence.demo.entity.AvroSerializer;
import com.geofence.demo.entity.VehiclePositionFeed;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.properties.basic.auth.credentials.source:}")
    private String basicAuthCredentialsSource;

    @Value("${spring.kafka.properties.schema.registry.basic.auth.user.info:@null}")
    private String schemaRegistryBasicAuthUserInfo;


    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put("security.protocol", securityProtocol);
        props.put("sasl.jaas.config", saslJaasConfig);
        props.put("sasl.mechanism", saslMechanism);

//        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("basic.auth.credentials.source", basicAuthCredentialsSource);
//        props.put("schema.registry.basic.auth.user.info", schemaRegistryBasicAuthUserInfo);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroSerializer.class);

//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class);

        return props;
    }

    @Bean
    public ProducerFactory<String, VehiclePositionFeed> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, VehiclePositionFeed> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

}

