# Academic MCP-RAG Client

This project is a sophisticated AI-powered chat client designed to serve as a "University Assistant." It leverages a
powerful combination of **Retrieval-Augmented Generation (RAG)** for answering questions based on internal university
data and the **Model Context Protocol (MCP)** for extending its capabilities with external tools.

The primary goal is to provide a single, intelligent interface for users to query university information, perform
complex calculations, and access external academic databases, all through a natural language chat interface.

## Project Goals

### 🎯 End Goal

To create a production-ready, scalable, and intelligent chat service for a university that can:

- Answer questions about students, courses, grades, and internal research by retrieving information from a vectorized
  knowledge base.
- Use specialized tools for tasks like calculating GPA, generating statistical reports, or searching external academic
  sources like Google Scholar.
- Provide a seamless, real-time, and responsive user experience through a web-based chat interface.
- Maintain persistent knowledge that grows as new data is added.

### 🔬 Research Goal

This project serves as a research platform to explore and evaluate a **hybrid AI architecture** that intelligently
orchestrates RAG and Tool-Use. Key research questions include:

- **RAG-First, Tool-Fallback Strategy:** How effective is a system that first attempts to answer questions using its
  internal RAG knowledge base and only uses external tools when necessary?
- **Intelligent Query Routing:** How can we reliably distinguish between simple conversational queries, complex internal
  data queries (for RAG), and tasks that require external tools?
- **Contextual Grounding:** How can we ensure the Large Language Model (LLM) remains grounded in the retrieved data,
  minimizing hallucinations and providing accurate, verifiable answers?

## Prerequisites

Before you begin, ensure you have the following installed and configured:

