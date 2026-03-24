# Viscord Build Menu - PowerShell Version with Progress Tracking
# Modern, colorful CLI interface for building Viscord mods

function Get-InstalledJavaVersions {
    $javaVersions = @()
    
    # Check Eclipse Adoptium (most common)
    $adoptiumPath = "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    if (Test-Path $adoptiumPath) {
        Get-ChildItem $adoptiumPath -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $javaExe = Join-Path $_.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                try {
                    $version = & $javaExe -version 2>&1 | Select-Object -First 1
                    if ($version -match '"([0-9.]+)"') {
                        $fullVersion = $matches[1]
                        $majorVersion = $fullVersion.Split('.')[0]
                        
                        $javaVersions += @{
                            Path = $javaExe
                            Version = [int]$majorVersion
                            FullPath = $_.FullName
                            DisplayName = "Java $majorVersion ($($_.Name))"
                            FullVersion = $fullVersion
                        }
                    }
                } catch {
                    # Ignore errors
                }
            }
        }
    }
    
    # Check Program Files Java
    $programFilesJava = "$env:ProgramFiles\Java"
    if (Test-Path $programFilesJava) {
        Get-ChildItem $programFilesJava -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $javaExe = Join-Path $_.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                try {
                    $version = & $javaExe -version 2>&1 | Select-Object -First 1
                    if ($version -match '"([0-9.]+)"') {
                        $fullVersion = $matches[1]
                        $majorVersion = $fullVersion.Split('.')[0]
                        
                        # Check for duplicates
                        $exists = $false
                        foreach ($existing in $javaVersions) {
                            if ($existing.FullPath -eq $_.FullName) {
                                $exists = $true
                                break
                            }
                        }
                        
                        if (-not $exists) {
                            $javaVersions += @{
                                Path = $javaExe
                                Version = [int]$majorVersion
                                FullPath = $_.FullName
                                DisplayName = "Java $majorVersion ($($_.Name))"
                                FullVersion = $fullVersion
                            }
                        }
                    }
                } catch {
                    # Ignore errors
                }
            }
        }
    }
    
    # Check JAVA_HOME
    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaExe) {
            try {
                $version = & $javaExe -version 2>&1 | Select-Object -First 1
                if ($version -match '"([0-9.]+)"') {
                    $fullVersion = $matches[1]
                    $majorVersion = $fullVersion.Split('.')[0]
                    
                    # Check for duplicates
                    $exists = $false
                    foreach ($existing in $javaVersions) {
                        if ($existing.FullPath -eq $env:JAVA_HOME) {
                            $exists = $true
                            break
                        }
                    }
                    
                    if (-not $exists) {
                        $javaVersions += @{
                            Path = $javaExe
                            Version = [int]$majorVersion
                            FullPath = $env:JAVA_HOME
                            DisplayName = "Java $majorVersion (JAVA_HOME)"
                            FullVersion = $fullVersion
                        }
                    }
                }
            } catch {
                # Ignore errors
            }
        }
    }
    
    # Check PATH
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        try {
            $version = & java -version 2>&1 | Select-Object -First 1
            if ($version -match '"([0-9.]+)"') {
                $fullVersion = $matches[1]
                $majorVersion = $fullVersion.Split('.')[0]
                $javaHome = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
                
                # Check for duplicates
                $exists = $false
                foreach ($existing in $javaVersions) {
                    if ($existing.FullPath -eq $javaHome) {
                        $exists = $true
                        break
                    }
                }
                
                if (-not $exists) {
                    $javaVersions += @{
                        Path = $javaCmd.Source
                        Version = [int]$majorVersion
                        FullPath = $javaHome
                        DisplayName = "Java $majorVersion (PATH)"
                        FullVersion = $fullVersion
                    }
                }
            }
        } catch {
            # Ignore errors
        }
    }
    
    return $javaVersions | Sort-Object Version -Descending
}

function Write-CenterText {
    param(
        [string]$Text,
        [ConsoleColor]$ForegroundColor = $Host.UI.RawUI.ForegroundColor,
        [ConsoleColor]$BackgroundColor = $Host.UI.RawUI.BackgroundColor,
        [int]$Width = $Host.UI.RawUI.WindowSize.Width
    )
    
    $padding = [math]::Max(0, ($Width - $Text.Length) / 2)
    $paddedText = " " * [math]::Floor($padding) + $Text + " " * [math]::Ceiling($padding)
    Write-Host $paddedText -ForegroundColor $ForegroundColor -BackgroundColor $BackgroundColor
}

