# Viscord Build Menu - PowerShell Version with Progress Tracking
# Modern, colorful CLI interface for building Viscord mods

function Get-ModVersion {
    # Read version from the first available gradle.properties
    $rootDir = Get-Location
    $gradleProps = Get-ChildItem -Path $rootDir -Recurse -Filter "gradle.properties" | Select-Object -First 1
    
    if ($gradleProps) {
        $content = Get-Content $gradleProps.FullName -ErrorAction SilentlyContinue
        $versionLine = $content | Where-Object { $_ -match "^mod_version\s*=\s*(.+)$" } | Select-Object -First 1
        if ($versionLine) {
            $version = $matches[1].Trim()
            return $version
        }
    }
    
    # Fallback version if gradle.properties not found
    return "2.4.8"
}

# Global variable to store mod version
$script:ModVersion = Get-ModVersion

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
    
    # Look for cleaning progress
    if ($GradleOutput -match "clean|Clean") {
        return @{
            Task = "Cleaning"
            Progress = 15
        }
    }
    
    # Look for compilation progress
    if ($GradleOutput -match "compiling|Compiling") {
        return @{
            Task = "Compiling"
            Progress = 50
        }
    }
    
    # Look for jar creation
    if ($GradleOutput -match "jar|Jar") {
        return @{
            Task = "Creating JAR"
            Progress = 85
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

function Get-RequiredJavaVersion {
    param(
        [string]$MinecraftVersion
    )
    
    # All versions require Java 21 due to newer Architectury Loom dependencies
    return 21
}

function Set-OptimalJavaVersion {
    param(
        [string]$MinecraftVersion
    )
    
    $requiredVersion = Get-RequiredJavaVersion -MinecraftVersion $MinecraftVersion
    $javaVersions = Get-InstalledJavaVersions
    
    # Find the best Java version (prefer the exact required version or higher)
    $bestJava = $javaVersions | Where-Object { $_.Version -ge $requiredVersion } | Sort-Object Version | Select-Object -First 1
    
    if ($bestJava) {
        Write-Host "Auto-selecting Java $($bestJava.Version) for Minecraft $MinecraftVersion" -ForegroundColor Cyan
        return $bestJava
    }
    
    # Fallback to user-selected Java or system default
    if ($script:SelectedJava) {
        Write-Host "Using user-selected Java $($script:SelectedJava.Version) for Minecraft $MinecraftVersion" -ForegroundColor Yellow
        return $script:SelectedJava
    }
    
    Write-Host "Using system default Java for Minecraft $MinecraftVersion" -ForegroundColor Yellow
    return $null
}

function Invoke-GradleBuildWithProgress {
    param(
        [string]$ProjectName = "project",
        [string]$MinecraftVersion = ""
    )
    
    Write-Host "Starting clean Gradle build for $ProjectName..." -ForegroundColor Blue
    Write-Host "Using Java: $env:JAVA_HOME" -ForegroundColor Cyan
    Write-ProgressBar -PercentComplete 0 -Status "Cleaning and building..."
    
    try {
        # Run gradle with live output capture using call operator
        $outputLines = @()
        $errorLines = @()
        $currentProgress = 0
        $lastStatus = "Cleaning and building..."
        $startTime = Get-Date
        $timeout = 300  # 5 minute timeout
        
        # Use & call operator which properly inherits environment variables
        & .\gradlew.bat clean build 2>&1 | ForEach-Object {
            $line = $_
            $outputLines += $line
            
            # Check for errors
            if ($line -match "^> Task .*FAILED" -or $line -match "BUILD FAILED" -or $line -match "error:") {
                $errorLines += $line
            }
            
            # Update progress based on output
            $elapsed = (Get-Date) - $startTime
            $timeBasedProgress = [math]::Min($elapsed.TotalSeconds * 2, 85)
            $currentProgress = [math]::Max($currentProgress, $timeBasedProgress)
            
            $progressInfo = Get-BuildProgress -GradleOutput $line
            if ($progressInfo.Progress -gt $currentProgress) {
                $currentProgress = $progressInfo.Progress
            }
            if ($progressInfo.Task -ne "Building") {
                $lastStatus = "$($progressInfo.Task) - $ProjectName"
            }
            
            Write-ProgressBar -PercentComplete $currentProgress -Status $lastStatus
        }
        
        $exitCode = $LASTEXITCODE
        Write-ProgressBar -PercentComplete 100 -Status "Build complete!"
        Write-Host ""
        
        # Check for BUILD SUCCESSFUL in output as additional verification
        $buildSuccessful = ($exitCode -eq 0) -or (($outputLines -join "`n") -match "BUILD SUCCESSFUL")
        
        return @{
            Success = $buildSuccessful
            Output = $outputLines
            Error = $errorLines
            ExitCode = $exitCode
        }
    }
    catch {
        Write-Host ""
        Write-Host "Build process error: $_" -ForegroundColor Red
        
        return @{
            Success = $false
            Output = @()
            Error = @($_)
            ExitCode = 1
        }
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
        Write-Host "Created Releases directory" -ForegroundColor Green
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
            
            # Set optimal Java version for this Minecraft version
            $optimalJava = Set-OptimalJavaVersion -MinecraftVersion $version
            $originalJavaHome = $env:JAVA_HOME
            $originalPath = $env:PATH
            
            if ($optimalJava) {
                $env:JAVA_HOME = $optimalJava.FullPath
                $env:PATH = $optimalJava.FullPath + "\bin;" + $env:PATH
            }
            
            # Build with progress tracking
            $buildResult = Invoke-GradleBuildWithProgress -ProjectName "Minecraft $version" -MinecraftVersion $version
            
            # Restore original environment
            $env:JAVA_HOME = $originalJavaHome
            $env:PATH = $originalPath
            
            if (-not $buildResult.Success) {
                Write-Host "X Failed to build $version!" -ForegroundColor Red
                Write-Host "Exit code: $($buildResult.ExitCode)" -ForegroundColor DarkRed
                
                # Always show the last 10 lines of output for debugging
                if ($buildResult.Output) {
                    Write-Host "Build output (last 10 lines):" -ForegroundColor DarkRed
                    $lastLines = $buildResult.Output | Select-Object -Last 10
                    foreach ($line in $lastLines) {
                        $lineStr = "$line"
                        if ($lineStr.Trim()) {
                            Write-Host "  $lineStr" -ForegroundColor Red
                        }
                    }
                }
                
                if ($buildResult.Error) {
                    Write-Host "Build errors:" -ForegroundColor DarkRed
                    foreach ($errorLine in $buildResult.Error[-5..-1]) {
                        $errStr = "$errorLine"
                        if ($errStr.Trim()) {
                            Write-Host "  $errStr" -ForegroundColor Red
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
                        # Try multiple patterns to find the correct jar
                        $jarPatterns = @("viscord-$platform-*.jar", "viscord-*-$platform.jar", "viscord-$platform.jar")
                        $foundJars = $false
                        
                        foreach ($pattern in $jarPatterns) {
                            $jars = Get-ChildItem -Path $libsPath -Filter $pattern -ErrorAction SilentlyContinue
                            if ($jars.Count -gt 0) {
                                foreach ($jar in $jars) {
                                    # Only copy the main jar, not dev-shadow or sources
                                    if ($jar.Name -notmatch "-dev-shadow" -and $jar.Name -notmatch "-sources" -and $jar.Name -notmatch "-transform") {
                                        # Create GitHub release-ready name: viscord-{version}-{platform}-{mcversion}.jar
                                        $baseName = $jar.BaseName  # e.g., "viscord-fabric-2.4.4"
                                        $extension = $jar.Extension
                                        $newName = "viscord-$version-$platform-$script:ModVersion$extension"
                                        $newPath = Join-Path $releasesDir $newName
                                        
                                        Copy-Item $jar.FullName $newPath -Force
                                        Write-Host "  + $newName copied" -ForegroundColor Green
                                        $foundJars = $true
                                    }
                                }
                            }
                        }
                        
                        if (-not $foundJars) {
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

function Build-AllVersioned {
    Clear-Host
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "BUILDING ALL VERSIONS TO VERSIONED FOLDERS" -ForegroundColor Green
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""

    $rootDir = Get-Location
    $versions = @("1.18.2", "1.19.2", "1.20.1", "1.21.1")
    
    foreach ($version in $versions) {
        Write-Host "Processing Minecraft $version..." -ForegroundColor Yellow
        Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
        
        $verDir = Get-ChildItem -Path $rootDir -Directory -Name "viscord-$version-*" | Select-Object -First 1
        
        if ($verDir) {
            Set-Location (Join-Path $rootDir $verDir)
            
            # Set optimal Java version for this Minecraft version
            $optimalJava = Set-OptimalJavaVersion -MinecraftVersion $version
            $originalJavaHome = $env:JAVA_HOME
            $originalPath = $env:PATH
            
            if ($optimalJava) {
                $env:JAVA_HOME = $optimalJava.FullPath
                $env:PATH = $optimalJava.FullPath + "\bin;" + $env:PATH
            }
            
            # Build with progress tracking
            $buildResult = Invoke-GradleBuildWithProgress -ProjectName "Minecraft $version" -MinecraftVersion $version
            
            # Restore original environment
            $env:JAVA_HOME = $originalJavaHome
            $env:PATH = $originalPath
            
            if (-not $buildResult.Success) {
                Write-Host "X Failed to build $version!" -ForegroundColor Red
                Write-Host "Exit code: $($buildResult.ExitCode)" -ForegroundColor DarkRed
            } else {
                Write-Host "+ Build $version successful. Creating versioned folder..." -ForegroundColor Green
                
                $versionDir = Join-Path $rootDir $version
                if (-not (Test-Path $versionDir)) {
                    New-Item -ItemType Directory -Path $versionDir | Out-Null
                }
                
                # Check what platforms are available in this version
                $platforms = @()
                if (Test-Path "fabric") { $platforms += "fabric" }
                if (Test-Path "forge") { $platforms += "forge" }
                if (Test-Path "neoforge") { $platforms += "neoforge" }
                
                Write-Host "  Available platforms: $($platforms -join ', ')" -ForegroundColor Cyan
                
                foreach ($platform in $platforms) {
                    $libsPath = "$platform\build\libs"
                    if (Test-Path $libsPath) {
                        # Try multiple patterns to find the correct jar
                        $jarPatterns = @("viscord-$platform-*.jar", "viscord-*-$platform.jar", "viscord-$platform.jar")
                        $foundJars = $false
                        
                        foreach ($pattern in $jarPatterns) {
                            $jars = Get-ChildItem -Path $libsPath -Filter $pattern -ErrorAction SilentlyContinue
                            if ($jars.Count -gt 0) {
                                foreach ($jar in $jars) {
                                    # Only copy the main jar, not dev-shadow or sources
                                    if ($jar.Name -notmatch "-dev-shadow" -and $jar.Name -notmatch "-sources" -and $jar.Name -notmatch "-transform") {
                                        Copy-Item $jar.FullName $versionDir -Force
                                        Write-Host "  + $($jar.Name) copied to $version" -ForegroundColor Green
                                        $foundJars = $true
                                    }
                                }
                            }
                        }
                        
                        if (-not $foundJars) {
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
    Write-CenterText "Jars copied to versioned folders." -ForegroundColor Cyan
    Write-CenterLine "=" -ForegroundColor Cyan
    
    Write-Host "`nPress any key to continue..." -ForegroundColor Cyan
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

function Build-SpecificVersion {
    Clear-Host
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "BUILD SPECIFIC VERSION" -ForegroundColor Yellow
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""
    Write-CenterText "Available Minecraft versions:" -ForegroundColor Cyan
    Write-CenterText "[1] -> Minecraft 1.18.2" -ForegroundColor Green
    Write-CenterText "[2] -> Minecraft 1.19.2" -ForegroundColor Green
    Write-CenterText "[3] -> Minecraft 1.20.1" -ForegroundColor Green
    Write-CenterText "[4] -> Minecraft 1.21.1" -ForegroundColor Green
    Write-CenterText "[5] -> Back to main menu" -ForegroundColor Red
    Write-Host ""
    Write-Host "Enter your choice: " -ForegroundColor Cyan -NoNewline
    
    $choice = Read-Host
    
    if ($choice -eq "5") { return }
    
    $versionMap = @{
        "1" = "1.18.2"
        "2" = "1.19.2"
        "3" = "1.20.1"
        "4" = "1.21.1"
    }
    
    if (-not $versionMap.ContainsKey($choice)) {
        Write-Host "Invalid choice. Please try again." -ForegroundColor Red
        Write-Host "`nPress any key to continue..." -ForegroundColor Cyan
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        return Build-SpecificVersion
    }
    
    $buildVer = $versionMap[$choice]
    
    Write-Host ""
    Write-Host "Building Minecraft $buildVer..." -ForegroundColor Yellow
    Write-Host "------------------------------------------------------------" -ForegroundColor Cyan

    $rootDir = Get-Location
    $verDir = Get-ChildItem -Path $rootDir -Directory -Name "viscord-$buildVer-*" | Select-Object -First 1
    
    if ($verDir) {
        Set-Location (Join-Path $rootDir $verDir)
        
        # Set optimal Java version for this Minecraft version
        $optimalJava = Set-OptimalJavaVersion -MinecraftVersion $buildVer
        $originalJavaHome = $env:JAVA_HOME
        $originalPath = $env:PATH
        
        if ($optimalJava) {
            $env:JAVA_HOME = $optimalJava.FullPath
            $env:PATH = $optimalJava.FullPath + "\bin;" + $env:PATH
        }
        
        # Build with progress tracking
        $buildResult = Invoke-GradleBuildWithProgress -ProjectName "Minecraft $buildVer" -MinecraftVersion $buildVer
        
        # Restore original environment
        $env:JAVA_HOME = $originalJavaHome
        $env:PATH = $originalPath
        
        if (-not $buildResult.Success) {
            Write-Host "X Failed to build $buildVer!" -ForegroundColor Red
            Write-Host "Exit code: $($buildResult.ExitCode)" -ForegroundColor DarkRed
        } else {
            Write-Host "+ Build $buildVer successful!" -ForegroundColor Green
            
            Write-Host ""
            Write-Host "Where would you like to copy the jars?" -ForegroundColor Cyan
            Write-Host "[1] -> Releases folder" -ForegroundColor Green
            Write-Host "[2] -> Versioned folder ($buildVer)" -ForegroundColor Green
            Write-Host "[3] -> Don't copy" -ForegroundColor Green
            Write-Host ""
            Write-Host "Enter your choice: " -ForegroundColor Cyan -NoNewline
            
            $copyChoice = Read-Host
            
            switch ($copyChoice) {
                "1" {
                    $releasesDir = Join-Path $rootDir "Releases"
                    if (-not (Test-Path $releasesDir)) {
                        New-Item -ItemType Directory -Path $releasesDir | Out-Null
                        Write-Host "Created Releases directory" -ForegroundColor Green
                    }
                    
                    # Check what platforms are available in this version
                    $platforms = @()
                    if (Test-Path "fabric") { $platforms += "fabric" }
                    if (Test-Path "forge") { $platforms += "forge" }
                    if (Test-Path "neoforge") { $platforms += "neoforge" }
                    
                    Write-Host "  Available platforms: $($platforms -join ', ')" -ForegroundColor Cyan
                    
                    foreach ($platform in $platforms) {
                        $libsPath = "$platform\build\libs"
                        if (Test-Path $libsPath) {
                            # Try multiple patterns to find the correct jar
                            $jarPatterns = @("viscord-$platform-*.jar", "viscord-*-$platform.jar", "viscord-$platform.jar")
                            $foundJars = $false
                            
                            foreach ($pattern in $jarPatterns) {
                                $jars = Get-ChildItem -Path $libsPath -Filter $pattern -ErrorAction SilentlyContinue
                                if ($jars.Count -gt 0) {
                                    foreach ($jar in $jars) {
                                        # Only copy the main jar, not dev-shadow or sources
                                        if ($jar.Name -notmatch "-dev-shadow" -and $jar.Name -notmatch "-sources" -and $jar.Name -notmatch "-transform") {
                                            # Create GitHub release-ready name: viscord-{version}-{platform}-{mcversion}.jar
                                            $baseName = $jar.BaseName
                                            $extension = $jar.Extension
                                            $newName = "viscord-$buildVer-$platform-2.4.8$extension"
                                            $newPath = Join-Path $releasesDir $newName
                                            
                                            Copy-Item $jar.FullName $newPath -Force
                                            Write-Host "  + $newName copied to Releases" -ForegroundColor Green
                                            $foundJars = $true
                                        }
                                    }
                                }
                            }
                            
                            if (-not $foundJars) {
                                Write-Host "  - No $platform jars found" -ForegroundColor Yellow
                            }
                        } else {
                            Write-Host "  - $platform build directory not found" -ForegroundColor Yellow
                        }
                    }
                }
                "2" {
                    $versionDir = Join-Path $rootDir $buildVer
                    if (-not (Test-Path $versionDir)) {
                        New-Item -ItemType Directory -Path $versionDir | Out-Null
                    }
                    
                    # Check what platforms are available in this version
                    $platforms = @()
                    if (Test-Path "fabric") { $platforms += "fabric" }
                    if (Test-Path "forge") { $platforms += "forge" }
                    if (Test-Path "neoforge") { $platforms += "neoforge" }
                    
                    Write-Host "  Available platforms: $($platforms -join ', ')" -ForegroundColor Cyan
                    
                    foreach ($platform in $platforms) {
                        $libsPath = "$platform\build\libs"
                        if (Test-Path $libsPath) {
                            # Try multiple patterns to find the correct jar
                            $jarPatterns = @("viscord-$platform-*.jar", "viscord-*-$platform.jar", "viscord-$platform.jar")
                            $foundJars = $false
                            
                            foreach ($pattern in $jarPatterns) {
                                $jars = Get-ChildItem -Path $libsPath -Filter $pattern -ErrorAction SilentlyContinue
                                if ($jars.Count -gt 0) {
                                    foreach ($jar in $jars) {
                                        # Only copy the main jar, not dev-shadow or sources
                                        if ($jar.Name -notmatch "-dev-shadow" -and $jar.Name -notmatch "-sources" -and $jar.Name -notmatch "-transform") {
                                            Copy-Item $jar.FullName $versionDir -Force
                                            Write-Host "  + $($jar.Name) copied to $buildVer folder" -ForegroundColor Green
                                            $foundJars = $true
                                        }
                                    }
                                }
                            }
                            
                            if (-not $foundJars) {
                                Write-Host "  - No $platform jars found" -ForegroundColor Yellow
                            }
                        } else {
                            Write-Host "  - $platform build directory not found" -ForegroundColor Yellow
                        }
                    }
                }
                "3" {
                    Write-Host "+ Jars not copied." -ForegroundColor Yellow
                }
                default {
                    Write-Host "+ Jars not copied." -ForegroundColor Yellow
                }
            }
        }
    } else {
        Write-Host "X Could not find directory for version $buildVer" -ForegroundColor Red
    }

    Set-Location $rootDir
    
    Write-Host ""
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "BUILD PROCESS COMPLETED" -ForegroundColor Green
    Write-CenterLine "=" -ForegroundColor Cyan
    
    Write-Host "`nPress any key to continue..." -ForegroundColor Cyan
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

function Build-CustomFolder {
    Clear-Host
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "BUILD TO CUSTOM RELEASE FOLDER" -ForegroundColor Yellow
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Enter custom release folder name: " -ForegroundColor Cyan -NoNewline
    
    $customFolder = Read-Host
    
    if ([string]::IsNullOrWhiteSpace($customFolder)) {
        Write-Host "Folder name cannot be empty." -ForegroundColor Red
        Write-Host "`nPress any key to continue..." -ForegroundColor Cyan
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        return Build-CustomFolder
    }
    
    Clear-Host
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "BUILDING TO CUSTOM FOLDER: $customFolder" -ForegroundColor Yellow
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-Host ""

    $rootDir = Get-Location
    $customDir = Join-Path $rootDir $customFolder
    
    if (-not (Test-Path $customDir)) {
        New-Item -ItemType Directory -Path $customDir | Out-Null
    }

    $versions = @("1.18.2", "1.19.2", "1.20.1", "1.21.1")
    
    foreach ($version in $versions) {
        Write-Host "Processing Minecraft $version..." -ForegroundColor Yellow
        
        $verDir = Get-ChildItem -Path $rootDir -Directory -Name "viscord-$version-*" | Select-Object -First 1
        
        if ($verDir) {
            Set-Location (Join-Path $rootDir $verDir)
            
            # Set optimal Java version for this Minecraft version
            $optimalJava = Set-OptimalJavaVersion -MinecraftVersion $version
            $originalJavaHome = $env:JAVA_HOME
            $originalPath = $env:PATH
            
            if ($optimalJava) {
                $env:JAVA_HOME = $optimalJava.FullPath
                $env:PATH = $optimalJava.FullPath + "\bin;" + $env:PATH
            }
            
            # Build with progress tracking
            $buildResult = Invoke-GradleBuildWithProgress -ProjectName "Minecraft $version" -MinecraftVersion $version
            
            # Restore original environment
            $env:JAVA_HOME = $originalJavaHome
            $env:PATH = $originalPath
            
            if (-not $buildResult.Success) {
                Write-Host "X Failed to build $version!" -ForegroundColor Red
                Write-Host "Exit code: $($buildResult.ExitCode)" -ForegroundColor DarkRed
            } else {
                Write-Host "+ Build $version successful. Copying to $customFolder..." -ForegroundColor Green
                
                # Check what platforms are available in this version
                $platforms = @()
                if (Test-Path "fabric") { $platforms += "fabric" }
                if (Test-Path "forge") { $platforms += "forge" }
                if (Test-Path "neoforge") { $platforms += "neoforge" }
                
                Write-Host "  Available platforms: $($platforms -join ', ')" -ForegroundColor Cyan
                
                foreach ($platform in $platforms) {
                    $libsPath = "$platform\build\libs"
                    if (Test-Path $libsPath) {
                        # Try multiple patterns to find the correct jar
                        $jarPatterns = @("viscord-$platform-*.jar", "viscord-*-$platform.jar", "viscord-$platform.jar")
                        $foundJars = $false
                        
                        foreach ($pattern in $jarPatterns) {
                            $jars = Get-ChildItem -Path $libsPath -Filter $pattern -ErrorAction SilentlyContinue
                            if ($jars.Count -gt 0) {
                                foreach ($jar in $jars) {
                                    # Only copy the main jar, not dev-shadow or sources
                                    if ($jar.Name -notmatch "-dev-shadow" -and $jar.Name -notmatch "-sources" -and $jar.Name -notmatch "-transform") {
                                        # Create GitHub release-ready name: viscord-{version}-{platform}-{mcversion}.jar
                                        $baseName = $jar.BaseName
                                        $extension = $jar.Extension
                                        $newName = "viscord-$version-$platform-$script:ModVersion$extension"
                                        $newPath = Join-Path $customDir $newName
                                        
                                        Copy-Item $jar.FullName $newPath -Force
                                        Write-Host "  + $newName copied" -ForegroundColor Green
                                        $foundJars = $true
                                    }
                                }
                            }
                        }
                        
                        if (-not $foundJars) {
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
    Write-CenterText "Jars copied to $customFolder folder." -ForegroundColor Cyan
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
        "2" { Build-AllVersioned }
        "3" { Build-SpecificVersion }
        "4" { Build-CustomFolder }
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
