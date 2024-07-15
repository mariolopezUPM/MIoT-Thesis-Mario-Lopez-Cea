package com.example.tfmapp.MQTT;

public class TopicsMQTT {

    public static final String topicPubCommand = "tfm/commandsAndroid"; //topic to send commands
    //public static final String topicPubCarStatus = "tfm/carStatusAndroid"; // topic to as for car status
    public static final String topicPubContact = "tfm/contactAndroid";

    public static final String topicPubSetUp = "tfm/setUpAndroid";
    public static final String topicSubData = "tfm/data"; //topic to receive sensrs data
    public static final String topicSubCommand = "tfm/commandsPython"; //topic to get car status
    //public static final String topicSubCarStatus = "tfm/carStatusPython"; //topic to receive commands reply
    public static final String topicSubContact = "tfm/contactPython";
}