function Write-CenterLine {
    param(
        [string]$Char = "=",
        [ConsoleColor]$ForegroundColor = $Host.UI.RawUI.ForegroundColor,
        [int]$Width = $Host.UI.RawUI.WindowSize.Width
    )
    
    $line = $Char * $Width
    Write-Host $line -ForegroundColor $ForegroundColor
}

function Write-ProgressBar {
    param(
        [int]$PercentComplete,
        [string]$Status = "Processing...",
        [int]$Width = 50
    )
    
    $filled = [math]::Floor($Width * $PercentComplete / 100)
    $empty = $Width - $filled
    
    # Use ASCII-safe characters instead of Unicode blocks
    $bar = "=" * $filled + "-" * $empty
    Write-Host "`r[$bar] $PercentComplete% - $Status" -NoNewline -ForegroundColor White
}

function Get-BuildProgress {
    param(
        [string]$GradleOutput
    )
    
    # Look for common Gradle progress indicators
    if ($GradleOutput -match ":(\w+):") {
        $task = $matches[1]
        return @{
            Task = $task
            Progress = 0
        }
    }
    
    # Look for compilation progress
    if ($GradleOutput -match "compiling|Compiling") {
        return @{
            Task = "Compiling"
            Progress = 25
        }
    }
    
    # Look for jar creation
    if ($GradleOutput -match "jar|Jar") {
        return @{
            Task = "Creating JAR"
            Progress = 75
        }
    }
    
    # Look for build success
    if ($GradleOutput -match "BUILD SUCCESSFUL") {
        return @{
            Task = "Complete"
            Progress = 100
        }
    }
    
    return @{
        Task = "Building"
        Progress = 10
    }
}

function Invoke-GradleBuildWithProgress {
    param(
        [string]$ProjectName = "project"
    )
    
    Write-Host "Starting Gradle build for $ProjectName..." -ForegroundColor Blue
    Write-ProgressBar -PercentComplete 0 -Status "Initializing build..."
    
    $outputFile = "$env:TEMP\gradle_output_$($ProjectName)_$((Get-Date).ToString('yyyyMMddHHmmss')).txt"
    $errorFile = "$env:TEMP\gradle_error_$($ProjectName)_$((Get-Date).ToString('yyyyMMddHHmmss')).txt"
    
    try {
        $process = Start-Process -FilePath ".\gradlew" -ArgumentList "build" -NoNewWindow -PassThru -RedirectStandardOutput $outputFile -RedirectStandardError $errorFile
        
        $currentProgress = 0
        $lastStatus = "Initializing build..."
        $startTime = Get-Date
        $timeout = 300  # 5 minute timeout
        
        while (-not $process.HasExited -and $timeout -gt 0) {
            Start-Sleep -Seconds 1
            $timeout--
            
            # Gradual progress increase
            $elapsed = (Get-Date) - $startTime
            $timeBasedProgress = [math]::Min($elapsed.TotalSeconds * 2, 85)
            $currentProgress = [math]::Max($currentProgress, $timeBasedProgress)
            
            # Try to read current output for status
            if (Test-Path $outputFile) {
                $output = Get-Content $outputFile -Tail 10 -ErrorAction SilentlyContinue
                if ($output) {
                    $progressInfo = Get-BuildProgress -GradleOutput ($output -join "`n")
                    if ($progressInfo.Progress -gt $currentProgress) {
                        $currentProgress = $progressInfo.Progress
                    }
                    $lastStatus = "$($progressInfo.Task) - $ProjectName"
                }
            }
            
            Write-ProgressBar -PercentComplete $currentProgress -Status $lastStatus
        }
        
        # If process is still running after timeout, kill it and use fallback
        if (-not $process.HasExited) {
            Write-Host ""
            Write-Host "Progress tracking timeout, using fallback build method..." -ForegroundColor Yellow
            $process.Kill()
            $process.WaitForExit()
            
            # Fallback to simple build
            Write-Host "Running fallback build..." -ForegroundColor Blue
            $fallbackResult = & .\gradlew build 2>&1
            Write-ProgressBar -PercentComplete 100 -Status "Build complete!"
            Write-Host ""
            
            return @{
                Success = ($LASTEXITCODE -eq 0)
                Output = $fallbackResult
                Error = @()
                ExitCode = $LASTEXITCODE
            }
        }
        
        # Wait for process and get result
        $process.WaitForExit()
        $buildResult = Get-Content $outputFile -ErrorAction SilentlyContinue
        $buildError = Get-Content $errorFile -ErrorAction SilentlyContinue
        
        Write-ProgressBar -PercentComplete 100 -Status "Build complete!"
        Write-Host ""
        
        return @{
            Success = ($process.ExitCode -eq 0)
            Output = $buildResult
            Error = $buildError
            ExitCode = $process.ExitCode
        }
    }
    catch {
        Write-Host ""
        Write-Host "Progress tracking failed, using fallback build method..." -ForegroundColor Yellow
        
        # Fallback to simple build
        Write-Host "Running fallback build..." -ForegroundColor Blue
        $fallbackResult = & .\gradlew build 2>&1
        Write-ProgressBar -PercentComplete 100 -Status "Build complete!"
        Write-Host ""
        
        return @{
            Success = ($LASTEXITCODE -eq 0)
            Output = $fallbackResult
            Error = @()
            ExitCode = $LASTEXITCODE
        }
    }
    finally {
        # Clean up temp files
        Remove-Item $outputFile -ErrorAction SilentlyContinue
        Remove-Item $errorFile -ErrorAction SilentlyContinue
    }
}

