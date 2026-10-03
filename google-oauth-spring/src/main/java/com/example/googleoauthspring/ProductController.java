package com.example.googleoauthspring;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ProductController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${product.service.url}")
    private String productServiceUrl;

    @GetMapping("/api/products")
    public List<Product> getProducts() {

        Product[] products =
                restTemplate.getForObject(
                        productServiceUrl,
                        Product[].class
                );

        return Arrays.asList(products);
    }
}