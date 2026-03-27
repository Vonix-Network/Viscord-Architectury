#!/usr/bin/env python3
"""
Viscord Build Menu - Modern Terminal UI
A beautiful, interactive CLI interface for building Viscord mods
"""

import os
import sys
import re
import subprocess
import tempfile
import shutil
from pathlib import Path
from datetime import datetime
from typing import Optional, List, Dict, Any
from dataclasses import dataclass
from enum import Enum

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn
from rich.layout import Layout
from rich.text import Text
from rich import box
from rich.align import Align
from rich.prompt import Prompt, Confirm
from rich.columns import Columns
from rich.live import Live
from rich.tree import Tree

console = Console()


class BuildType(Enum):
    CLEAN = "clean"
    QUICK = "quick"


@dataclass
class JavaVersion:
    path: str
    version: int
    full_path: str
    display_name: str
    full_version: str


@dataclass
class BuildResult:
    success: bool
    output: List[str]
    error: List[str]
    exit_code: int


class ViscordBuildMenu:
    VERSIONS = ["1.18.2", "1.19.2", "1.20.1", "1.21.1"]
    
    def __init__(self):
        self.root_dir = Path.cwd()
        self.selected_java: Optional[JavaVersion] = None
        self.mod_version = self._get_mod_version()
        
    def _get_mod_version(self) -> str:
        """Read version from gradle.properties"""
        for gradle_props in self.root_dir.rglob("gradle.properties"):
            try:
                content = gradle_props.read_text()
                match = re.search(r"^mod_version\s*=\s*(.+)$", content, re.MULTILINE)
                if match:
                    return match.group(1).strip()
            except Exception:
                pass
        return "2.4.10"
    
    def _get_installed_java_versions(self) -> List[JavaVersion]:
        """Detect installed Java versions"""
        java_versions = []
        
        # Check Eclipse Adoptium
        adoptium_path = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Eclipse Adoptium"
        if adoptium_path.exists():
            for java_dir in adoptium_path.iterdir():
                if java_dir.is_dir():
                    java_exe = java_dir / "bin" / "java.exe"
                    if java_exe.exists():
                        try:
                            result = subprocess.run([str(java_exe), "-version"], 
                                                  capture_output=True, text=True, timeout=5)
                            version_output = result.stderr or result.stdout
                            match = re.search(r'"([0-9.]+)"', version_output)
                            if match:
                                full_version = match.group(1)
                                major_version = int(full_version.split(".")[0])
                                java_versions.append(JavaVersion(
                                    path=str(java_exe),
                                    version=major_version,
                                    full_path=str(java_dir),
                                    display_name=f"Java {major_version} ({java_dir.name})",
                                    full_version=full_version
                                ))
                        except Exception:
                            pass
        
        # Check Program Files Java
        program_files_java = Path(os.environ.get("ProgramFiles", "")) / "Java"
        if program_files_java.exists():
            for java_dir in program_files_java.iterdir():
                if java_dir.is_dir():
                    java_exe = java_dir / "bin" / "java.exe"
                    if java_exe.exists():
                        try:
                            result = subprocess.run([str(java_exe), "-version"],
                                                  capture_output=True, text=True, timeout=5)
                            version_output = result.stderr or result.stdout
                            match = re.search(r'"([0-9.]+)"', version_output)
                            if match:
                                full_version = match.group(1)
                                major_version = int(full_version.split(".")[0])
                                # Check for duplicates
                                if not any(j.full_path == str(java_dir) for j in java_versions):
                                    java_versions.append(JavaVersion(
                                        path=str(java_exe),
                                        version=major_version,
                                        full_path=str(java_dir),
                                        display_name=f"Java {major_version} ({java_dir.name})",
                                        full_version=full_version
                                    ))
                        except Exception:
                            pass
        
        # Check JAVA_HOME
        java_home = os.environ.get("JAVA_HOME", "")
        if java_home and Path(java_home).exists():
            java_exe = Path(java_home) / "bin" / "java.exe"
            if java_exe.exists():
                try:
                    result = subprocess.run([str(java_exe), "-version"],
                                          capture_output=True, text=True, timeout=5)
                    version_output = result.stderr or result.stdout
                    match = re.search(r'"([0-9.]+)"', version_output)
                    if match:
                        full_version = match.group(1)
                        major_version = int(full_version.split(".")[0])
                        if not any(j.full_path == java_home for j in java_versions):
                            java_versions.append(JavaVersion(
                                path=str(java_exe),
                                version=major_version,
                                full_path=java_home,
                                display_name=f"Java {major_version} (JAVA_HOME)",
                                full_version=full_version
                            ))
                except Exception:
                    pass
        
        # Check PATH
        try:
            result = subprocess.run(["java", "-version"],
                                  capture_output=True, text=True, timeout=5)
            version_output = result.stderr or result.stdout
            match = re.search(r'"([0-9.]+)"', version_output)
            if match:
                full_version = match.group(1)
                major_version = int(full_version.split(".")[0])
                # Try to find the Java home from PATH
                java_cmd = shutil.which("java")
                if java_cmd:
                    java_home = str(Path(java_cmd).parent.parent)
                    if not any(j.full_path == java_home for j in java_versions):
                        java_versions.append(JavaVersion(
                            path=java_cmd,
                            version=major_version,
                            full_path=java_home,
                            display_name=f"Java {major_version} (PATH)",
                            full_version=full_version
                        ))
        except Exception:
            pass
        
        return sorted(java_versions, key=lambda x: x.version, reverse=True)
    
    def _get_required_java_version(self, mc_version: str) -> int:
        """All versions require Java 21 due to newer Architectury Loom"""
        return 21
    
    def _set_optimal_java(self, mc_version: str) -> Optional[JavaVersion]:
        """Auto-select best Java version for the Minecraft version"""
        required = self._get_required_java_version(mc_version)
        java_versions = self._get_installed_java_versions()
        
        # Find Java >= required version
        for java in sorted(java_versions, key=lambda x: x.version):
            if java.version >= required:
                console.print(f"[cyan]Auto-selecting {java.display_name} for Minecraft {mc_version}[/cyan]")
                return java
        
        if self.selected_java:
            console.print(f"[yellow]Using user-selected {self.selected_java.display_name} for Minecraft {mc_version}[/yellow]")
            return self.selected_java
        
        console.print(f"[yellow]Using system default Java for Minecraft {mc_version}[/yellow]")
        return None
    
    def _display_header(self, title: str):
        """Display a beautiful header"""
        console.clear()
        header = Panel(
            Align.center(Text(title, style="bold white")),
            box=box.ROUNDED,
            style="cyan",
            border_style="cyan"
        )
        console.print(header)
        console.print()
    
    def _display_menu(self):
        """Display the main menu"""
        self._display_header("VISCORD BUILD MENU v3.0")
        
        # Create menu table
        table = Table(
            show_header=False,
            box=box.ROUNDED,
            border_style="cyan",
            pad_edge=False,
            width=60
        )
        table.add_column("Option", style="green", justify="center")
        table.add_column("Description", style="white")
        
        java_display = self.selected_java.display_name if self.selected_java else "System Default"
        
        menu_items = [
            ("[1]", "Build all versions → Releases folder"),
            ("[2]", "Build all versions → Versioned folders"),
            ("[3]", "Build specific version"),
            ("[4]", "Build to custom folder"),
            ("[5]", f"Select Java ({java_display})"),
            ("[6]", "Exit"),
        ]
        
        for opt, desc in menu_items:
            style = "red" if opt == "[6]" else ("yellow" if opt == "[5]" else "green")
            table.add_row(f"[{style}]{opt}[/{style}]", desc)
        
        console.print(Align.center(table))
        console.print()
        
        # Version info panel
        version_info = Panel(
            f"[cyan]Mod Version:[/cyan] [white]{self.mod_version}[/white]",
            box=box.ROUNDED,
            border_style="dim cyan"
        )
        console.print(Align.center(version_info))
        console.print()
    
    def _select_build_type(self) -> BuildType:
        """Prompt for build type selection"""
        self._display_header("SELECT BUILD TYPE")
        
        table = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        table.add_column("Option", style="green")
        table.add_column("Type", style="white bold")
        table.add_column("Description", style="dim white")
        
        table.add_row("[1]", "Clean Build", "Deletes previous build artifacts, rebuilds from scratch (recommended)")
        table.add_row("[2]", "Quick Build", "Builds only changed files, preserves existing artifacts (faster)")
        
        console.print(Align.center(table))
        console.print()
        
        choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=["1", "2"], default="1")
        return BuildType.CLEAN if choice == "1" else BuildType.QUICK
    
    def _select_java_version(self):
        """Java version selection screen"""
        java_versions = self._get_installed_java_versions()
        
        if not java_versions:
            console.print("[red]No Java installations found![/red]")
            console.print("[yellow]Please install Java 17 or Java 21 to continue.[/yellow]")
            Prompt.ask("[cyan]Press Enter to continue...[/cyan]")
            return
        
        self._display_header("SELECT JAVA VERSION")
        
        table = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        table.add_column("Option", style="green")
        table.add_column("Version", style="white")
        
        for i, java in enumerate(java_versions, 1):
            table.add_row(f"[{i}]", java.display_name)
        
        table.add_row("[0]", "Use system default Java")
        
        console.print(Align.center(table))
        console.print()
        
        choices = [str(i) for i in range(len(java_versions) + 1)]
        choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=choices, default="0")
        
        if choice == "0":
            self.selected_java = None
        else:
            self.selected_java = java_versions[int(choice) - 1]
            console.print(f"[green]Selected: {self.selected_java.display_name}[/green]")
            Prompt.ask("[cyan]Press Enter to continue...[/cyan]")
    
    def _build_with_progress(self, project_name: str, mc_version: str, build_type: BuildType) -> BuildResult:
        """Execute Gradle build with visual progress tracking"""
        status_text = "Clean Build" if build_type == BuildType.CLEAN else "Quick Build"
        
        with Progress(
            SpinnerColumn(),
            TextColumn("[progress.description]{task.description}"),
            BarColumn(complete_style="green", finished_style="green"),
            TaskProgressColumn(),
            console=console,
            transient=False
        ) as progress:
            
            task = progress.add_task(f"[cyan]{status_text} - {project_name}...", total=100)
            
            output_file = tempfile.mktemp(suffix=".txt", prefix="gradle_output_")
            error_file = tempfile.mktemp(suffix=".txt", prefix="gradle_error_")
            
            try:
                args = ["clean", "build"] if build_type == BuildType.CLEAN else ["build"]
                cmd = ["cmd.exe", "/c", "gradlew.bat"] + args
                
                with open(output_file, "w") as out_f, open(error_file, "w") as err_f:
                    process = subprocess.Popen(
                        cmd,
                        stdout=out_f,
                        stderr=err_f,
                        shell=False
                    )
                
                start_time = datetime.now()
                timeout = 600  # 10 minutes — first-time remap can take a while
                current_progress = 0
                timed_out = False
                
                while process.poll() is None:
                    elapsed = (datetime.now() - start_time).total_seconds()
                    
                    if elapsed > timeout:
                        process.terminate()
                        timed_out = True
                        break
                    
                    time_progress = min(int(elapsed * 2), 85)
                    current_progress = max(current_progress, time_progress)
                    
                    try:
                        with open(output_file, "r", errors="ignore") as f:
                            lines = f.readlines()
                            if lines:
                                last_lines = "".join(lines[-10:])
                                if ":" in last_lines:
                                    task_match = re.search(r":(\w+):", last_lines)
                                    if task_match:
                                        task_name = task_match.group(1)
                                        progress.update(task, description=f"[cyan]{status_text} - {task_name}...")
                                
                                if re.search(r"clean|Clean", last_lines):
                                    current_progress = max(current_progress, 15)
                                elif re.search(r"compiling|Compiling", last_lines, re.IGNORECASE):
                                    current_progress = max(current_progress, 50)
                                elif re.search(r"jar|Jar", last_lines):
                                    current_progress = max(current_progress, 85)
                                elif "BUILD SUCCESSFUL" in last_lines:
                                    current_progress = 100
                    except Exception:
                        pass
                    
                    progress.update(task, completed=current_progress)
                    import time
                    time.sleep(0.5)
                
                exit_code = process.returncode
                
                try:
                    with open(output_file, "r", errors="ignore") as f:
                        output_lines = f.read().splitlines()
                except Exception:
                    output_lines = []
                
                try:
                    with open(error_file, "r", errors="ignore") as f:
                        error_lines = f.read().splitlines()
                except Exception:
                    error_lines = []
                
                if timed_out:
                    progress.update(task, completed=100, description=f"[red]{status_text} - TIMED OUT[/red]")
                    return BuildResult(
                        success=False,
                        output=output_lines,
                        error=[f"BUILD TIMED OUT after {timeout}s — Gradle may be downloading dependencies or stuck. Try again."] + error_lines,
                        exit_code=-1
                    )
                
                progress.update(task, completed=100, description=f"[green]{status_text} Complete![/green]")
                success = exit_code == 0 or any("BUILD SUCCESSFUL" in line for line in output_lines)
                
                return BuildResult(
                    success=success,
                    output=output_lines,
                    error=error_lines,
                    exit_code=exit_code
                )
                
            except Exception as e:
                return BuildResult(
                    success=False,
                    output=[],
                    error=[str(e)],
                    exit_code=1
                )
            finally:
                try:
                    os.unlink(output_file)
                    os.unlink(error_file)
                except Exception:
                    pass
    
    def _copy_jars(self, version: str, dest_dir: Path, rename: bool = True) -> bool:
        """Copy built JARs to destination"""
        platforms = []
        if (Path("fabric")).exists():
            platforms.append("fabric")
        if (Path("forge")).exists():
            platforms.append("forge")
        if (Path("neoforge")).exists():
            platforms.append("neoforge")
        
        if not platforms:
            console.print("[yellow]  - No platforms found[/yellow]")
            return False
        
        console.print(f"[cyan]  Available platforms: {', '.join(platforms)}[/cyan]")
        
        copied = False
        for platform in platforms:
            libs_path = Path(platform) / "build" / "libs"
            if not libs_path.exists():
                console.print(f"[yellow]  - {platform} build directory not found[/yellow]")
                continue
            
            jar_patterns = [
                f"viscord-{platform}-*.jar",
                f"viscord-*-{platform}.jar",
                f"viscord-{platform}.jar"
            ]
            
            found = False
            for pattern in jar_patterns:
                jars = list(libs_path.glob(pattern))
                for jar in jars:
                    # Skip dev/shadow/sources jars
                    if any(x in jar.name for x in ["-dev-shadow", "-sources", "-transform"]):
                        continue
                    
                    if rename:
                        new_name = f"viscord-{version}-{platform}-{self.mod_version}.jar"
                        dest_path = dest_dir / new_name
                    else:
                        dest_path = dest_dir / jar.name
                    
                    shutil.copy2(jar, dest_path)
                    console.print(f"[green]  + {dest_path.name} copied[/green]")
                    found = True
                    copied = True
                
                if found:
                    break
            
            if not found:
                console.print(f"[yellow]  - No {platform} jars found[/yellow]")
        
        return copied
    
    def _get_version_dir(self, version: str) -> Optional[Path]:
        """Find the version directory"""
        pattern = f"viscord-{version}-*"
        for item in self.root_dir.iterdir():
            if item.is_dir() and item.name.startswith(f"viscord-{version}-"):
                return item
        return None
    
    def _build_version(self, version: str, build_type: BuildType) -> BuildResult:
        """Build a single version"""
        ver_dir = self._get_version_dir(version)
        if not ver_dir:
            console.print(f"[red]X Could not find directory for version {version}[/red]")
            return BuildResult(False, [], [f"Directory not found for {version}"], 1)
        
        os.chdir(ver_dir)
        
        # Set optimal Java
        optimal_java = self._set_optimal_java(version)
        original_java_home = os.environ.get("JAVA_HOME", "")
        original_path = os.environ.get("PATH", "")
        
        try:
            if optimal_java:
                os.environ["JAVA_HOME"] = optimal_java.full_path
                os.environ["PATH"] = str(Path(optimal_java.full_path) / "bin") + ";" + original_path
            
            result = self._build_with_progress(f"Minecraft {version}", version, build_type)
        finally:
            os.environ["JAVA_HOME"] = original_java_home
            os.environ["PATH"] = original_path
            os.chdir(self.root_dir)
        
        return result
    
    def _display_build_error(self, result: BuildResult):
        """Display build error details with categorised diagnosis"""
        all_lines = result.output + result.error
        all_text = "\n".join(all_lines)

        # ── Categorise the failure ────────────────────────────────────────────
        if result.exit_code == -1 or any("TIMED OUT" in l for l in result.error):
            category = "TIMEOUT"
            category_color = "yellow"
            hint = (
                "The build exceeded the 5-minute limit.\n"
                "  This usually means Gradle is downloading dependencies for the first time.\n"
                "  [bold]Try running the build again[/bold] — subsequent builds will be much faster."
            )
        elif any("GradleWrapperMain" in l or "ClassNotFoundException" in l for l in all_lines):
            category = "GRADLE WRAPPER MISSING"
            category_color = "red"
            hint = (
                "gradle-wrapper.jar is missing from gradle/wrapper/.\n"
                "  Run: [bold]gradle wrapper[/bold] inside the template directory,\n"
                "  or copy gradle-wrapper.jar from another working template."
            )
        elif any("error: cannot find symbol" in l or "symbol:" in l for l in all_lines):
            category = "COMPILATION ERROR"
            category_color = "red"
            hint = "Java compilation failed. See the symbol/location lines below for the exact cause."
        elif any("compileJava FAILED" in l for l in all_lines):
            category = "COMPILE FAILED"
            category_color = "red"
            hint = "Java source failed to compile. Check the error lines below."
        elif any("test" in l.lower() and "FAILED" in l for l in all_lines):
            category = "TEST FAILURE"
            category_color = "yellow"
            hint = "Build compiled but one or more tests failed. See test report for details."
        elif any("Could not resolve" in l or "Could not download" in l for l in all_lines):
            category = "DEPENDENCY ERROR"
            category_color = "yellow"
            hint = "Gradle could not download a dependency. Check your internet connection and try again."
        elif any("remapping sources" in l for l in all_lines) and result.exit_code is None:
            category = "TIMEOUT DURING REMAP"
            category_color = "yellow"
            hint = (
                "Build timed out while remapping Minecraft sources (first-time setup).\n"
                "  This is normal on the first run. [bold]Try again[/bold] — it will resume from where it left off."
            )
        elif result.exit_code is None:
            category = "PROCESS KILLED / TIMEOUT"
            category_color = "yellow"
            hint = "The build process was terminated (likely a timeout). Try running again."
        else:
            category = f"BUILD FAILED (exit {result.exit_code})"
            category_color = "red"
            hint = "An unexpected build error occurred. See output below."

        # ── Header ────────────────────────────────────────────────────────────
        console.print(Panel(
            f"[bold {category_color}]{category}[/bold {category_color}]\n[white]{hint}[/white]",
            box=box.ROUNDED,
            border_style=category_color,
            title="[bold]Build Failure Details[/bold]"
        ))
        console.print()

        # ── Extract meaningful lines only ─────────────────────────────────────
        error_keywords = [
            "error:", "ERROR", "FAILED", "Exception", "symbol:", "location:",
            "cannot find symbol", "package does not exist", "BUILD FAILED",
            "What went wrong", "Execution failed", "AssertionFailedError",
            "TIMED OUT", "ClassNotFoundException", "Could not resolve",
            "Could not download", "compileJava", "test FAILED"
        ]

        meaningful = [
            l for l in all_lines
            if l.strip() and any(kw in l for kw in error_keywords)
        ]

        # Deduplicate while preserving order
        seen = set()
        deduped = []
        for l in meaningful:
            key = l.strip()
            if key not in seen:
                seen.add(key)
                deduped.append(l)

        if deduped:
            console.print("[bold red]Key error lines:[/bold red]")
            for line in deduped[:20]:
                console.print(f"  [red]{line.strip()}[/red]")
        else:
            # Fallback: last 15 non-empty lines
            console.print("[dim]Last build output:[/dim]")
            non_empty = [l for l in all_lines if l.strip()]
            for line in non_empty[-15:]:
                console.print(f"  [dim]{line.strip()}[/dim]")

        console.print()
    
    def build_all_releases(self):
        """Build all versions to Releases folder"""
        build_type = self._select_build_type()
        
        console.clear()
        self._display_header("BUILDING ALL VERSIONS → RELEASES")
        
        releases_dir = self.root_dir / "Releases"
        releases_dir.mkdir(exist_ok=True)
        console.print(f"[green]Created/Found Releases directory[/green]")
        console.print()
        
        total = len(self.VERSIONS)
        success_count = 0
        
        for i, version in enumerate(self.VERSIONS, 1):
            console.print(f"[yellow]Processing Minecraft {version}... ({i}/{total})[/yellow]")
            console.print("-" * 60)
            
            result = self._build_version(version, build_type)
            
            if result.success:
                console.print(f"[green]+ Build {version} successful. Copying jars...[/green]")
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, releases_dir, rename=True)
                os.chdir(self.root_dir)
                success_count += 1
            else:
                console.print(f"[red]X Failed to build {version}![/red]")
                self._display_build_error(result)
            
            console.print()
        
        self._display_summary(success_count, total, "Releases")
    
    def build_all_versioned(self):
        """Build all versions to versioned folders"""
        build_type = self._select_build_type()
        
        console.clear()
        self._display_header("BUILDING ALL VERSIONS → VERSIONED FOLDERS")
        
        total = len(self.VERSIONS)
        success_count = 0
        
        for version in self.VERSIONS:
            console.print(f"[yellow]Processing Minecraft {version}...[/yellow]")
            console.print("-" * 60)
            
            result = self._build_version(version, build_type)
            
            if result.success:
                console.print(f"[green]+ Build {version} successful. Creating folder...[/green]")
                version_dir = self.root_dir / version
                version_dir.mkdir(exist_ok=True)
                
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, version_dir, rename=False)
                os.chdir(self.root_dir)
                success_count += 1
            else:
                console.print(f"[red]X Failed to build {version}![/red]")
                self._display_build_error(result)
            
            console.print()
        
        self._display_summary(success_count, total, "versioned folders")
    
    def build_specific_version(self):
        """Build a specific Minecraft version"""
        self._display_header("BUILD SPECIFIC VERSION")
        
        table = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        table.add_column("Option", style="green")
        table.add_column("Version", style="white")
        
        for i, v in enumerate(self.VERSIONS, 1):
            table.add_row(f"[{i}]", f"Minecraft {v}")
        table.add_row("[5]", "Back to main menu")
        
        console.print(Align.center(table))
        console.print()
        
        choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=["1", "2", "3", "4", "5"])
        
        if choice == "5":
            return
        
        version = self.VERSIONS[int(choice) - 1]
        build_type = self._select_build_type()
        
        # Build the version
        result = self._build_version(version, build_type)
        
        # Show result screen with proper header
        console.clear()
        
        if result.success:
            self._display_header(f"✓ BUILT FOR MINECRAFT {version}")
            console.print(f"[green]+ Build {version} successful![/green]")
            console.print()
            console.print("[cyan]Where would you like to copy the jars?[/cyan]")
            console.print()
            
            # Show options table
            options_table = Table(show_header=False, box=box.ROUNDED, border_style="cyan", width=60)
            options_table.add_column("Option", style="green", justify="center")
            options_table.add_column("Description", style="white")
            options_table.add_row("[1]", "Copy to Releases folder (with version names)")
            options_table.add_row("[2]", "Copy to version folder (raw names)")
            options_table.add_row("[3]", "Copy to custom folder")
            options_table.add_row("[4]", "Don't copy, return to menu")
            
            console.print(Align.center(options_table))
            console.print()
            
            dest_choice = Prompt.ask(
                "[cyan]Choose destination[/cyan]",
                choices=["1", "2", "3", "4"],
                default="4"
            )
            
            if dest_choice == "1":
                releases_dir = self.root_dir / "Releases"
                releases_dir.mkdir(exist_ok=True)
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, releases_dir, rename=True)
                os.chdir(self.root_dir)
                console.print(f"[green]+ Jars copied to Releases folder[/green]")
            elif dest_choice == "2":
                version_dir = self.root_dir / version
                version_dir.mkdir(exist_ok=True)
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, version_dir, rename=False)
                os.chdir(self.root_dir)
                console.print(f"[green]+ Jars copied to {version}/ folder[/green]")
            elif dest_choice == "3":
                custom_path = Prompt.ask("[cyan]Enter custom folder path[/cyan]")
                if custom_path.strip():
                    custom_dir = Path(custom_path.strip())
                    custom_dir.mkdir(parents=True, exist_ok=True)
                    os.chdir(self._get_version_dir(version))
                    self._copy_jars(version, custom_dir, rename=True)
                    os.chdir(self.root_dir)
                    console.print(f"[green]+ Jars copied to {custom_dir}[/green]")
                else:
                    console.print("[yellow]+ No path entered, jars not copied.[/yellow]")
            else:
                console.print("[yellow]+ Jars not copied.[/yellow]")
        else:
            self._display_header(f"✗ FAILED TO BUILD {version}")
            console.print(f"[red]X Build failed for {version}![/red]")
            console.print()
            self._display_build_error(result)
        
        console.print()
        Prompt.ask("[cyan]Press Enter to return to main menu...[/cyan]")
    
    def build_custom_folder(self):
        """Build to custom folder"""
        self._display_header("BUILD TO CUSTOM FOLDER")
        
        custom_folder = Prompt.ask("[cyan]Enter custom release folder name[/cyan]")
        
        if not custom_folder.strip():
            console.print("[red]Folder name cannot be empty.[/red]")
            Prompt.ask("[cyan]Press Enter to continue...[/cyan]")
            return self.build_custom_folder()
        
        build_type = self._select_build_type()
        
        console.clear()
        self._display_header(f"BUILDING TO CUSTOM FOLDER: {custom_folder}")
        
        custom_dir = self.root_dir / custom_folder
        custom_dir.mkdir(exist_ok=True)
        
        total = len(self.VERSIONS)
        success_count = 0
        
        for version in self.VERSIONS:
            console.print(f"[yellow]Processing Minecraft {version}...[/yellow]")
            
            result = self._build_version(version, build_type)
            
            if result.success:
                console.print(f"[green]+ Build {version} successful. Copying to {custom_folder}...[/green]")
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, custom_dir, rename=True)
                os.chdir(self.root_dir)
                success_count += 1
            else:
                console.print(f"[red]X Failed to build {version}![/red]")
                self._display_build_error(result)
            
            console.print()
        
        self._display_summary(success_count, total, custom_folder)
    
    def _display_summary(self, success_count: int, total_count: int, destination: str):
        """Display build summary"""
        console.clear()
        console.print()
        console.print(Panel(
            Align.center(Text("BUILD PROCESS COMPLETED", style="bold green")),
            box=box.ROUNDED,
            border_style="green"
        ))
        
        summary = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        summary.add_column("Metric", style="cyan")
        summary.add_column("Value", style="white")
        summary.add_row("Successful Builds", f"[green]{success_count}/{total_count}[/green]")
        summary.add_row("Destination", f"[white]{destination}[/white]")
        
        console.print(Align.center(summary))
        console.print()
        Prompt.ask("[cyan]Press Enter to continue...[/cyan]")
    
    def run(self):
        """Main application loop"""
        while True:
            self._display_menu()
            
            choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=["1", "2", "3", "4", "5", "6"])
            
            if choice == "1":
                self.build_all_releases()
            elif choice == "2":
                self.build_all_versioned()
            elif choice == "3":
                self.build_specific_version()
            elif choice == "4":
                self.build_custom_folder()
            elif choice == "5":
                self._select_java_version()
            elif choice == "6":
                self._display_exit_screen()
                break
    
    def _display_exit_screen(self):
        """Display exit screen"""
        console.clear()
        console.print(Panel(
            Align.center(Text("THANK YOU FOR USING\nVISCORD BUILD MENU v3.0", style="bold yellow")),
            box=box.ROUNDED,
            border_style="cyan"
        ))
        console.print(Align.center("[cyan]Have a great day![/cyan]"))
        import time
        time.sleep(1)


def main():
    """Entry point"""
    try:
        app = ViscordBuildMenu()
        app.run()
    except KeyboardInterrupt:
        console.print("\n[yellow]Interrupted by user. Goodbye![/yellow]")
        sys.exit(0)
    except Exception as e:
        console.print(f"\n[red]Error: {e}[/red]")
        sys.exit(1)


if __name__ == "__main__":
    main()
