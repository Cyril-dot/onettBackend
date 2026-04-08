# 🛒 AI-Powered Marketplace API

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)
![Docker](https://img.shields.io/badge/Docker-Enabled-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

## 📖 Overview

The **AI-Powered Marketplace API** is a robust, scalable, and feature-rich backend solution designed to power modern e-commerce platforms. Built with **Spring Boot** and **Java 17**, it integrates advanced AI capabilities to enhance the shopping experience for users and streamline operations for sellers.

Key features include:
- **AI Shopping Assistant**: Personalized product recommendations and chat support.
- **Smart Search**: Location-based and image-based product search.
- **Seller Tools**: AI-driven listing generation, price suggestions, and inventory analysis.
- **Real-time Messaging**: Seamless communication between buyers and sellers.
- **Secure Authentication**: Role-based access control (RBAC) using JWT.

---

## 🚀 Features

### 🛍️ For Users (Buyers)
- **User Registration & Profile**: Secure sign-up, login, and profile management.
- **Product Discovery**: Browse by category, search by keyword, filter by price/brand, and view trending items.
- **AI Assistant**: Chat with an AI for fashion advice, budget planning, and product comparisons.
- **Smart Cart & Checkout**: Manage cart items and place orders with delivery tracking.
- **Order History**: View past orders and track current deliveries.

### 🏪 For Sellers
- **Store Management**: Create and manage a store profile with verification.
- **Product Management**: Add, update, and delete products with image uploads.
- **AI Seller Tools**:
  - **Listing Generator**: Create compelling product descriptions automatically.
  - **Price Suggester**: Get competitive pricing based on market trends.
  - **Inventory Analysis**: Receive alerts for low stock and slow-moving items.
- **Order Fulfillment**: Manage incoming orders and update delivery statuses.

### 🔧 For Admins
- **Platform Oversight**: Monitor all orders, deliveries, and user activities.
- **Analytics**: View daily order charts and platform summaries.
- **Content Moderation**: Manage categories and ensure platform compliance.

---

## 🛠️ Tech Stack

- **Backend Framework**: Spring Boot 3.x
- **Language**: Java 17
- **Database**: PostgreSQL
- **Security**: Spring Security + JWT (JSON Web Tokens)
- **AI Integration**: Custom AI Service (ASI)
- **Image Storage**: Cloudinary
- **Build Tool**: Maven
- **Containerization**: Docker

---

## ⚙️ Configuration

The application is configured via `application.properties`. Below are the key environment variables you need to set:

### Database
- `DB_HOST`: Database host (default: `localhost`)
- `DB_PORT`: Database port (default: `5432`)
- `DB_NAME`: Database name (default: `marketplace`)
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password

### Security
- `JWT_SECRET`: Secret key for signing JWTs
- `TOKEN_ENCRYPTION_KEY`: AES-256 key for token encryption

### AI Service
- `ASI_API_KEY`: API key for the AI service

### Cloudinary (Image Uploads)
- `CLOUDINARY_CLOUD_NAME`: Your Cloudinary cloud name
- `CLOUDINARY_API_KEY`: Your Cloudinary API key
- `CLOUDINARY_API_SECRET`: Your Cloudinary API secret

---

## 🏃‍♂️ Getting Started

### Prerequisites
- Java 17 SDK
- Maven
- PostgreSQL
- Docker (optional)

### Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/marketplace-api.git
    cd marketplace-api
    ```

2.  **Set up the database:**
    Ensure PostgreSQL is running and create a database named `marketplace`.

3.  **Configure environment variables:**
    Create a `.env` file or set the variables in your IDE/system as listed in the Configuration section.

4.  **Build the project:**
    ```bash
    mvn clean install
    ```

5.  **Run the application:**
    ```bash
    mvn spring-boot:run
    ```
    The API will be available at `http://localhost:8080`.

---

## 🐳 Docker Support

You can run the application and the database using Docker Compose.

1.  **Build the Docker image:**
    ```bash
    docker build -t marketplace-api .
    ```

2.  **Run with Docker Compose:**
    ```bash
    docker-compose up -d
    ```

---

## 📚 API Documentation

Detailed API documentation is available in the `API_DOCUMENTATION.txt` file included in this repository. It covers:

- **Authentication**: Registration, Login, Profile Management.
- **Sellers**: Store & Product Management, AI Tools.
- **Products**: Browsing, Searching, Filtering.
- **Cart & Orders**: Shopping Cart, Checkout, Order Tracking.
- **Delivery**: Delivery Requests & Status Updates.
- **Chat**: Real-time Messaging.
- **AI Features**: Chat, Image Search, Fashion Advice.

**Base URL**: `/api/v1`

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1.  Fork the repository.
2.  Create a new branch (`git checkout -b feature/YourFeature`).
3.  Commit your changes (`git commit -m 'Add some feature'`).
4.  Push to the branch (`git push origin feature/YourFeature`).
5.  Open a Pull Request.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
