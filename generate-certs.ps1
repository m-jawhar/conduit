# SSL Certificate Generation Script for Conduit File Transfer
# Generates self-signed certificates for testing TLS/SSL encryption

Write-Host "=== Conduit SSL Certificate Generator ===" -ForegroundColor Cyan
Write-Host ""

# Configuration
$KEYSTORE_PASSWORD = "changeit"
$VALIDITY_DAYS = 365
$KEY_ALGORITHM = "RSA"
$KEY_SIZE = 2048
$ALIAS = "conduit-server"

# Directories
$CERT_DIR = "certs"
$KEYSTORE_FILE = "$CERT_DIR/server-keystore.jks"
$TRUSTSTORE_FILE = "$CERT_DIR/client-truststore.jks"
$CERT_FILE = "$CERT_DIR/server-cert.crt"

# Create certs directory
if (!(Test-Path $CERT_DIR)) {
    New-Item -ItemType Directory -Path $CERT_DIR | Out-Null
    Write-Host "Created directory: $CERT_DIR" -ForegroundColor Green
}

# Check if keytool is available
$keytool = "keytool"
try {
    & $keytool -help 2>&1 | Out-Null
} catch {
    Write-Host "Error: keytool not found. Please ensure Java JDK is installed and in PATH." -ForegroundColor Red
    exit 1
}

Write-Host "Generating server keystore and self-signed certificate..." -ForegroundColor Yellow

# Generate server keystore with self-signed certificate
& $keytool -genkeypair `
    -alias $ALIAS `
    -keyalg $KEY_ALGORITHM `
    -keysize $KEY_SIZE `
    -validity $VALIDITY_DAYS `
    -keystore $KEYSTORE_FILE `
    -storepass $KEYSTORE_PASSWORD `
    -keypass $KEYSTORE_PASSWORD `
    -dname "CN=localhost, OU=Conduit, O=Development, L=City, ST=State, C=US" `
    -ext "SAN=dns:localhost,ip:127.0.0.1"

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Server keystore created: $KEYSTORE_FILE" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to create server keystore" -ForegroundColor Red
    exit 1
}

# Export certificate from keystore
Write-Host "Exporting server certificate..." -ForegroundColor Yellow
& $keytool -exportcert `
    -alias $ALIAS `
    -keystore $KEYSTORE_FILE `
    -storepass $KEYSTORE_PASSWORD `
    -file $CERT_FILE

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Certificate exported: $CERT_FILE" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to export certificate" -ForegroundColor Red
    exit 1
}

# Create client truststore and import server certificate
Write-Host "Creating client truststore..." -ForegroundColor Yellow
& $keytool -importcert `
    -alias $ALIAS `
    -file $CERT_FILE `
    -keystore $TRUSTSTORE_FILE `
    -storepass $KEYSTORE_PASSWORD `
    -noprompt

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Client truststore created: $TRUSTSTORE_FILE" -ForegroundColor Green
} else {
    Write-Host "✗ Failed to create client truststore" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== SSL Certificates Generated Successfully ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Generated files:" -ForegroundColor White
Write-Host "  - Server keystore:    $KEYSTORE_FILE" -ForegroundColor Gray
Write-Host "  - Client truststore:  $TRUSTSTORE_FILE" -ForegroundColor Gray
Write-Host "  - Server certificate: $CERT_FILE" -ForegroundColor Gray
Write-Host ""
Write-Host "Password: $KEYSTORE_PASSWORD" -ForegroundColor Yellow
Write-Host ""
Write-Host "Usage:" -ForegroundColor White
Write-Host "  Server: java Server 9000 . --ssl $KEYSTORE_FILE $KEYSTORE_PASSWORD" -ForegroundColor Gray
Write-Host "  Client: java Client myfile.pdf localhost 9000 --ssl $TRUSTSTORE_FILE $KEYSTORE_PASSWORD" -ForegroundColor Gray
Write-Host ""
Write-Host "Note: These are self-signed certificates for testing only." -ForegroundColor Yellow
Write-Host "      Do not use in production without proper CA-signed certificates." -ForegroundColor Yellow
