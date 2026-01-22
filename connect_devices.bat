@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM connect_devices.bat
REM 交互式连接远程 Android 设备：查询设备清单 → 选择 → 连接 → 退出时断开

set SSH_HOST=root@10.86.32.34
set KEY_FILE_SRC=%~dp0app\src\main\assets\id_ed25519

REM 检查源私钥文件
if not exist "%KEY_FILE_SRC%" (
    echo 错误: 找不到私钥文件: %KEY_FILE_SRC%
    pause
    exit /b 1
)

REM 复制私钥到用户目录并修复权限
set KEY_FILE=%USERPROFILE%\.ssh\droidesk_key
if not exist "%USERPROFILE%\.ssh" mkdir "%USERPROFILE%\.ssh"
copy /Y "%KEY_FILE_SRC%" "%KEY_FILE%" >nul
icacls "%KEY_FILE%" /inheritance:r /grant:r "%USERNAME%:R" >nul 2>&1

:MENU
cls
echo ============================================
echo        远程安卓设备连接工具
echo ============================================
echo.
echo 正在查询设备清单...
echo.

REM 查询服务器上的端口列表
set COUNT=0
set "PORTS="
for /f "tokens=*" %%p in ('ssh -i "%KEY_FILE%" -o UserKnownHostsFile^=/dev/null -o StrictHostKeyChecking^=no %SSH_HOST% "ss -ltn 2>/dev/null | grep -oE ':[0-9]+' | sed 's/://' | sort -n | uniq | awk '$1>=5555 && $1<=6555'" 2^>nul') do (
    set /a COUNT+=1
    set "PORT_!COUNT!=%%p"
    set "PORTS=!PORTS! %%p"
)

if %COUNT%==0 (
    echo 未找到任何在线设备
    echo.
    echo 按任意键刷新...
    pause >nul
    goto MENU
)

echo 在线设备列表（访问码）:
echo ----------------------------------------
for /L %%i in (1,1,%COUNT%) do (
    echo   %%i. 访问码: !PORT_%%i!
)
echo ----------------------------------------
echo   0. 刷新列表
echo   Q. 退出
echo.

set /p CHOICE=请输入选项:

if /i "%CHOICE%"=="Q" goto END
if "%CHOICE%"=="0" goto MENU

REM 验证输入
set /a VALID_CHOICE=%CHOICE% 2>nul
if %VALID_CHOICE% LSS 1 goto MENU
if %VALID_CHOICE% GTR %COUNT% goto MENU

REM 获取选中的端口
set "SELECTED_PORT=!PORT_%CHOICE%!"
echo.
echo 已选择访问码: %SELECTED_PORT%
echo.

REM 启动 SSH 本地转发
echo 正在建立安全隧道...
start /b "" ssh -i "%KEY_FILE%" -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -N -L %SELECTED_PORT%:127.0.0.1:%SELECTED_PORT% %SSH_HOST%

timeout /t 2 /nobreak >nul

REM 连接设备
echo 正在连接设备...
adb connect 127.0.0.1:%SELECTED_PORT%

timeout /t 1 /nobreak >nul

REM 启动 scrcpy（在当前窗口运行，关闭 scrcpy 后自动断开连接）
echo 正在启动屏幕镜像（关闭 scrcpy 窗口后将自动断开连接）...
echo.
scrcpy -s 127.0.0.1:%SELECTED_PORT%

echo.
echo ============================================
echo scrcpy 已关闭
echo ============================================

REM 断开连接
echo.
echo 正在断开连接...
adb disconnect 127.0.0.1:%SELECTED_PORT%

REM 关闭对应的 SSH 隧道进程
for /f "tokens=2 delims=," %%a in ('tasklist /FI "IMAGENAME eq ssh.exe" /FO CSV /NH 2^>nul') do (
    set "PID=%%~a"
    wmic process where "ProcessId=!PID!" get CommandLine 2>nul | findstr /C:"%SELECTED_PORT%" >nul && taskkill /PID !PID! /F >nul 2>&1
)

echo 已断开连接
timeout /t 1 /nobreak >nul

goto MENU

:END
echo.
echo 正在清理所有连接...

REM 断开所有可能的 adb 连接
for /L %%i in (1,1,%COUNT%) do (
    adb disconnect 127.0.0.1:!PORT_%%i! >nul 2>&1
)

REM 关闭所有 droidesk 相关的 ssh 隧道
for /f "tokens=2 delims=," %%a in ('tasklist /FI "IMAGENAME eq ssh.exe" /FO CSV /NH 2^>nul') do (
    set "PID=%%~a"
    wmic process where "ProcessId=!PID!" get CommandLine 2>nul | findstr /C:"droidesk_key" >nul && taskkill /PID !PID! /F >nul 2>&1
)

echo 再见！
timeout /t 1 /nobreak >nul
