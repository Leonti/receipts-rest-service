FROM leonti/sbt_1.2.1_openjdk8-alpine3.7:1.0

RUN apk update && apk add imagemagick

WORKDIR /app

COPY receipts.jar /app

CMD java -jar receipts.jar
