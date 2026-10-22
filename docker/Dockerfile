FROM eclipse-temurin:17-jdk-jammy
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/cache/gradle
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*
ARG ANDROID_COMMAND_TOOLS=13114758
RUN curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMAND_TOOLS}_latest.zip" -o /tmp/tools.zip \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && unzip -q /tmp/tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/tools.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
WORKDIR /workspace
ENTRYPOINT ["sh", "./gradlew"]
CMD ["--no-daemon", "--max-workers=2", ":app:assembleDebug"]
