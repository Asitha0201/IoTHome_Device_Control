package com.example.bulbcontrol.controller;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.example.bulbcontrol.client.SessionClient;
import java.util.HashMap;
import java.util.Map;

@Controller
public class BulbViewController {
    private final Map<Integer, Boolean> bulbStates = new HashMap<>();
    private final SessionClient sessionClient;

    public BulbViewController(SessionClient sessionClient) {
        this.sessionClient = sessionClient;
        // Initialize all 4 bulbs
        bulbStates.put(1, false);
        bulbStates.put(2, false);
        bulbStates.put(3, false);
        bulbStates.put(4, false);
    }

    @GetMapping("/")
    public String home(Model model) {
        sessionClient.ensureConnected();
        model.addAttribute("bulbStates", bulbStates);
        model.addAttribute("clientId", sessionClient.getClientId());
        return "bulb-control";
    }

    @PostMapping("/toggle/{bulbId}")
    @ResponseBody
    public Map<String, Object> toggleBulb(@PathVariable int bulbId) {
        Map<String, Object> result = new HashMap<>();

        if (!sessionClient.isConnected()) {
            result.put("success", false);
            result.put("error", "Not connected to ESP32");
            return result;
        }

        boolean currentState = bulbStates.getOrDefault(bulbId, false);
        boolean newState = !currentState;

        String command = newState ? "TURN_ON_BULB" + bulbId : "TURN_OFF_BULB" + bulbId;
        String response = sessionClient.sendCommand(command);

        bulbStates.put(bulbId, newState);

        result.put("success", true);
        result.put("state", newState);
        result.put("response", response);
        result.put("clientId", sessionClient.getClientId());
        return result;
    }

    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("bulbStates", bulbStates);
        status.put("connected", sessionClient.isConnected());
        status.put("clientId", sessionClient.getClientId());
        return status;
    }

    @GetMapping("/sensor")
    @ResponseBody
    public Map<String, Object> getSensorData() {
        Map<String, Object> result = new HashMap<>();

        if (!sessionClient.isConnected()) {
            result.put("success", false);
            result.put("error", "Not connected to ESP32");
            return result;
        }

        String response = sessionClient.getTemperatureAndHumidity();
        result.put("success", true);
        result.put("data", response);
        return result;
    }


    @PreDestroy
    public void cleanup() {
        sessionClient.stop();
    }
}