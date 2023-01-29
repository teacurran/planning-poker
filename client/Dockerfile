FROM node:19-alpine

RUN mkdir -p /app

WORKDIR /app

COPY ./package.json ./
COPY ./package-lock.json ./
RUN npm install

EXPOSE 3000