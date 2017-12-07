FROM leonti/sbt_1.0.4_openjdk8-alpine3.7:1.0

RUN apk update && apk add imagemagick

WORKDIR /app

COPY receipts.jar /app

CMD java -jar \
    -Dmongodb.db=$MONGODB_DB \
    -Dmongodb.user=$MONGODB_USER \
    -Dmongodb.password=$MONGODB_PASSWORD \
    -Dmongodb.servers.0=$MONGODB_SERVER \
    -Ds3.bucket=$S3_BUCKET \
    -Ds3.accessKey=$S3_ACCESS_KEY \
    -Ds3.secretAccessKey=$S3_SECRET_ACCESS_KEY \
    -Ds3.customEndpoint=$S3_CUSTOM_ENDPOINT \
    -DgoogleClientId=$GOOGLE_CLIENT_ID \
    -DuploadsFolder=$UPLOADS_FOLDER \
    -DtokenSecret=$AUTH_TOKEN_SECRET \
    -DgoogleApiCredentials=$GOOGLE_API_CREDENTIALS \
    -DuseOcrStub=$USE_OCR_STUB \
     receipts.jar
