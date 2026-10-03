package com.example.googleoauthspring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Controller
public class ProductPageController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${product.service.url}")
    private String productServiceUrl;

    @GetMapping("/products")
    public String products(Model model) {

        Product[] products =
                restTemplate.getForObject(
                        productServiceUrl,
                        Product[].class
                );

        model.addAttribute("products", products);

        return "products";
    }
}