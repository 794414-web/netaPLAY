$ErrorActionPreference = 'Continue'
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
Remove-Item Env:DIPLAY_AUTH_ASSETS_DIR -ErrorAction SilentlyContinue
Remove-Item Env:ANDROID_KEYSTORE_PATH -ErrorAction SilentlyContinue
Remove-Item Env:JAVA_OPTS -ErrorAction SilentlyContinue
Set-Location $PSScriptRoot
& .\gradlew.bat :automotive:assembleStandaloneDebug -x lint --console=plain
