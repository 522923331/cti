import logging

from fastapi import FastAPI

from api.routes import router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)

app = FastAPI(title="CTI Quant", version="1.0.0")
app.include_router(router)