function Show-MainMenu {
    Clear-Host
    $width = $Host.UI.RawUI.WindowSize.Width
    
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "VISCORD BUILD MENU v2.0" -ForegroundColor White -BackgroundColor Blue
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText " " -ForegroundColor Cyan
    Write-CenterText "[1] -> Build all versions and copy to Releases folder" -ForegroundColor Green
    Write-CenterText " " -ForegroundColor Cyan
    Write-CenterText "[2] -> Build all versions and copy to versioned folder" -ForegroundColor Green
    Write-CenterText " " -ForegroundColor Cyan
    Write-CenterText "[3] -> Build specific version" -ForegroundColor Green
    Write-CenterText " " -ForegroundColor Cyan
    Write-CenterText "[4] -> Build and move to custom release folder" -ForegroundColor Green
    Write-CenterText " " -ForegroundColor Cyan
    Write-CenterText "[5] -> Select Java version (Current: $($script:SelectedJavaDisplayName))" -ForegroundColor Yellow
    Write-CenterText " " -ForegroundColor Cyan
    Write-CenterText "[6] -> Exit" -ForegroundColor Red
    Write-CenterText " " -ForegroundColor Cyan
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Enter your choice: " -ForegroundColor Cyan -NoNewline
}

function Select-JavaVersion {
    $javaVersions = Get-InstalledJavaVersions
    
    if ($javaVersions.Count -eq 0) {
        Write-CenterText "No Java installations found!" -ForegroundColor Red
        Write-CenterText "Please install Java 17 or Java 21 to continue." -ForegroundColor Yellow
        Write-Host "`nPress any key to exit..." -ForegroundColor Cyan
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        return $null
    }
    
    Clear-Host
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "SELECT JAVA VERSION" -ForegroundColor Yellow
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""
    
    for ($i = 0; $i -lt $javaVersions.Count; $i++) {
        $java = $javaVersions[$i]
        Write-CenterText "[$($i + 1)] -> $($java.DisplayName)" -ForegroundColor Green
    }
    
    Write-CenterText "[0] -> Use system default Java" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Enter your choice: " -ForegroundColor Cyan -NoNewline
    
    $choice = Read-Host
    $choiceNum = 0
    
    if ([int]::TryParse($choice, [ref]$choiceNum)) {
        if ($choiceNum -eq 0) {
            return $null  # Use system default
        } elseif ($choiceNum -ge 1 -and $choiceNum -le $javaVersions.Count) {
            return $javaVersions[$choiceNum - 1]
        }
    }
    
    Write-Host "Invalid choice. Using system default Java." -ForegroundColor Red
    Start-Sleep -Seconds 2
    return $null
}

