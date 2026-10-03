
# Google OAuth 2.0 Login Integration

A simple demonstration of Google OAuth 2.0 / OpenID Connect authentication integrated with Spring Boot, Spring Security, and a FastAPI Product Service.

## 1. Project Overview

This project demonstrates how to integrate Google Login into a Spring Boot application using OAuth 2.0 and OpenID Connect (OIDC).

After logging in with Google, the authenticated user can access a product page. The product data is provided by a separate FastAPI microservice through a REST API.

### Technologies

- Java
- Spring Boot
- Spring Security
- OAuth 2.0
- OpenID Connect (OIDC)
- Thymeleaf
- FastAPI
- Python
- REST API
- Google Cloud OAuth 2.0

---

## 2. Project Architecture

The system contains two main services:

1. Spring Boot Application
2. FastAPI Product Service

The authentication is handled by Google, while Spring Boot manages the OAuth2 login process.

```text
                    Google
                       |
                  OAuth2 / OIDC
                       |
                       v
              +------------------+
              |   Spring Boot    |
              | Spring Security  |
              |  Google Login    |
              |                  |
              | localhost:8080   |
              +--------+---------+
                       |
                    REST API
                       |
                       v
              +------------------+
              |     FastAPI      |
              | Product Service  |
              |                  |
              | localhost:8000   |
              +------------------+


              