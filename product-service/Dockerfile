FROM python:3.14.8-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY req.txt .

RUN python -m pip install --no-cache-dir -r req.txt

COPY main.py .

USER 10001:10001

EXPOSE 8000

CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]