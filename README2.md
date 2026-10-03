# Google OAuth 2.0 Login Integration

A simple demonstration of Google OAuth 2.0 / OpenID Connect authentication integrated with Spring Boot, Spring Security, and a FastAPI Product Service.

---

## 1. Project Overview

This project demonstrates how to integrate Google Login into a Spring Boot application using OAuth 2.0 and OpenID Connect (OIDC).

After logging in with Google, the authenticated user can access the product page. Product data is provided by a separate FastAPI microservice through a REST API.

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

## 2. Business Problem

Many applications need a secure and convenient authentication system.

Building a complete username/password authentication system requires password management, password security, account recovery, and authentication protection.

Google OAuth 2.0 / OpenID Connect allows applications to use Google for user authentication.

This provides a simple login experience and reduces the need for the application to manage user passwords.

---

## 3. Main Features

### Google Login

Users can sign in using their Google account.

The application receives basic user information such as:

- Full name
- Email
- Profile picture

The application does not store the user's Google password.

### User Home

After successful authentication, users are redirected to the User Home page.

The page displays:

- Google profile picture
- User name
- User email
- View Products button
- Logout button

### Product Service

The FastAPI service provides product data through REST API endpoints.

Example products:

- Laptop
- Smartphone
- Headphone

### Product Page

Spring Boot requests product data from FastAPI and displays the result using a Thymeleaf HTML page.

---

## 4. Project Architecture

The system contains two main services:

1. Spring Boot Application
2. FastAPI Product Service

Google handles authentication, while Spring Security manages the OAuth2 login process.

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


              