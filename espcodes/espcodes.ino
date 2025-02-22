
#include <WiFi.h>
#include "DHT.h"

// Wi-Fi credentials
const char* ssid = "*****";
const char* password = "*****";

// Server settings
WiFiServer server(12345);

// DHT22 sensor setup
#define DHTPIN 14  // GPIO 14 (D5)
#define DHTTYPE DHT22
DHT dht(DHTPIN, DHTTYPE);

// Define pins for bulbs
#define BULB1 2
#define BULB2 4
#define BULB3 22
#define BULB4 23

// Maximum number of clients
const int maxClients = 3;
int activeClients = 0;

void setup() {
    Serial.begin(115200);

    // Set up bulb control pins
    pinMode(BULB1, OUTPUT);
    pinMode(BULB2, OUTPUT);
    pinMode(BULB3, OUTPUT);
    pinMode(BULB4, OUTPUT);
    digitalWrite(BULB1, LOW);
    digitalWrite(BULB2, LOW);
    digitalWrite(BULB3, LOW);
    digitalWrite(BULB4, LOW);

    // Enable internal pull-up for DHT22
    pinMode(DHTPIN, INPUT_PULLUP);
    dht.begin();

    // Connect to Wi-Fi
    WiFi.begin(ssid, password);
    while (WiFi.status() != WL_CONNECTED) {
        delay(1000);
        Serial.println("Connecting to Wi-Fi...");
    }
    Serial.println("Connected to Wi-Fi!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());

    // Start TCP server
    server.begin();
    Serial.println("TCP server started");
}

void handleClient(void* clientSocket) {
    WiFiClient client = *((WiFiClient*)clientSocket);
    free(clientSocket);

    while (client.connected()) {
        if (client.available()) {
            String command = client.readStringUntil('\n');
            command.trim();
            Serial.println("Command received: " + command);

            if (command == "GET_TEMP_HUMIDITY") {
                delay(1000);  // Give sensor time to stabilize
                float temperature = dht.readTemperature();
                float humidity = dht.readHumidity();

                if (isnan(temperature) || isnan(humidity)) {
                    client.println("Error: Failed to read DHT22");
                } else {
                    client.printf("TEMP: %.2f°C HUMID: %.2f%%\n", temperature, humidity);
                }
            } else if (command.startsWith("TURN_ON_BULB")) {
                int bulbId = command.charAt(12) - '0';
                digitalWrite(getBulbPin(bulbId), HIGH);
                client.printf("Bulb %d is ON\n", bulbId);
            } else if (command.startsWith("TURN_OFF_BULB")) {
                int bulbId = command.charAt(13) - '0';
                digitalWrite(getBulbPin(bulbId), LOW);
                client.printf("Bulb %d is OFF\n", bulbId);
            } else {
                client.println("Unknown command");
            }
        }
        vTaskDelay(10 / portTICK_PERIOD_MS);
    }

    client.stop();
    Serial.println("Client disconnected");
    activeClients--;
    vTaskDelete(NULL);
}

int getBulbPin(int bulbId) {
    switch (bulbId) {
        case 1: return BULB1;
        case 2: return BULB2;
        case 3: return BULB3;
        case 4: return BULB4;
        default: return -1;
    }
}

void loop() {
    WiFiClient client = server.available();
    if (client) {
        if (activeClients < maxClients) {
            Serial.println("New client connected");
            WiFiClient* clientSocket = new WiFiClient(client);
            activeClients++;

            xTaskCreate(
                handleClient,
                "HandleClient",
                8192,
                clientSocket,
                1,
                NULL
            );
        } else {
            Serial.println("Max clients reached. Rejecting new client.");
            client.println("Server busy. Try again later.");
            client.stop();
        }
    }
}
