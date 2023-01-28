FROM amazoncorretto:19-alpine3.17-jdk

RUN mkdir -p /app
# RUN chown appuser /app

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# USER appuser
WORKDIR /app

# USER 185

COPY ./gradlew /app
COPY ./gradle /app/gradle

RUN ./gradlew

EXPOSE 8080

CMD ["./gradlew", "bootRun"]

