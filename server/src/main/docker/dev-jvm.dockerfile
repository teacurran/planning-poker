FROM maven:3.8.7-amazoncorretto-19

RUN mkdir -p /app
# RUN chown appuser /app

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# USER appuser
WORKDIR /app

EXPOSE 8080
