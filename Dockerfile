FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# FFmpeg is required by the free mock-provider integration tests at build time.
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -B -Dtest=NeuroScenePlanTest,NeuroReplacementPilotServiceTest,NeuroReplacementPipelineSimulationTest,NeuroPaidKlingReadTest,NeuroUploadResumeTest,GlobalExceptionHandlerTest,TestRoomBotReplacementTest,ReleaseTestBotSeatUseCaseTest,TestBotInitialRoomSetupTest,LiveAvatarSocketHandlerTest,AiVoiceSeatPolicyTest,AiVoiceSeatServiceTest,AiVoiceScriptedPartnerTest,AiVoiceDuoRoleTest,MatchPresenceExpectedRosterTest,AiVoiceDispatchSchedulerTest,AiVoiceSmallTalkServiceTest,AiVoiceGreetingTest,AiVoiceWordMemoryServiceTest,AiVoiceSkipCommandTest,PrepareYoutubeMemeClipUseCaseTest,YoutubeMemeClipTest,YoutubeMemeSearchControllerTest,EphemeralMemeClipStoreTest test
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
# The extractor and FFmpeg run on HOT-HAT's server; players install nothing.
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg python3 python3-venv nodejs \
    && python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir yt-dlp==2026.10.26 \
    && rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/yt-dlp/bin:$PATH"
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-XX:MaxMetaspaceSize=256m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
