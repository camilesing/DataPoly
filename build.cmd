@echo off

if not exist datapoly-manager\src\main\resources\index.html (
  echo [WARN] Built-in UI is not built: the package will contain no management UI.
  echo        Build it first with Node 14 (`npm run build`) or Docker (`sh build-ui.sh`),
  echo        see docs/zh/build-deploy.md.
  echo.
)

echo "Clean Project ..."
call mvn clean -f pom.xml

echo "Build Project ..."
call mvn package -f pom.xml -D"maven.test.skip=true"

:exit
pause