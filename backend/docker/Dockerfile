FROM eclipse-temurin:17-jdk

# 작업 디렉토리 설정
WORKDIR /app

# JAR 파일을 컨테이너에 복사(jar파일이 하나만 생기도록 설정해줘야 함.)
COPY build/libs/*.jar app.jar


# 포트 설정
EXPOSE 8080

# 실행 명령어
ENTRYPOINT ["java", "-jar" , "app.jar"]
