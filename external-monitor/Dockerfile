FROM python:3.13-slim
WORKDIR /app
COPY config.py store.py monitor.py channels.py delivery.py main.py ./
RUN groupadd --gid 10001 insight && useradd --uid 10001 --gid insight insight && mkdir /data && chown insight:insight /data
USER 10001:10001
ENV PYTHONUNBUFFERED=1
VOLUME ["/data"]
CMD ["python", "main.py"]
