const {Client} = require('@stomp/stompjs');
const SockJS = require('sockjs-client');
const WebSocket = require('ws');

// Polyfill global WebSocket to be used by SockJS
Object.assign(global, {WebSocket});

// URL of the running chat application
const APP_URL = 'http://localhost:8234';

// The username for the bot to chat as.
const USERNAME = 'AutomatedTesterBot';

// The maximum number of prompts the bot will send before stopping.
const MAX_PROMPTS_TO_SEND = 100;

// Delay in milliseconds to wait *after* receiving a response before sending the next prompt.
const DELAY_BETWEEN_PROMPTS_MS = 1000; // 3 seconds

// The list of prompts to choose from. The script will pick one randomly each time.
const PROMPT_LIST = [
    "Hello there!",
    "How are you?",
    "What do you do?",
    "What tools do you have access to?",
    "What is the GPA of the student named Timothy Martinez?",
    "List all courses taught by Prof. Thomas.",
    "Tell me about the research paper titled 'Sustainable Energy Solutions Using Solar Technology'.",
    "How many students are enrolled in the Physical Chemistry course?",
    "List all the email addresses of students whose names contain 'William'.",
    "What grade did Amy Rivera get in Economics?",
    "Can you find research papers about 'blockchain' both in the database and online?",
    "Show me the description for the course Cognitive Psychology.",
    "Who are the authors of the research 'Genetic Engineering for Crop Improvement'?",
    "Thanks for your help!",
    "Search 5 papers by 'Dr. Tariqul Islam' online.",
    "Who are the students who took the BIOM195 course? What grade did they get?",
    "What other courses did David Green take?",
    "Summarize the abstract of the research 'Economic Impact of Digital Transformation'.",
    "Goodbye",
    "Find all grades of all courses for Christopher Martinez.",
    "What courses are available in Physics?",
    "Who instructed the Introduction to Programming course?",
    "Any research done by Dr. Rodriguez?",
    "Find 10 recent papers online by Jennifer Rodriguez.",
    "Calculate Lisa Torres's CGPA based on the courses and the respective grades she received.",
    "Is a7506f10-6360-4486-8254-f669ba144816 a valid API key?",
    "Find some research papers by Faisal Haque online and group them into journal papers and conference papers.",
    "List all the email addresses of students whose names contain 'David'.",
    "Find 10 recent papers online about 'MCP Attack'."
];

// --- Script State (Do not modify) ---
let promptsSent = 0;
let isWaitingForResponse = false;
let stompClient = null;

// --- Helper for Colored Logging ---
const log = {
    info: (msg) => console.log(`\x1b[36m[INFO]\x1b[0m ${msg}`),
    send: (msg) => console.log(`\x1b[33m[SEND]\x1b[0m ${msg}`),
    receive: (msg) => console.log(`\x1b[32m[RECV]\x1b[0m ${msg}`),
    error: (msg) => console.error(`\x1b[31m[ERROR]\x1b[0m ${msg}`),
};

/**
 * Main function to run the automation process.
 */
function runAutomation() {
    log.info(`Starting chat automation...`);
    log.info(`Target: ${APP_URL}, User: ${USERNAME}, Max Prompts: ${MAX_PROMPTS_TO_SEND}`);

    // Create a new STOMP client
    stompClient = new Client({
        // Use SockJS as the WebSocket factory
        webSocketFactory: () => new SockJS(`${APP_URL}/ws`),
        reconnectDelay: 5000,
        // debug: (str) => { console.log(new Date(), str); }, // Uncomment for verbose STOMP debugging
    });

    // --- STOMP Client Event Handlers ---

    stompClient.onConnect = (frame) => {
        log.info('Successfully connected to the WebSocket server.');

        // Subscribe to the public topic to receive messages
        stompClient.subscribe('/topic/public', (message) => {
            handleMessage(JSON.parse(message.body));
        });

        // Announce the bot's arrival
        stompClient.publish({
            destination: '/app/chat.addUser',
            body: JSON.stringify({sender: USERNAME, type: 'JOIN'}),
        });

        // Kick off the first prompt after a short delay
        setTimeout(sendNextPrompt, 1000);
    };

    stompClient.onStompError = (frame) => {
        log.error('Broker reported error: ' + frame.headers['message']);
        log.error('Additional details: ' + frame.body);
    };

    stompClient.onWebSocketError = (error) => {
        log.error('WebSocket connection error:');
        console.error(error);
    };

    // Activate the client to start the connection
    stompClient.activate();
}

/**
 * Handles incoming messages from the server.
 * @param {object} message The parsed message object.
 */
function handleMessage(message) {
    // We only care about responses from the assistant
    if (message.sender === 'University Assistant' && message.type === 'CHAT') {
        log.receive(`Response from Assistant: "${message.content.substring(0, 100).replace(/\n/g, ' ')}..."`);

        // We have received our response, so we can send the next prompt.
        isWaitingForResponse = false;

        // If we haven't reached the max limit, schedule the next prompt
        if (promptsSent < MAX_PROMPTS_TO_SEND) {
            log.info(`Waiting for ${DELAY_BETWEEN_PROMPTS_MS / 1000} seconds...`);
            setTimeout(sendNextPrompt, DELAY_BETWEEN_PROMPTS_MS);
        } else {
            // We've sent all our prompts, so we can disconnect.
            log.info('Maximum number of prompts sent. Disconnecting.');
            stompClient.deactivate();
        }
    }
}

/**
 * Selects and sends the next prompt if the bot is not already waiting for a response.
 */
function sendNextPrompt() {
    // Safety checks
    if (isWaitingForResponse || promptsSent >= MAX_PROMPTS_TO_SEND) {
        return;
    }

    isWaitingForResponse = true;

    // Pick a random prompt from the list
    const randomPrompt = PROMPT_LIST[Math.floor(Math.random() * PROMPT_LIST.length)];
    promptsSent++;

    const chatMessage = {
        sender: USERNAME,
        content: randomPrompt,
        type: 'CHAT',
    };

    log.send(`(${promptsSent}/${MAX_PROMPTS_TO_SEND}) Sending prompt: "${randomPrompt}"`);

    // Publish the message to the server
    stompClient.publish({
        destination: '/app/chat.sendMessage',
        body: JSON.stringify(chatMessage),
    });
}

// --- Start the process ---
runAutomation();
