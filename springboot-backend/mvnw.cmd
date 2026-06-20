@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET %%i=
@echo off

@SET MAVEN_PROJECTBASEDIR=%~dp0
@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

@IF NOT EXIST %WRAPPER_JAR% (
  @echo Downloading Maven Wrapper...
  powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile %WRAPPER_JAR%"
)

@SET JAVA_EXE=java.exe
%JAVA_EXE% -cp %WRAPPER_JAR% -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
