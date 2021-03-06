/*
 * Copyright 2017 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License").  See License in the project root for license information.
 */

package com.linkedin.kafka.clients.producer;

import com.linkedin.kafka.clients.consumer.LiKafkaConsumer;
import com.linkedin.kafka.clients.largemessage.errors.SkippableException;
import com.linkedin.kafka.clients.utils.tests.AbstractKafkaClientsIntegrationTestHarness;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.BitSet;
import java.util.Collections;
import java.util.Properties;
import java.util.Random;

import static org.testng.AssertJUnit.*;


public class LiKafkaProducerIntegrationTest extends AbstractKafkaClientsIntegrationTestHarness {
  // Runs testing against running kafka host
  private static final int RECORD_COUNT = 1000;

  @BeforeMethod
  @Override
  public void setUp() {
    super.setUp();
  }

  @AfterMethod
  @Override
  public void tearDown() {
    super.tearDown();
  }

  /**
   * This test pushes test data into a temporary topic to a particular broker, and
   * verifies all data are sent and can be consumed corerctly.
   *
   * @throws java.io.IOException
   * @throws InterruptedException
   */
  @Test
  public void testSend() throws IOException, InterruptedException {

    Properties props = new Properties();
    props.setProperty(ProducerConfig.ACKS_CONFIG, "-1");
    LiKafkaProducer<String, String> producer = createProducer(props);
    final String tempTopic = "testTopic" + new Random().nextInt(1000000);

    for (int i = 0; i < RECORD_COUNT; ++i) {
      String value = Integer.toString(i);
      producer.send(new ProducerRecord<>(tempTopic, value));
    }

    // Drain the send queues
    producer.close();

    Properties consumerProps = new Properties();
    consumerProps.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    LiKafkaConsumer<String, String> consumer = createConsumer(consumerProps);
    consumer.subscribe(Collections.singleton(tempTopic));
    int messageCount = 0;
    BitSet counts = new BitSet(RECORD_COUNT);
    long startMs = System.currentTimeMillis();
    while (messageCount < RECORD_COUNT && System.currentTimeMillis() < startMs + 30000) {
      ConsumerRecords<String, String> records = consumer.poll(100);
      for (ConsumerRecord<String, String> record : records) {
        int index = Integer.parseInt(record.value());
        counts.set(index);
        messageCount++;
      }
    }
    consumer.close();
    assertEquals(RECORD_COUNT, messageCount);
  }

  /**
   * This test produces test data into a temporary topic to a particular broker with a non-deseriable value
   * verifies producer.send() will throw exception if and only if SKIP_RECORD_ON_SKIPPABLE_EXCEPTION_CONFIG is false
   */
  @Test
  public void testSerializationException() {

    Serializer<String> stringSerializer = new StringSerializer();
    Serializer<String> errorThrowingSerializer = new Serializer<String>() {
      @Override
      public void configure(Map<String, ?> configs, boolean isKey) {

      }

      @Override
      public byte[] serialize(String topic, String value) {
        if (value.equals("ErrorBytes")) {
          throw new SkippableException();
        }
        return stringSerializer.serialize(topic, value);
      }

      @Override
      public void close() {

      }
    };

    Properties props = getProducerProperties(null);
    props.setProperty(ProducerConfig.ACKS_CONFIG, "-1");
    LiKafkaProducer<String, String> producer = new LiKafkaProducerImpl<>(props, stringSerializer, errorThrowingSerializer, null, null);
    final String tempTopic = "testTopic" + new Random().nextInt(1000000);

    producer.send(new ProducerRecord<>(tempTopic, "ErrorBytes"));
    producer.send(new ProducerRecord<>(tempTopic, "value"));
    producer.close();

    Properties consumerProps = new Properties();
    consumerProps.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    LiKafkaConsumer<String, String> consumer = createConsumer(consumerProps);
    consumer.subscribe(Collections.singleton(tempTopic));
    int messageCount = 0;
    long startMs = System.currentTimeMillis();
    while (messageCount < 1 && System.currentTimeMillis() < startMs + 30000) {
      ConsumerRecords<String, String> records = consumer.poll(100);
      for (ConsumerRecord<String, String> record : records) {
        assertEquals("value", record.value());
        messageCount++;
      }
    }
    consumer.close();
    assertEquals(1, messageCount);
  }

}

