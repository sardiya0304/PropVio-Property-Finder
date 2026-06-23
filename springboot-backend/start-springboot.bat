@echo off
REM Kill any existing process on port 8080
for /f "tokens=5" %%p in ('netstat -aon ^| findstr ":8080 " ^| findstr LISTENING 2^>nul') do (
    echo Killing PID %%p on port 8080...
    taskkill /F /PID %%p >nul 2>&1
)

REM Clean stale compiled classes so all Java changes are picked up
if exist "%~dp0target" (
    echo Cleaning target directory...
    rmdir /s /q "%~dp0target"
)

SET DB_USERNAME=root
SET DB_PASSWORD=0304
SET DB_NAME=propvio_db
SET DB_HOST=localhost
SET DB_PORT=3306
SET JWT_SECRET=84607483861b39c39c93adf5dcfde781c8704039c35a9848242bb986d850a957
SET MAIL_USER=ankushsardiya@gmail.com
SET MAIL_PASS=placeholder
SET OPENAI_API_KEY=sk-placeholder-not-needed
SET WEBSITE_URL=http://localhost:5173
SET BACKEND_URL=http://localhost:8080
SET IMAGEKIT_PUBLIC_KEY=public_BbwXeC384KyK3b+bXoG85BEBkfI=
SET IMAGEKIT_PRIVATE_KEY=private_pldW7W3uvvY44DTVaKYTdPW1K/A=
SET IMAGEKIT_URL_ENDPOINT=https://ik.imagekit.io/bd0lxd0ai

REM Strip trailing backslash from %~dp0 to avoid quoting issues with java -D args
SET "BASEDIR=%~dp0"
IF "%BASEDIR:~-1%"=="\" SET "BASEDIR=%BASEDIR:~0,-1%"

echo Starting Spring Boot backend on port 8080...
java -cp "%BASEDIR%\.mvn\wrapper\maven-wrapper.jar" -Dmaven.multiModuleProjectDirectory="%BASEDIR%" org.apache.maven.wrapper.MavenWrapperMain spring-boot:run
