package com.example.notify.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/rates")
public class ExchangeRateController {

    private final RestClient restClient;

    public ExchangeRateController(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.frankfurter.dev").build();
    }

    @GetMapping("/latest")
    public JsonNode latest(@RequestParam(defaultValue = "USD") String from,
                           @RequestParam(defaultValue = "EUR") String to) {
        return restClient.get()
                .uri(uri -> uri.path("/v1/latest")
                        .queryParam("base", from)
                        .queryParam("symbols", to)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }
}
