from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="CTI_")

    app_name: str = "cti-quant"
    host: str = "0.0.0.0"
    port: int = 8090
    mysql_url: str = "mysql+pymysql://cti:cti@127.0.0.1:3306/cti"
    redis_host: str = "127.0.0.1"
    redis_port: int = 6379
    feature_version: str = "features-1.0.0"
    strategy_name: str = "CTI_TREND_V1"
    strategy_version: str = "1.0.0"


settings = Settings()
