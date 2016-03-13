FROM java:8

COPY receipts-rest-service-assembly-1.0.jar /root/receipts-rest-service-assembly.jar

WORKDIR /root

EXPOSE  9000

CMD java -jar \
    -Dmongodb.db=$MONGODB_DB \
    -Dmongodb.user=$MONGODB_USER \
    -Dmongodb.password=$MONGODB_PASSWORD \
    -Dmongodb.servers.0=$MONGODB_SERVER \
    -Ds3.bucket=$S3_BUCKET \
    -Ds3.accessKey=$S3_ACCESS_KEY \
    -Ds3.secretAccessKey=$S3_SECRET_ACCESS_KEY \
     /root/receipts-rest-service-assembly.jar