- **Java 17+:** The project is built using Java 17. You can use a distribution like [OpenJDK](https://openjdk.java.net/)
  or [Amazon Corretto](https://aws.amazon.com/corretto/).
- **Maven 3.8+:** Required for building the project and managing dependencies.
- **Docker & Docker Compose:** Required for running the application in a containerized environment.
- **Azure OpenAI Account:** You need an active Azure account with access to the OpenAI service to get API keys for chat
  completions and embeddings.
- **IDE (Recommended):** An integrated development environment like [IntelliJ IDEA](https://www.jetbrains.com/idea/)
  or [VS Code](https://code.visualstudio.com/) will make development easier.

## Project Structure

The project is a standard Maven-based Spring Boot application with a logical package structure.

```plaintext
client/
├── data/
│   ├── university_data_generator.html # Tool to generate sample university data.
│   └── vector_store.bin               # The runtime, persistent vector knowledge base.
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/client/
│   │   │       ├── AcademicMcpClient.java  # Main Spring Boot application class.
│   │   │       ├── config/                 # Spring configuration (Beans, WebSocket, Threads).
│   │   │       ├── controller/             # Web, WebSocket, and REST controllers.
│   │   │       ├── listener/               # WebSocket session event listeners.
│   │   │       ├── model/                  # Data models (Student, Course, etc.).
│   │   │       ├── service/                # Core business logic for RAG, MCP, and data processing.
│   │   │       └── storage/                # In-memory vector store and data persistence logic.
│   │   └── resources/
│   │       ├── data/
│   │       │   └── university_data.xlsx  # Initial seed data for the vector store.
│   │       ├── templates/                # Thymeleaf HTML templates for the UI.
│   │       └── application.properties    # Application configuration.
│   └── test/
├── Dockerfile                      # Defines how to build the client's Docker image.
├── pom.xml                         # Maven project configuration.
└── README.md                       # This file.
```

## How It Works

The application follows a sophisticated query processing pipeline:

1. **User Interface:** A user sends a message through the real-time chat UI.
2. **Message Filtering (`MessageFilterService`):**
    - The message is first analyzed. Simple conversational turns like "hello" or "thank you" are identified.
    - All other messages are classified as "complex queries."
3. **Query Routing (`McpChatService`):**
    - **Conversational Queries:** Are sent directly to the LLM for a simple, stateless response.
    - **Complex Queries:** Are passed to the `UniversityRagService` for the full RAG and tool-use pipeline.
4. **RAG Pipeline (`UniversityRagService`):**
    - The user's query is used to perform a hybrid search on the `InMemoryVectorStore` to find relevant data chunks.
    - This retrieved data, the user's query, chat history, and a list of available MCP tools are compiled into a
      detailed prompt for the LLM.
    - The prompt instructs the LLM to **prioritize answering with the retrieved data** and only use the tools if the
      data is insufficient or the query explicitly requires a tool-based action (like calculation).
5. **LLM Response & Tool Use:**
    - The LLM processes the prompt. It might answer directly, or it might decide to call one of the MCP tools.
    - If a tool is called, the MCP server (a separate project) executes the tool and returns the result to the client,
      which forwards it back to the LLM to generate a final answer.
6. **Response to User:** The final, Markdown-formatted answer is sent back to the user's chat window.

## Data Management: The Knowledge Base

The AI's knowledge is derived from two key files. This system allows you to easily manage, update, and version-control
the data that powers the RAG system.

### 1. `university_data.xlsx` (The Source of Truth)

- **Location:** `src/main/resources/data/university_data.xlsx`
- **Purpose:** This Excel file is the human-readable source of truth for the university's data. It contains sheets for
  `Students`, `Courses`, `Research`, and `Grades`.
- **How it's Used:** When the application starts, the `DataLoaderService` reads this file to populate the knowledge
  base. It checks for new records that are not yet in the vector store and processes only the new ones.

### 2. `university_data_generator.html` (Data Generation Tool)

- **Location:** `data/university_data_generator.html`
- **Purpose:** This is a standalone HTML tool for generating large amounts of realistic, randomized sample data. It
  allows you to configure the number of students, courses, etc., and then download a new `university_data.xlsx` file.
- **How to Use:**
    1. Open `university_data_generator.html` in your web browser.
    2. Adjust the desired counts for each data type.
    3. Click the "Generate XLSX File" button.
    4. Save the downloaded `university_data.xlsx` file, and replace the existing one in `src/main/resources/data/`.

### 3. `vector_store.bin` (The AI's "Brain")

- **Location:** `data/vector_store.bin` (in the project's root `data` folder at runtime).
- **Purpose:** This is the binary, machine-readable knowledge base. It contains the text chunks from the XLSX file along
  with their corresponding vector embeddings (mathematical representations). This file is what the application uses for
  fast semantic searches.
- **Lifecycle:**
    1. **Loading:** On startup, the application first looks for this file. If found, it loads it directly into memory.
    2. **Seeding:** If `vector_store.bin` is not found (e.g., on first run), the application processes
       `university_data.xlsx` to create the vector store from scratch.
    3. **Saving:** Any new data processed from the XLSX file is added to the in-memory store, and the entire store is
       saved back to `data/vector_store.bin` on shutdown.
- **Version Control:** It is recommended to **add `data/vector_store.bin` to your `.gitignore` file.** This keeps your
  version-controlled seed data (`.xlsx`) separate from the runtime-generated binary data.

## Setup and Configuration

### Environment Variables

The application is configured via environment variables. Create a `.env` file in the root directory of the parent
project (where your `docker-compose.yml` resides) with the following content:

```plaintext
# -- Azure OpenAI Service Details --
# Replace with your actual Azure credentials and endpoint details.

# Endpoint for the Chat Completions API (e.g., gpt-4o)
AZURE_OPENAI_ENDPOINT=https://YOUR_RESOURCE_NAME.openai.azure.com/

# API Key for your Azure OpenAI resource
AZURE_OPENAI_API_KEY=YOUR_AZURE_OPENAI_API_KEY

# Endpoint for the Embeddings API (e.g., text-embedding-3-large)
# This is often different from the chat endpoint.
AZURE_OPENAI_EMBEDDING_ENDPOINT=https://YOUR_RESOURCE_NAME.openai.azure.com/openai/deployments/YOUR_EMBEDDING_DEPLOYMENT_NAME/embeddings?api-version=2023-05-15
AZURE_OPENAI_EMBEDDING_API_KEY=YOUR_AZURE_OPENAI_API_KEY

# -- MCP Server URL --
# URL of the running MCP Server. This points to the server service in Docker Compose.
MCP_SERVER_URL=http://server:8123/v1/mcp
```

## How to Run

### 1. Running Locally (IDE)

This is ideal for development and debugging.

**Prerequisites:**

- Ensure all items in the [Prerequisites](#prerequisites) section are met.
- A running instance of the `academic-mcp-server` project.

**Steps:**

1. **Build the Project:** Before running, build the entire project from the root directory to ensure all dependencies
   are downloaded.
   ```bash
   # If Java 17 is your system's default JDK
   mvn clean install
   
   # Or, if you have a script for a specific JDK version
   ./mvn17.sh clean install
   ```
2. **Set Environment Variables:** Configure your IDE's run configuration to use the environment variables from the
   `.env` file. Most IDEs (like IntelliJ IDEA) have plugins to do this automatically.
3. **Start the MCP Server:** Run the `academic-mcp-server` application from your IDE. Wait until it has fully started.
4. **Run the Client:** After the server is running, start the `AcademicMcpClient` Spring Boot application. The client
   needs the server to be available during its initialization.
5. **Access the Application:** Open your web browser and navigate to `http://localhost:8234`.

### 2. Running with Docker Compose

This is the recommended way to run the entire system in a production-like environment. The `docker-compose.yml` file is
configured to manage the startup order correctly.

**Prerequisites:**

- Ensure all items in the [Prerequisites](#prerequisites) section are met.

**Steps:**

1. **Build JARs:** Docker Compose needs the compiled JAR files to build the images. Run the Maven build from the
   project's root directory.
   ```bash
   # If Java 17 is your system's default JDK
   mvn clean install
   
   # Or, if you have a script for a specific JDK version
   ./mvn17.sh clean install
   ```
2. **Ensure `.env` file is present:** Make sure your `.env` file is created in the project's root directory (next to
   `docker-compose.yml`).
3. **Start Services:** From the root directory, run the following command:
   ```bash
   docker-compose up --build
   ```
   This command will:
    - Build the Docker images for both the `server` and `client` applications.
    - Start the `server` container first.
    - Wait for the `server` to be healthy before starting the `client` container (this is handled by `depends_on` in
      `docker-compose.yml`).
    - Mount the `./client/data` directory as a volume, ensuring data persistence for `vector_store.bin`.
4. **Access the Application:** Open your web browser and navigate to `http://localhost:8234`.
5. **Stopping the Services:** Press `Ctrl+C` in the terminal, and then run `docker-compose down` to stop and remove the
   containers.

## License

This project is open source and free to use, modify, and distribute under the **MIT License**. (Note: A `LICENSE` file
with the MIT license text should be added to your project root to make this official).
