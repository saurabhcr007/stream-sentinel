const { Kafka, logLevel } = require('kafkajs');

/**
 * Initializes a Kafka consumer to listen to the given topic and broadcasts
 * parsed JSON messages via the provided Socket.IO instance.
 */
async function setupKafkaConsumer(brokers, topic, io) {
    const kafka = new Kafka({
        clientId: 'stream-sentinel-api-gateway',
        brokers: brokers.split(','),
        logLevel: logLevel.WARN
    });

    const consumer = kafka.consumer({ groupId: 'api-gateway-group-' + Date.now() }); // Unique group to receive all current alerts

    try {
        console.log(`[Kafka] Connecting to brokers: ${brokers}`);
        await consumer.connect();

        console.log(`[Kafka] Subscribing to topic: ${topic}`);
        // Subscribe to topic, from beginning just for demo purposes so UI sees historical alerts
        await consumer.subscribe({ topic, fromBeginning: true });

        console.log(`[Kafka] Running consumer...`);
        await consumer.run({
            eachMessage: async ({ message }) => {
                try {
                    if (!message.value) return;

                    const alertJsonStr = message.value.toString();
                    const alertData = JSON.parse(alertJsonStr);

                    console.log(`[Kafka] Received alert from rule: ${alertData.ruleName || 'unknown'}`);

                    // Broadcast to all connected React clients
                    io.emit('new_alert', alertData);

                } catch (err) {
                    console.error('[Kafka] Error parsing message value:', err);
                }
            },
        });
    } catch (error) {
        console.error(`[Kafka] Connection/Subscription error:`, error);
        // Do not crash the entire API Gateway if Kafka is down yet
        console.log('[Kafka] Will operate without live Kafka feed.');
    }
}

module.exports = { setupKafkaConsumer };
