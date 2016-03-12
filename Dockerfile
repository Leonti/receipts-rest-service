FROM java:8

COPY receipts-rest-service-assembly-1.0.jar /root/receipts-rest-service-assembly.jar

WORKDIR /root

EXPOSE  9000

CMD ["java", "-jar", "/root/receipts-rest-service-assembly.jar"]
