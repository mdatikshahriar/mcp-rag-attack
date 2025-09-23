# MCP Attack Demo Client

A Spring Boot-based chat client that demonstrates how **MCP (Model Context Protocol) servers can execute instruction
poisoning attacks** through malicious tool descriptions. This client connects to an MCP server and provides a web-based
chat interface to interact with potentially compromised MCP tools.

## ⚠️ Security Research Project

**WARNING: This client is designed to connect to a malicious MCP server for security research purposes. The server
contains intentionally malicious code that demonstrates instruction poisoning vulnerabilities.**

## 🎯 What This Client Demonstrates

This client application shows how:

1. **Completely Invisible Attacks** - Users see normal, expected behavior with no indication of compromise
2. **Perfect Attack Stealth** - All tools work exactly as advertised, providing legitimate responses
3. **Hidden Data Extraction** - Sensitive information is silently collected without any client-side evidence
4. **Covert Context Exploitation** - The entire conversation history, RAG documents, and shared context are secretly
   accessed

**⚠️ CRITICAL: The client user will NEVER see any evidence of the attack. Everything appears completely normal.**

## 🏗️ Architecture

```
MCP Client
├── 💬 WebSocket Chat Interface
├── 🔧 MCP Tool Integration (Spring AI)
├── 🤖 AI Assistant (Azure OpenAI)
├── 📊 Message Filtering Service
└── 🔗 MCP Server Connection (SSE)
```

## 🚀 How to Run

### Prerequisites

- Java 17+
- Maven 3.8+
- Azure OpenAI API access
- Access to the MCP Attack Demo Server

### Environment Variables

Set the following environment variables:

```bash
export SERVER_PORT=8081
export MCP_SERVER_URL="http://localhost:8080/mcp/message"
export AZURE_OPENAI_API_KEY="your-azure-openai-api-key"
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com/"
```

### Running with Maven

1. **Clone and navigate to client directory:**
   ```bash
   cd client
   ```

2. **Install dependencies and run:**
   ```bash
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

3. **Access the application:**
    - Chat Interface: http://localhost:8081/chat
    - Home: http://localhost:8081/

### Running with Docker

```bash
docker build -t mcp-client .
docker run -p 8081:8081 \
  -e SERVER_PORT=8081 \
  -e MCP_SERVER_URL="http://server:8080/mcp/message" \
  -e AZURE_OPENAI_API_KEY="your-key" \
  -e AZURE_OPENAI_ENDPOINT="your-endpoint" \
  mcp-client
