require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const { setupKafkaConsumer } = require('./kafka');
const rulesRouter = require('./routes/rules');

const PORT = process.env.PORT || 3001;
const KAFKA_BROKERS = process.env.KAFKA_BOOTSTRAP_SERVERS || 'localhost:9092';
const KAFKA_ALERT_TOPIC = process.env.KAFKA_ALERT_TOPIC || 'alerts';

// 1. Initialize Express and HTTP Server
const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);

// 2. Initialize Socket.IO for real-time pushing
const io = new Server(server, {
    cors: {
        origin: '*', // For development, allow any localhost origin
        methods: ['GET', 'POST']
    }
});

// 3. REST API Routes
app.use('/api/rules', rulesRouter);

// Basic health check
app.get('/health', (req, res) => {
    res.json({ status: 'UP', service: 'api-gateway' });
});

// 4. WebSocket Connections
let connectedClients = 0;
io.on('connection', (socket) => {
    connectedClients++;
    console.log(`[Socket.IO] Client connected. Total: ${connectedClients}`);

    socket.on('disconnect', () => {
        connectedClients--;
        console.log(`[Socket.IO] Client disconnected. Total: ${connectedClients}`);
    });
});

// 5. Connect to Kafka and broadcast alerts
setupKafkaConsumer(KAFKA_BROKERS, KAFKA_ALERT_TOPIC, io).catch(err => {
    console.error('[Kafka] Failed to set up consumer:', err);
});

// 6. Simulate Real-time Metrics (Processing Rate) for the UI Dashboard
// In production, this would also come from Kafka metrics/Prometheus, but we mock the flow here
setInterval(() => {
    if (connectedClients > 0) {
        const timeStr = new Date().toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });

        // Generate realistic-looking fluctuating metrics
        const baseRate = 4000;
        const normal = Math.floor(baseRate + (Math.random() * 800 - 400));
        const auth = Math.floor(250 + (Math.random() * 100 - 50));
        const payments = Math.floor(130 + (Math.random() * 60 - 30));

        io.volatile.emit('metrics_update', {
            time: timeStr,
            normal,
            auth,
            payments
        });
    }
}, 3000); // Push every 3 seconds

// 7. Start Server
server.listen(PORT, () => {
    console.log(`🚀 API Gateway running on http://localhost:${PORT}`);
    console.log(`🔌 WebSocket server attached.`);
    console.log(`📡 Expecting Kafka at: ${KAFKA_BROKERS}`);
});
