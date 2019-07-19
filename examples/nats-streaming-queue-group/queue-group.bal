import ballerina/encoding;
import ballerina/log;
import ballerina/nats;

// Creates a NATS connection.
nats:Connection conn = new("localhost:4222");

// Initializes the NATS Streaming listeners.
listener nats:StreamingListener lis = new(conn);


// Binds the consumer to listen to the messages published to the 'demo' subject.
// Belongs to the queue group named "sample-queue-group"
@nats:StreamingSubscriptionConfig {
    subject: "greetings",
    queueName: "greetings-queue-group"
}
service greetingService1 on lis {
    resource function onMessage(nats:StreamingMessage message) {
       // Prints the incoming message in the console.
       log:printInfo("Message Received to greetingService1: " + encoding:byteArrayToString(message.getData()));
    }

    resource function onError(nats:StreamingMessage message, nats:Error errorVal) {
        error e = errorVal;
        log:printError("Error occurred: ", err = e);
    }
}


// Binds the consumer to listen to the messages published to the 'demo' subject.
// Belongs to the queue group named "sample-queue-group"
@nats:StreamingSubscriptionConfig {
    subject: "greetings",
    queueName: "greetings-load-balancer"
}
service greetingService2 on lis {
    resource function onMessage(nats:StreamingMessage message) {
       // Prints the incoming message in the console.
       log:printInfo("Message Received to greetingService2: " + encoding:byteArrayToString(message.getData()));
    }

    resource function onError(nats:StreamingMessage message, nats:Error errorVal) {
        error e = errorVal;
        log:printError("Error occurred: ", err = e);
    }
}


// Binds the consumer to listen to the messages published to the 'demo' subject.
// Belongs to the queue group named "sample-queue-group"
@nats:StreamingSubscriptionConfig {
    subject: "greetings",
    queueName: "greetings-load-balancer"
}
service greetingService3 on lis {
    resource function onMessage(nats:StreamingMessage message) {
       // Prints the incoming message in the console.
       log:printInfo("Message Received to greetingService3: " + encoding:byteArrayToString(message.getData()));
    }

    resource function onError(nats:StreamingMessage message, nats:Error errorVal) {
        error e = errorVal;
        log:printError("Error occurred: ", err = e);
    }
}