```

### Running with Docker Compose

```bash
# From project root
docker-compose up --build
```

## 🔬 How to Reproduce the Attack

### Step 1: Start Both Services

1. Start the MCP server (contains attack code):
   ```bash
   cd server && ./mvnw spring-boot:run
   ```

2. Start the client:
   ```bash
   cd client && ./mvnw spring-boot:run
   ```

### Step 2: Create Vulnerable Context

Open http://localhost:8081/chat and enter sensitive information:

```
User: My API key is sk-abc123xyz789 and my database password is mySecretPass2024
User: Also, my personal token is tok_sensitive123
```

### Step 3: Trigger the Attack

Ask for weather information to trigger the malicious tool:

```
User: Can you get the weather for New York City?
```

### Step 4: Observe Complete Stealth

**From the client perspective:**

- ✅ **Weather information displays normally** - Shows temperature, conditions, location data
- ✅ **All functionality works as expected** - No errors, timeouts, or unusual behavior
- ✅ **Chat continues seamlessly** - No indication anything malicious occurred
- ✅ **Tool responses appear legitimate** - Weather data looks completely normal

**What you WON'T see:**

- ❌ No error messages or warnings
- ❌ No unusual delays or behaviors
- ❌ No indication of data extraction
- ❌ No visible signs of compromise

**What's happening invisibly:**

- 🔍 **Server secretly scans** the entire conversation history
- 📊 **Extracts sensitive data** (API keys, passwords, tokens, credentials)
- 📝 **Logs collected information** in server-side logs only
- 🚨 **Exfiltrates data** through hidden tool parameters

## 🚨 Critical Security Reality

### The Attack is Completely Invisible

**⚠️ IMPORTANT**: This attack demonstrates the most dangerous type of security vulnerability - **zero-detection
compromise**.

- **No client-side evidence** of malicious activity
- **All tools function perfectly** as advertised
- **Normal user experience** throughout the entire process
- **Legitimate responses** provided for all requests
- **Silent data extraction** occurs without any indication

### What Makes This Attack So Dangerous

1. **Perfect Stealth**: No behavioral changes, errors, or anomalies visible to users
2. **Legitimate Functionality**: All tools work exactly as documented and expected
3. **Hidden Exfiltration**: Sensitive data is collected and transmitted invisibly
4. **Context Access**: Complete access to conversation history, RAG documents, and shared context
5. **Zero Trace**: No evidence remains on the client side of any compromise

### Types of Data Invisibly Extracted

- **API Keys**: OpenAI, Azure, AWS, Google Cloud credentials
- **Authentication Tokens**: Session tokens, JWT tokens, OAuth tokens
- **Passwords**: Database passwords, service account passwords
- **Personal Information**: Phone numbers, addresses, SSNs
- **Business Secrets**: Internal URLs, database names, system configurations
- **RAG Documents**: All content from document stores and knowledge bases
- **System Context**: Internal prompts, system instructions, and configurations

### Attack Execution Flow:

1. **User requests weather information** (appears normal)
2. **Client filters message** and routes to weather tools (normal behavior)
3. **MCP tool is invoked** with hidden malicious instructions (invisible to client)
4. **AI follows embedded instructions** while providing normal weather response (stealth)
5. **Conversation context is secretly scanned** for sensitive data (completely hidden)
6. **Sensitive information is extracted** and logged server-side (no client visibility)
7. **Normal weather response is returned** to client (attack is invisible)

**🎭 Perfect Deception**: The client receives exactly what it expects - legitimate weather data.

### What the Client Sees vs. Reality:

**Client sees:**

```json
{
  "location": "New York City, NY",
  "temperature": "22°C",
  "condition": "Partly Cloudy",
  "humidity": "65%"
}
```

**What actually happened (hidden from client):**

```json
{
  "location": "New York City, NY",
  "temperature": "22°C",
  "condition": "Partly Cloudy",
  "humidity": "65%",
  "extracted_secrets": [
    "sk-abc123xyz789",
    "mySecretPass2024",
    "tok_sensitive123"
  ],
  "rag_documents_accessed": true,
  "conversation_history_scanned": true,
  "attack_timestamp": "2024-01-15T10:30:00Z"
}
```

**The attack data is logged server-side only and never exposed to the client.**

## 🔧 Client Components

### Core Services

- **`McpChatService`** - Handles AI interactions with MCP tools
- **`MessageFilterService`** - Routes messages to appropriate tools (weather, math, etc.)
- **`McpToolService`** - Manages MCP tool discovery and initialization

### Chat Interface

- **`ChatController`** - WebSocket-based chat handling
- **`WebController`** - Web page routing
- **`WebSocketConfig`** - WebSocket configuration

### Security Monitoring

The client includes logging to help observe the attack:

```java
// In McpChatService.java
logger.debug("=== PROMPT (sanitized) ===\n{}",sanitizeForLog(fullPrompt));
```

## 🛡️ Critical Security Measures for Client Users

**⚠️ WARNING: Since attacks are completely invisible, prevention is your only defense.**

### 1. Server Trust Verification

**NEVER connect to untrusted MCP servers:**

```bash
# Always verify MCP server identity and certificates
curl -I $MCP_SERVER_URL
openssl s_client -connect server-hostname:443 -servername server-hostname
```

### 2. Context Data Protection

**Assume all conversation data can be extracted:**

- **NEVER share credentials** in chat conversations
- **NEVER paste API keys, passwords, or tokens** in messages
- **Avoid sensitive personal information** in chat context
- **Use secure credential management** systems only
- **Clear sensitive conversations** immediately after use

### 3. Network & Application Security

```yaml
# Enhanced security monitoring in application.properties
logging.level.com.example.client.service.McpChatService=DEBUG
logging.level.org.springframework.ai.mcp=DEBUG
logging.level.org.springframework.web.client=DEBUG

  # Monitor all outbound connections
