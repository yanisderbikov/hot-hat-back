FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# FFmpeg is required by the free mock-provider integration tests at build time.
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -B -Dtest=NeuroScenePlanTest,NeuroReplacementPilotServiceTest,NeuroReplacementPipelineSimulationTest,NeuroPaidKlingReadTest,NeuroUploadResumeTest,GlobalExceptionHandlerTest,TestRoomBotReplacementTest,ReleaseTestBotSeatUseCaseTest,TestBotInitialRoomSetupTest,LiveAvatarSocketHandlerTest,AiVoiceSeatPolicyTest,AiVoiceSeatServiceTest,AiVoiceScriptedPartnerTest,AiVoiceDuoRoleTest,MatchPresenceExpectedRosterTest,AiVoiceDispatchSchedulerTest,AiVoiceSmallTalkServiceTest,AiVoiceGreetingTest,AiVoiceWordMemoryServiceTest,AiVoiceSkipCommandTest,PrepareYoutubeMemeClipUseCaseTest,YoutubeMemeClipTest,YoutubeMemeSearchControllerTest test
RUN mvn clean package -DskipTests

# yt-dlp 2026.x requires Node.js >=22 for YouTube's JS player challenge.
# Debian/Ubuntu apt "nodejs" is unpinned and can be Node 18; copy a known
# supported runtime from the official image instead.
FROM node:22-bookworm-slim AS youtube_node
FROM eclipse-temurin:21-jre
# The extractor and FFmpeg run on HOT-HAT's server; players install nothing.
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg python3 python3-venv \
    && python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir yt-dlp==2026.10.26 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=youtube_node /usr/local/bin/node /usr/local/bin/node
RUN node --version && node -e 'if(Number(process.versions.node.split(".")[0]) < 22) process.exit(1)' \
    && /opt/yt-dlp/bin/yt-dlp --version
ENV PATH="/opt/yt-dlp/bin:$PATH"
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-XX:MaxMetaspaceSize=256m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
