# Oxygen — сервер без окна: симуляция на полной скорости, просмотр и управление через веб.
# Сборка: docker build -t oxygen-server .   Запуск — см. docker-compose.yml и docs/docker-server.md
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src ./src
COPY test ./test
RUN javac -encoding UTF-8 -d /app src/sct/*.java src/sct/plants/*.java src/sct/neuro/*.java \
 && cp -r src/sct/web /app/sct/ \
 && javac -encoding UTF-8 -cp /app -d /tests test/sct/*.java \
 && java -Djava.awt.headless=true -cp /app:/tests sct.SelfTest

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home oxygen \
 && mkdir -p "/data/saved worlds" && chown -R oxygen /data
COPY --from=build /app /app
WORKDIR /data
USER oxygen
# 8080 — просмотр (только чтение), 8081 — управление
EXPOSE 8080 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    OXYGEN_ARGS="--resume --autosave 10"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1
# exec — java получает SIGTERM от docker stop и успевает сохранить мир (autosave-on-exit)
ENTRYPOINT ["sh", "-c", "exec java -Djava.awt.headless=true $JAVA_OPTS -cp /app sct.Server --view-port 8080 --control-port 8081 $OXYGEN_ARGS \"$@\"", "--"]
