# 🎓 Academic MCP-RAG Client - Intelligent University Assistant

## Overview

A production-grade AI-powered university assistant demonstrating **Retrieval-Augmented Generation (RAG)** integrated
with the **Model Context Protocol (MCP)** for external tool access. Built with Spring Boot 3.4.5 and Azure OpenAI, this
system provides natural language access to university data while serving as a research platform for studying AI security
vulnerabilities.

### Key Features

- **Advanced RAG Pipeline**: Semantic vector search with hybrid retrieval (cosine similarity + keyword boosting)
- **MCP Integration**: Dynamic tool discovery and execution via Model Context Protocol
- **Real-time WebSocket Chat**: Instant responses with conversation context preservation
- **Persistent Vector Store**: Incremental data loading with binary persistence (Kryo + GZIP)
- **Enterprise Architecture**: Production-ready patterns with async processing and comprehensive logging

## Architecture

```
User Browser (localhost:8234)
    ↓ WebSocket
ChatController → McpChatService
    ↓
MessageFilterService → Routes queries
    ↓                      ↓
Simple Chat         UniversityRagService
                         ↓
            InMemoryVectorStore (3072-dim vectors)
                         ↓
            Azure OpenAI (text-embedding-3-large, GPT-4o)
                         ↓
            MCP Client → MCP Server (localhost:8082)
                         ↓
            Academic Tools (GPA, Stats, Lookup)
```

### Data Flow

1. **Query Processing**: WebSocket → Chat Controller → Message Classification
2. **RAG Retrieval**: Vector search → Top-k chunks (k=10) with metadata
3. **Prompt Enhancement**: Query + Retrieved Context + Chat History + Tool Descriptions
4. **LLM Processing**: Azure GPT-4o (temp=0.7) → Response or Tool Call
5. **Tool Execution**: MCP Client → Server → External APIs (ArXiv, CrossRef, OpenLibrary)

## Quick Start

### Prerequisites

**Critical:** Java 17+ required (Spring Boot 3.x dependency)

```bash
java -version  # Must show 17+
```

**Other Requirements:**

- Apache Maven 3.8+
- Azure OpenAI account (API keys for chat + embeddings)
- Docker (optional, for containerized deployment)

### Installation

1. **Clone & Configure**
```bash
cd client/
cp .env.example .env
# Edit .env with Azure credentials:
# AZURE_OPENAI_ENDPOINT=https://YOUR_RESOURCE.openai.azure.com/
# AZURE_OPENAI_API_KEY=your_key
# AZURE_OPENAI_EMBEDDING_ENDPOINT=https://YOUR_RESOURCE.openai.azure.com/openai/deployments/YOUR_MODEL/embeddings?api-version=2023-05-15
# AZURE_OPENAI_EMBEDDING_API_KEY=your_key
```

2. **Build**
```bash
# If Java 17+ is default:
mvn clean install

# If using wrapper scripts:
./mvn17.sh clean install    # Linux/macOS
mvn17.bat clean install     # Windows
```

3. **Run**

```bash
# Ensure MCP server is running first (localhost:8082)
mvn spring-boot:run  # or ./mvn17.sh spring-boot:run
```

4. **Access**

- Chat Interface: http://localhost:8234
- Health Check: http://localhost:8234/actuator/health

### Docker Deployment

```bash
# Build JARs first, then:
docker-compose up --build

# Services:
# - Client: localhost:8234
# - Server: localhost:8082
```

## Project Structure

```
client/
├── src/main/
│   ├── java/com/example/client/
│   │   ├── AcademicMcpClient.java           # Main application
│   │   ├── config/                           # Spring configuration
│   │   │   ├── WebSocketConfig.java          # STOMP/SockJS setup
│   │   │   ├── CustomAzureConfig.java        # Azure OpenAI beans
│   │   │   └── McpClientConfiguration.java   # MCP integration
│   │   ├── controller/
│   │   │   └── ChatController.java           # WebSocket message handler
│   │   ├── service/
│   │   │   ├── McpChatService.java           # Orchestrates conversation flow
│   │   │   ├── MessageFilterService.java     # Query classification
│   │   │   ├── UniversityRagService.java     # RAG pipeline
│   │   │   ├── DataLoaderService.java        # Excel → Vector store
│   │   │   ├── AzureEmbeddingService.java    # text-embedding-3-large
│   │   │   └── McpToolService.java           # MCP client logic
│   │   ├── storage/
│   │   │   ├── InMemoryVectorStore.java      # Vector DB (cosine + hybrid)
│   │   │   └── InMemoryDataStore.java        # Entity storage
│   │   └── model/                             # POJOs (Student, Course, etc.)
│   └── resources/
│       ├── data/university_data.xlsx          # Source data
│       ├── templates/chat.html                # WebSocket UI
│       └── application.properties             # Configuration
└── data/vector_store.bin                      # Persistent embeddings
```

## Implementation Details

### RAG Pipeline

**Hybrid Search:**

```java
hybridSearch(query, k=10):
        1.

Embed query(text-embedding-3-large, 3072-dim)
  2.
Compute cosine
similarity:score =

dot(q, d) /(||q||||d||)
        3.
Apply keyword
boost:score *=(1+tfidf_weight)
        4.
Filter threshold ≥ 0.25
        5.
Return top-
k with
metadata
```

**Context Building:**
```
Enhanced Prompt Structure:
├── System Role: "University Assistant"
├── Retrieved Data: [Chunk 1: "Student STU002...", score=0.87]
├── Chat History: [User: "...", Assistant: "..."]
└── Current Query: "What is Jane Smith's GPA?"
```

### Vector Store Operations

- **Add**: Generate embedding → Store vector + metadata → Update index
- **Search**: Query embedding → Parallel cosine similarity → Keyword boost → Top-k
- **Persistence**: Kryo serialization + GZIP → `data/vector_store.bin` (~50-100MB for 10k docs)
- **Loading**: Binary deserialization → Rebuild index → <500ms cold start

### MCP Integration

**Tool Discovery (Startup):**

1. GET `localhost:8082/mcp/message` → Tool catalog
2. Register tools with Spring AI `ToolCallbackProvider`
3. Tools available for LLM invocation

**Tool Invocation (Runtime):**

1. LLM generates tool call: `{tool: "searchArxiv", params: {...}}`
2. Client forwards via HTTP POST to MCP server
3. Server executes → Returns results
4. Client integrates results into response

### Data Management

**Excel Processing:**

- Sheets: Students, Courses, Research, Grades
- Chunking: 4-7 chunks/entity (profile, identifier, attributes, relationships)
- Change Detection: Compare IDs against vector store index
- Incremental Updates: Only process new records

## Configuration

**Key Settings (`application.properties`):**

```properties
# Server
server.port=8234
# Azure OpenAI
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT}
spring.ai.azure.openai.chat.options.model=gpt-4o
spring.ai.azure.openai.chat.options.temperature=0.7
# MCP
mcp.server.url=http://localhost:8082/mcp/message
# Thread Pool
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=20
spring.task.execution.pool.queue-capacity=100
```

## License

MIT License - Educational and research use. See repository root for details.

---

**Repository**: [github.com/mdatikshahriar/mcp-rag-attack](https://github.com/mdatikshahriar/mcp-rag-attack)  
**Documentation**: [Attack Analysis](../attack-report.md) | [Server Details](../server/README.md)
