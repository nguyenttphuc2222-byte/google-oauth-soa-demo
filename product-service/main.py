
from fastapi import FastAPI

app = FastAPI(title="Product Service")


products = [
    {
        "id": 1,
        "name": "Laptop",
        "price": 15000000
    },
    {
        "id": 2,
        "name": "Smartphone",
        "price": 8000000
    },
    {
        "id": 3,
        "name": "Headphone",
        "price": 1500000
    }
]


@app.get("/")
def root():
    return {
        "message": "Product Service is running"
    }


@app.get("/products")
def get_products():
    return products


@app.get("/products/{product_id}")
def get_product(product_id: int):
    for product in products:
        if product["id"] == product_id:
            return product

    return {
        "message": "Product not found"
    }