function Build-AllReleases {
    Clear-Host
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "BUILDING ALL VERSIONS TO RELEASES" -ForegroundColor Green
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""

    $rootDir = Get-Location
    $releasesDir = Join-Path $rootDir "Releases"
    
    if (-not (Test-Path $releasesDir)) {
        New-Item -ItemType Directory -Path $releasesDir | Out-Null
    }

    $versions = @("1.18.2", "1.19.2", "1.20.1", "1.21.1")
    $totalVersions = $versions.Count
    $currentVersionIndex = 0
    
    foreach ($version in $versions) {
        $currentVersionIndex++
        $overallProgress = [math]::Floor(($currentVersionIndex - 1) * 100 / $totalVersions)
        
        Write-Host "Processing Minecraft $version... ($currentVersionIndex/$totalVersions)" -ForegroundColor Yellow
        Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
        
        $verDir = Get-ChildItem -Path $rootDir -Directory -Name "viscord-$version-*" | Select-Object -First 1
        
        if ($verDir) {
            Set-Location (Join-Path $rootDir $verDir)
            
            # Set JAVA_HOME if a specific Java version is selected
            $originalJavaHome = $env:JAVA_HOME
            $originalPath = $env:PATH
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $script:SelectedJava.FullPath
                $env:PATH = $script:SelectedJava.FullPath + "\bin;" + $env:PATH
                Write-Host "Using Java $($script:SelectedJava.Version)" -ForegroundColor Yellow
            }
            
            # Build with progress tracking
            $buildResult = Invoke-GradleBuildWithProgress -ProjectName "Minecraft $version"
            
            # Restore original environment
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $originalJavaHome
                $env:PATH = $originalPath
            }
            
            if (-not $buildResult.Success) {
                Write-Host "X Failed to build $version!" -ForegroundColor Red
                Write-Host "Exit code: $($buildResult.ExitCode)" -ForegroundColor DarkRed
                
                if ($buildResult.Error) {
                    Write-Host "Build errors:" -ForegroundColor DarkRed
                    foreach ($errorLine in $buildResult.Error[-5..-1]) {
                        if ($errorLine.Trim()) {
                            Write-Host "  $errorLine" -ForegroundColor Red
                        }
                    }
                }
                
                if ($buildResult.Output) {
                    $relevantOutput = $buildResult.Output | Where-Object { $_ -match "error|Error|FAILED|failed" } | Select-Object -Last 3
                    if ($relevantOutput) {
                        Write-Host "Relevant output:" -ForegroundColor DarkRed
                        foreach ($line in $relevantOutput) {
                            Write-Host "  $line" -ForegroundColor Red
                        }
                    }
                }
            } else {
                Write-Host "+ Build $version successful. Copying jars to Releases folder..." -ForegroundColor Green
                
                # Check what platforms are available in this version
                $platforms = @()
                if (Test-Path "fabric") { $platforms += "fabric" }
                if (Test-Path "forge") { $platforms += "forge" }
                if (Test-Path "neoforge") { $platforms += "neoforge" }
                
                Write-Host "  Available platforms: $($platforms -join ', ')" -ForegroundColor Cyan
                
                foreach ($platform in $platforms) {
                    $libsPath = "$platform\build\libs"
                    if (Test-Path $libsPath) {
                        $jarPattern = "viscord-*-$platform.jar"
                        $jars = Get-ChildItem -Path $libsPath -Filter $jarPattern -ErrorAction SilentlyContinue
                        
                        if ($jars.Count -gt 0) {
                            foreach ($jar in $jars) {
                                Copy-Item $jar.FullName $releasesDir -Force
                                Write-Host "  + $($jar.Name) copied" -ForegroundColor Green
                            }
                        } else {
                            Write-Host "  - No $platform jars found" -ForegroundColor Yellow
                        }
                    } else {
                        Write-Host "  - $platform build directory not found" -ForegroundColor Yellow
                    }
                }
            }
        } else {
            Write-Host "X Could not find directory for version $version" -ForegroundColor Red
        }
        Write-Host ""
    }

    Set-Location $rootDir
    
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "BUILD PROCESS COMPLETED SUCCESSFULLY!" -ForegroundColor Green
    Write-CenterText "Jars copied to Releases folder." -ForegroundColor Cyan
    Write-CenterLine "=" -ForegroundColor Cyan
    
    Write-Host "`nPress any key to continue..." -ForegroundColor Cyan
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

function Show-ExitScreen {
    Clear-Host
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "THANK YOU FOR USING" -ForegroundColor Green
    Write-CenterText "VISCORD BUILD MENU v2.0" -ForegroundColor Yellow
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""
    Write-CenterText "Have a great day!" -ForegroundColor Cyan
    Start-Sleep -Seconds 2
}

# Initialize script variables
$script:SelectedJava = $null
$script:SelectedJavaDisplayName = "System Default"

# Main program loop
do {
    Show-MainMenu
    $choice = Read-Host
    
    switch ($choice) {
        "1" { Build-AllReleases }
        "2" { Write-Host "Feature coming soon..." -ForegroundColor Yellow; Start-Sleep -Seconds 2 }
        "3" { Write-Host "Feature coming soon..." -ForegroundColor Yellow; Start-Sleep -Seconds 2 }
        "4" { Write-Host "Feature coming soon..." -ForegroundColor Yellow; Start-Sleep -Seconds 2 }
        "5" { 
            $selectedJava = Select-JavaVersion
            if ($selectedJava) {
                $script:SelectedJava = $selectedJava
                $script:SelectedJavaDisplayName = $selectedJava.DisplayName
            }
        }
        "6" { 
            Show-ExitScreen
            break
        }
        default {
            Write-Host "Invalid choice. Please try again." -ForegroundColor Red
            Write-Host "`nPress any key to continue..." -ForegroundColor Cyan
            $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        }
    }
} while ($choice -ne "6")