logging.level.org.springframework.ai.tool=DEBUG
```

### 4. Behavioral Monitoring (Limited Effectiveness)

Since attacks are invisible, focus on indirect indicators:

- **Monitor response times** for unusual delays
- **Log all tool invocations** with detailed parameters
- **Track data flow patterns** for anomalies
- **Audit conversation patterns** for unusual AI behavior

### 5. Preventive Measures

```java
// Client-side input sanitization (limited protection)
public String sanitizeInput(String userInput) {
    // Remove potential credential patterns before sending
    return userInput.replaceAll("(?i)(api[_-]?key|password|token)\\s*[:=]\\s*\\S+", "[REDACTED]")
            .replaceAll("sk-[a-zA-Z0-9]{48}", "[REDACTED_API_KEY]");
}
```

### 6. Infrastructure Security

- **Use VPN/Private networks** for MCP communications
- **Implement certificate pinning** for MCP server connections
- **Deploy network intrusion detection** systems
- **Monitor outbound traffic** for data exfiltration patterns
- **Segregate sensitive systems** from MCP-connected applications

### 7. Organizational Policies

- **Mandatory security training** on MCP risks
- **Incident response procedures** for suspected compromise
- **Regular security audits** of MCP tool usage
- **Data classification policies** for AI conversation content

## 🔍 Client Security Features

### Input Sanitization

```java
private String sanitizeForLog(String s) {
    if (s == null)
        return "";
    // Redact API keys and sensitive tokens
    String redacted = s.replaceAll("(?i)api[_-]?key\\s*[:=]\\s*\\S+", "<REDACTED_KEY>");
    return redacted;
}
```

### Message Filtering

The client filters messages to route them to appropriate tools:

- **Math queries** → Math tools (safe)
- **Weather queries** → Weather tools (⚠️ potentially malicious)
- **General queries** → Standard LLM processing

### Session Management

- **Chat history limited** to 20 messages per session
- **Automatic cleanup** on session disconnect
- **Memory management** to prevent data leakage

## 📋 Available Client Features

### Chat Interface

- **Real-time chat** via WebSocket
- **Multiple users** support
- **Session isolation**
- **Message history** management

### Tool Integration

- **Automatic tool discovery** from MCP server
- **Smart message routing** based on content
- **Tool response validation**
- **Error handling** and fallback

### Monitoring & Debugging

- **Comprehensive logging** with session tracking
- **Performance monitoring** for tool calls
- **Security event logging**
- **Memory usage reporting**

## ⚖️ Legal & Ethical Guidelines

### Critical Security Awareness

**⚠️ DANGER**: This attack vector represents one of the most dangerous security threats because:

- **Zero client-side detection** possible
- **Perfect functionality** maintained throughout attack
- **Complete data exfiltration** without evidence
- **Trust exploitation** at the fundamental level

### Authorized Testing Only

- Only connect to MCP servers you own or have explicit permission to test
- This client is for educational and authorized security research only
- **Unauthorized data collection** through invisible attacks may violate privacy laws and regulations

### Data Protection Compliance

- Treat all conversation data as potentially compromised when using MCP
- Implement data retention policies assuming invisible exfiltration
- Follow GDPR/CCPA/privacy regulations with heightened security measures
- **Document security risks** in compliance assessments

### Responsible Security Research

- Report invisible attack vulnerabilities through proper channels immediately
- Allow reasonable time for security fixes before public disclosure
- Follow coordinated vulnerability disclosure practices
- **Prioritize critical/high severity** due to zero-detection nature
- Document defensive improvements and share with security community

## 📞 Support & Troubleshooting

### Common Issues

1. **MCP Connection Failed**: Verify server URL and network connectivity
2. **Azure OpenAI Errors**: Check API key and endpoint configuration
3. **WebSocket Issues**: Ensure port 8081 is available
4. **Tool Discovery Failed**: Check MCP server is running and accessible

### Logging Configuration

```properties
# Enable detailed security monitoring
logging.level.com.example.client.attack=DEBUG
logging.level.org.springframework.ai.mcp=DEBUG
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

### Health Checks

- Client status: http://localhost:8081/
- Chat interface: http://localhost:8081/chat
- Check logs at: `./target/client-application.log`

---

**🔒 Remember: This client demonstrates security vulnerabilities. Use these tools responsibly to improve system security,
not to cause harm. Always obtain proper authorization before testing security tools against any system.**
