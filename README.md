# Conduit - Java File Transfer System

A robust client-server file transfer application built with Java sockets.

## Features

✅ **Multi-threaded Server** - Handles up to 10 concurrent client connections
✅ **Progress Tracking** - Real-time transfer progress with percentage and data rates
✅ **File Integrity Verification** - MD5 checksum validation ensures corruption-free transfers
✅ **Flexible Configuration** - Command-line arguments for host, port, and output directory
✅ **Automatic File Renaming** - Prevents overwriting existing files on the server
✅ **Large File Support** - 64KB buffer size for efficient large file transfers
✅ **Comprehensive Error Handling** - Graceful handling of network and file system errors

## Quick Start

### 1. Compile the project

```bash
cd src
javac Server.java Client.java
```

### 2. Start the Server

```bash
# Use defaults (port 900, current directory)
java Server

# Custom port and output directory
java Server 8080 D:\received_files
```

### 3. Send a file from Client

```bash
# Basic usage (connects to localhost:900)
java Client myfile.pdf

# Specify server and port
java Client document.docx 192.168.1.100 8080
```

## Usage

### Server

```
java Server [port] [output_directory]
```

- **port**: Port number to listen on (default: 900)
- **output_directory**: Directory to save received files (default: current directory)

**Example:**

```bash
java Server 9000 C:\Downloads\received
```

### Client

```
java Client <file_path> [host] [port]
```

- **file_path**: Path to the file you want to transfer (required)
- **host**: Server hostname or IP address (default: localhost)
- **port**: Server port number (default: 900)

**Examples:**

```bash
# Send to local server
java Client report.pdf

# Send to remote server
java Client data.zip 192.168.1.50 9000

# Transfer large file
java Client video.mp4 server.example.com 8080
```

## Features in Detail

### Multi-Client Support

The server uses a thread pool to handle up to 10 concurrent clients, allowing multiple file transfers simultaneously without blocking.

### Progress Tracking

Both client and server display transfer progress:

```
Progress: 10% (5.12 MB / 51.20 MB)
Progress: 20% (10.24 MB / 51.20 MB)
...
Progress: 100% (51.20 MB / 51.20 MB)
```

### File Integrity Verification

Every transfer is verified using MD5 checksums:

- Client calculates MD5 before sending
- Server calculates MD5 after receiving
- Automatic verification with clear success/failure indication

### Automatic Duplicate Handling

If a file with the same name exists, the server automatically renames it:

- `document.pdf` → `document_1.pdf` → `document_2.pdf`, etc.

## Technical Details

- **Buffer Size**: 64 KB for optimal performance
- **Max Concurrent Clients**: 10
- **Supported File Types**: All (binary-safe transfer)
- **File Size Limit**: Limited only by available disk space and memory
- **Network Protocol**: TCP (reliable, ordered delivery)

## Folder Structure

```
conduit/
├── src/
│   ├── Server.java    # Multi-threaded server implementation
│   ├── Client.java    # Client with progress tracking
│   └── App.java       # Basic hello world (not used)
├── lib/               # Dependencies (currently empty)
└── README.md          # This file
```

## Error Handling

The application handles common errors gracefully:

- File not found on client side
- Network connection failures
- Permission denied errors
- Corrupted file transfers (checksum mismatch)
- Invalid port numbers
- Non-existent output directories

## Limitations & Future Improvements

**Current Limitations:**

- No encryption (transfers are in plain text)
- No authentication mechanism
- No pause/resume functionality
- MD5 is not cryptographically secure

**Potential Improvements:**

- Add SSL/TLS encryption
- Implement user authentication
- Support for pause/resume transfers
- Replace MD5 with SHA-256
- Add compression support
- Web-based management interface

## Examples

### Local Testing

```bash
# Terminal 1 - Start server
cd src
javac Server.java
java Server 9000 received_files

# Terminal 2 - Send file
cd src
javac Client.java
java Client ../README.md localhost 9000
```

### Remote Server Setup

```bash
# On server machine (192.168.1.100)
java Server 8080 /var/file_storage

# On client machine
java Client important_data.zip 192.168.1.100 8080
```

## License

This is an educational project for learning Java socket programming.
