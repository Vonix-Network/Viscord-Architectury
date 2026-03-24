# Viscord Build Menu - PowerShell Version
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

function Show-MainMenu {
    Clear-Host
    $width = $Host.UI.RawUI.WindowSize.Width
    
    Write-CenterLine "=" -ForegroundColor Cyan
    Write-CenterText "VISCORD BUILD MENU" -ForegroundColor White -BackgroundColor Blue
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
    
    foreach ($version in $versions) {
        Write-Host "Processing Minecraft $version..." -ForegroundColor Yellow
        Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
        
        $verDir = Get-ChildItem -Path $rootDir -Directory -Name "viscord-$version-*" | Select-Object -First 1
        
        if ($verDir) {
            Set-Location (Join-Path $rootDir $verDir)
            Write-Host "Building $version..." -ForegroundColor Blue
            
            # Set JAVA_HOME if a specific Java version is selected
            $originalJavaHome = $env:JAVA_HOME
            $originalPath = $env:PATH
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $script:SelectedJava.FullPath
                $env:PATH = $script:SelectedJava.FullPath + "\bin;" + $env:PATH
                Write-Host "Using Java $($script:SelectedJava.Version)" -ForegroundColor Yellow
            }
            
            $buildResult = & .\gradlew build 2>&1
            
            # Restore original environment
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $originalJavaHome
                $env:PATH = $originalPath
            }
            
            if ($LASTEXITCODE -ne 0) {
                Write-Host "X Failed to build $version!" -ForegroundColor Red
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
            Write-Host "Building $version..." -ForegroundColor Blue
            
            # Set JAVA_HOME if a specific Java version is selected
            $originalJavaHome = $env:JAVA_HOME
            $originalPath = $env:PATH
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $script:SelectedJava.FullPath
                $env:PATH = $script:SelectedJava.FullPath + "\bin;" + $env:PATH
                Write-Host "Using Java $($script:SelectedJava.Version)" -ForegroundColor Yellow
            }
            
            $buildResult = & .\gradlew build 2>&1
            
            # Restore original environment
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $originalJavaHome
                $env:PATH = $originalPath
            }
            
            if ($LASTEXITCODE -ne 0) {
                Write-Host "X Failed to build $version!" -ForegroundColor Red
            } else {
                Write-Host "+ Build $version successful. Creating versioned folder..." -ForegroundColor Green
                
                $versionDir = Join-Path $rootDir $version
                if (-not (Test-Path $versionDir)) {
                    New-Item -ItemType Directory -Path $versionDir | Out-Null
                }
                
                $fabricJars = Get-ChildItem -Path "fabric\build\libs\viscord-*-fabric.jar" -ErrorAction SilentlyContinue
                foreach ($jar in $fabricJars) {
                    Copy-Item $jar.FullName $versionDir -Force
                    Write-Host "  + Fabric jar copied to $version" -ForegroundColor Green
                }
                
                $forgeJars = Get-ChildItem -Path "forge\build\libs\viscord-*-forge.jar" -ErrorAction SilentlyContinue
                foreach ($jar in $forgeJars) {
                    Copy-Item $jar.FullName $versionDir -Force
                    Write-Host "  + Forge jar copied to $version" -ForegroundColor Green
                }
                
                $neoforgeJars = Get-ChildItem -Path "neoforge\build\libs\viscord-*-neoforge.jar" -ErrorAction SilentlyContinue
                foreach ($jar in $neoforgeJars) {
                    Copy-Item $jar.FullName $versionDir -Force
                    Write-Host "  + NeoForge jar copied to $version" -ForegroundColor Green
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
        Write-Host "Starting build process..." -ForegroundColor Blue
        
        # Set JAVA_HOME if a specific Java version is selected
        $originalJavaHome = $env:JAVA_HOME
        $originalPath = $env:PATH
        if ($script:SelectedJava) {
            $env:JAVA_HOME = $script:SelectedJava.FullPath
            $env:PATH = $script:SelectedJava.FullPath + "\bin;" + $env:PATH
            Write-Host "Using Java $($script:SelectedJava.Version)" -ForegroundColor Yellow
        }
        
        $buildResult = & .\gradlew build 2>&1
        
        # Restore original environment
        if ($script:SelectedJava) {
            $env:JAVA_HOME = $originalJavaHome
            $env:PATH = $originalPath
        }
        
        if ($LASTEXITCODE -ne 0) {
            Write-Host "X Failed to build $buildVer!" -ForegroundColor Red
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
                    }
                    
                    $fabricJars = Get-ChildItem -Path "fabric\build\libs\viscord-*-fabric.jar" -ErrorAction SilentlyContinue
                    foreach ($jar in $fabricJars) {
                        Copy-Item $jar.FullName $releasesDir -Force
                        Write-Host "  + Fabric jar copied to Releases" -ForegroundColor Green
                    }
                    
                    $forgeJars = Get-ChildItem -Path "forge\build\libs\viscord-*-forge.jar" -ErrorAction SilentlyContinue
                    foreach ($jar in $forgeJars) {
                        Copy-Item $jar.FullName $releasesDir -Force
                        Write-Host "  + Forge jar copied to Releases" -ForegroundColor Green
                    }
                    
                    $neoforgeJars = Get-ChildItem -Path "neoforge\build\libs\viscord-*-neoforge.jar" -ErrorAction SilentlyContinue
                    foreach ($jar in $neoforgeJars) {
                        Copy-Item $jar.FullName $releasesDir -Force
                        Write-Host "  + NeoForge jar copied to Releases" -ForegroundColor Green
                    }
                }
                "2" {
                    $versionDir = Join-Path $rootDir $buildVer
                    if (-not (Test-Path $versionDir)) {
                        New-Item -ItemType Directory -Path $versionDir | Out-Null
                    }
                    
                    $fabricJars = Get-ChildItem -Path "fabric\build\libs\viscord-*-fabric.jar" -ErrorAction SilentlyContinue
                    foreach ($jar in $fabricJars) {
                        Copy-Item $jar.FullName $versionDir -Force
                        Write-Host "  + Fabric jar copied to $buildVer folder" -ForegroundColor Green
                    }
                    
                    $forgeJars = Get-ChildItem -Path "forge\build\libs\viscord-*-forge.jar" -ErrorAction SilentlyContinue
                    foreach ($jar in $forgeJars) {
                        Copy-Item $jar.FullName $versionDir -Force
                        Write-Host "  + Forge jar copied to $buildVer folder" -ForegroundColor Green
                    }
                    
                    $neoforgeJars = Get-ChildItem -Path "neoforge\build\libs\viscord-*-neoforge.jar" -ErrorAction SilentlyContinue
                    foreach ($jar in $neoforgeJars) {
                        Copy-Item $jar.FullName $versionDir -Force
                        Write-Host "  + NeoForge jar copied to $buildVer folder" -ForegroundColor Green
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
            Write-Host "Building $version..." -ForegroundColor Blue
            
            # Set JAVA_HOME if a specific Java version is selected
            $originalJavaHome = $env:JAVA_HOME
            $originalPath = $env:PATH
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $script:SelectedJava.FullPath
                $env:PATH = $script:SelectedJava.FullPath + "\bin;" + $env:PATH
                Write-Host "Using Java $($script:SelectedJava.Version)" -ForegroundColor Yellow
            }
            
            $buildResult = & .\gradlew build 2>&1
            
            # Restore original environment
            if ($script:SelectedJava) {
                $env:JAVA_HOME = $originalJavaHome
                $env:PATH = $originalPath
            }
            
            if ($LASTEXITCODE -ne 0) {
                Write-Host "X Failed to build $version!" -ForegroundColor Red
            } else {
                Write-Host "+ Build $version successful. Copying to $customFolder..." -ForegroundColor Green
                
                $fabricJars = Get-ChildItem -Path "fabric\build\libs\viscord-*-fabric.jar" -ErrorAction SilentlyContinue
                foreach ($jar in $fabricJars) {
                    Copy-Item $jar.FullName $customDir -Force
                    Write-Host "  + Fabric jar copied" -ForegroundColor Green
                }
                
                $forgeJars = Get-ChildItem -Path "forge\build\libs\viscord-*-forge.jar" -ErrorAction SilentlyContinue
                foreach ($jar in $forgeJars) {
                    Copy-Item $jar.FullName $customDir -Force
                    Write-Host "  + Forge jar copied" -ForegroundColor Green
                }
                
                $neoforgeJars = Get-ChildItem -Path "neoforge\build\libs\viscord-*-neoforge.jar" -ErrorAction SilentlyContinue
                foreach ($jar in $neoforgeJars) {
                    Copy-Item $jar.FullName $customDir -Force
                    Write-Host "  + NeoForge jar copied" -ForegroundColor Green
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
    Write-CenterText "VISCORD BUILD MENU v1.0" -ForegroundColor Yellow
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
