import logging

from fastapi import FastAPI, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from api.routes import router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)

app = FastAPI(title="CTI Quant", version="1.0.0")
app.include_router(router)


@app.exception_handler(Exception)
async def unhandled(request, exc):
    if isinstance(exc, (HTTPException, RequestValidationError)):
        raise exc
    logging.getLogger("cti").exception("calculation failed")
    return JSONResponse(status_code=500, content={"status": "FAILED", "error": str(exc)})
