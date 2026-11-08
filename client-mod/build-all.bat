@echo off
rem Builds the Orbit mod for every supported Minecraft version and copies the jars into launcher\resources.
rem Needs JDK 21 (JAVA_HOME). Each version folder is its own Gradle project sharing ..\shared.
setlocal
for %%V in (1.21.11) do (
  echo === Building Orbit for Minecraft %%V ===
  pushd "%~dp0versions\%%V"
  call .\gradlew.bat build --no-daemon -q || (popd & exit /b 1)
  popd
)
echo Done. Jars are in ..\launcher\resources
