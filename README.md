# Conduit - Java File Transfer System

A robust client-server file transfer application built with Java sockets.

## Features

✅ **SSL/TLS Encryption** - Optional end-to-end encryption for secure file transfers
✅ **Multi-threaded Server** - Handles up to 10 concurrent client connections
✅ **Progress Tracking** - Real-time transfer progress with percentage and data rates
✅ **File Integrity Verification** - MD5 checksum validation ensures corruption-free transfers
✅ **Resumable Transfers** - Automatically resume interrupted transfers from where they left off
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

### 4. (Optional) Enable SSL/TLS Encryption

**Generate SSL certificates:**

```powershell
# Windows PowerShell
.\generate-certs.ps1
```

This creates:

- `certs/server-keystore.jks` - Server keystore
- `certs/client-truststore.jks` - Client truststore
- `certs/server-cert.crt` - Server certificate

**Start server with SSL:**

```bash
java Server 9000 . --ssl certs/server-keystore.jks changeit
```

**Send file with SSL:**

```bash
java Client myfile.pdf localhost 9000 --ssl certs/client-truststore.jks changeit
```

## Usage

### Server

```
java Server [port] [output_directory] [--ssl keystore_path keystore_password]
```

- **port**: Port number to listen on (default: 900)
- **output_directory**: Directory to save received files (default: current directory)
- **--ssl**: Enable SSL/TLS encryption (requires keystore)
- **keystore_path**: Path to Java keystore file (.jks)
- **keystore_password**: Password for the keystore

**Example:**

```bash
java Server 9000 C:\Downloads\received
```

### Client

```
java Client <file_path> [host] [port] [--ssl truststore_path truststore_password]
```

- **file_path**: Path to the file you want to transfer (required)
- **host**: Server hostname or IP address (default: localhost)
- **port**: Server port number (default: 900)
- **--ssl**: Enable SSL/TLS encryption (requires truststore)
- **truststore_path**: Path to Java truststore file (.jks)
- **truststore_password**: Password for the truststore

**Examples:**

```bash
# Send to local server
java Client report.pdf

# Send to remote server
java Client data.zip 192.168.1.50 9000

# Transfer large file
java Client video.mp4 server.example.com 8080

# Send with SSL encryption
java Client secret.zip localhost 9000 --ssl certs/client-truststore.jks changeit
```

## Features in Detail

### SSL/TLS Encryption

Optional end-to-end encryption protects file transfers from eavesdropping:

- Uses Java's built-in SSL/TLS support (javax.net.ssl)
- Supports TLS 1.2+ with strong cipher suites
- Server authenticates with X.509 certificate
- Self-signed certificates for testing (generate-certs.ps1)
- Use CA-signed certificates for production

**Security Notes:**

- Always use SSL in production environments
- Self-signed certificates are for testing only
- Keep private keys secure and never share them
- Use strong passwords for keystores/truststores

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

### Resumable Transfers

Transfers automatically resume if interrupted:

- Server stores incomplete files with `.partial` extension
- Client reconnects and resumes from last byte transferred
- No data loss - picks up exactly where it left off
- Works across connection drops, crashes, or manual interruptions

**Example Resume:**

```
⚠ Resuming previous transfer from: 25.60 MB
Already transferred: 25.60 MB / 51.20 MB
Skipped to offset: 25.60 MB
Sending remaining: 25.60 MB
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

- **Encryption**: TLS 1.2+ with strong cipher suites (optional)
- **Buffer Size**: 64 KB for optimal performance
- **Max Concurrent Clients**: 10
- **Supported File Types**: All (binary-safe transfer)
- **File Size Limit**: Limited only by available disk space and memory
- **Network Protocol**: TCP (reliable, ordered delivery)
- **Resume Protocol**: Byte-offset based with .partial files

## Folder Structure

```
conduit/
├── src/
│   ├── Server.java         # Multi-threaded SSL server
│   ├── Client.java         # Client with SSL support
├── certs/                  # SSL certificates (after running generate-certs.ps1)
│   ├── server-keystore.jks
│   ├── client-truststore.jks
│   └── server-cert.crt
├── generate-certs.ps1      # Certificate generation script
├── lib/                    # Dependencies (currently empty)
└── README.md               # This file
```

## Error Handling

The application handles common errors gracefully:

- File not found on client side
- Network connection failures and automatic resume
- SSL/TLS handshake failures with clear error messages
- Permission denied errors
- Corrupted file transfers (checksum mismatch)
- Invalid port numbers or SSL certificate issues
- Non-existent output directories
- Interrupted transfers (automatically resume on reconnect)

## Limitations & Future Improvements

**Current Limitations:**

- No authentication mechanism (SSL only provides encryption, not auth)
- MD5 is not cryptographically secure (but sufficient for integrity checking)
- Resume requires same filename on reconnect
- Self-signed certificates require manual trust

**Potential Improvements:**

- Implement user authentication (username/password or certificate-based)
- Replace MD5 with SHA-256
- Add compression support
- Parallel chunk transfers for faster speeds
- Support for CA-signed certificates
- Web-based management interface

## Examples

### Local Testing (Plain-text)

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

### Local Testing (SSL/TLS)

```powershell
# Generate certificates first
.\generate-certs.ps1

# Terminal 1 - Start SSL server
cd src
javac Server.java
java Server 9000 received_files --ssl ../certs/server-keystore.jks changeit

# Terminal 2 - Send file with SSL
cd src
javac Client.java
java Client ../README.md localhost 9000 --ssl ../certs/client-truststore.jks changeit
```

### Remote Server Setup

```bash
# On server machine (192.168.1.100) - with SSL
java Server 8080 /var/file_storage --ssl certs/server-keystore.jks your_password

# On client machine - with SSL
java Client important_data.zip 192.168.1.100 8080 --ssl certs/client-truststore.jks your_password
```

## License

This is an educational project for learning Java socket programming and SSL/TLS.
